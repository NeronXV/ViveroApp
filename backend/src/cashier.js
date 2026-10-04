import { createHash, randomBytes } from 'node:crypto';
import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { saleQuery } from './sales.js';
import { deductSaleStock } from './stock.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const hash = value => createHash('sha256').update(value).digest();
export function cashierScope(context) {
  requireCapabilities(context, ['OPERATE_CASHIER'], context.branch?.id);
  if (!context.branch?.is_active) fail(403, 'CASHIER_UNAUTHORIZED');
  return context.branch.id;
}
function exact(input, keys) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== keys.length
    || keys.some(key => !Object.hasOwn(input, key))) fail(400, 'PAYMENT_DATA_INVALID');
}
export function claimInput(input, nullable = false) {
  exact(input, ['claim_token']);
  if (nullable && input.claim_token === null) return null;
  if (typeof input.claim_token !== 'string' || !/^[a-f0-9]{64}$/.test(input.claim_token)) fail(400, 'CLAIM_TOKEN_INVALID');
  return input.claim_token;
}
export function paymentKey(value, actorId) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) fail(400, 'PAYMENT_KEY_REQUIRED');
  return hash(`payment:${actorId}:${value}`).toString('hex');
}
export function paymentInput(input) {
  exact(input, ['claim_token', 'method', 'amount_received_cents', 'reference']);
  const token = claimInput({ claim_token: input.claim_token });
  if (!['CASH', 'CARD', 'TRANSFER'].includes(input.method)) fail(400, 'PAYMENT_METHOD_INVALID');
  let reference = input.reference;
  if (reference !== null) {
    if (typeof reference !== 'string' || !reference.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(reference)
      || [...reference.trim()].length > 120) fail(400, 'PAYMENT_DATA_INVALID');
    reference = reference.trim() || null;
  }
  const received = input.amount_received_cents;
  if (input.method === 'CASH') {
    if (!Number.isSafeInteger(received) || received < 1 || reference !== null) fail(400, 'PAYMENT_DATA_INVALID');
  } else {
    if (received !== null) fail(400, 'PAYMENT_DATA_INVALID');
    if (input.method === 'TRANSFER' && reference === null) fail(400, 'TRANSFER_REFERENCE_REQUIRED');
    if (input.method === 'CARD' && reference !== null
      && ([...reference].length > 64 || /^[0-9]{3,4}$/.test(reference) || /^[0-9]{13,19}$/.test(reference.replace(/[^0-9]/g, '')))) fail(400, 'PAYMENT_DATA_INVALID');
  }
  return { claim_token: token, method: input.method, amount_received_cents: received, reference };
}
export function cashierQuery(params) {
  const query = new URLSearchParams(params);
  const view = query.get('view');
  if (query.getAll('view').length > 1 || (view !== null && view !== 'operations')) fail(400, 'INVALID_INPUT');
  query.delete('view');
  return { ...saleQuery(query), operations: view === 'operations' };
}
const money = row => Object.fromEntries(Object.entries(row).map(([key, value]) => [key, key.endsWith('_cents') && value !== null ? Number(value) : value]));

// Called only within auth.withAccess READ COMMITTED transactions.
export function createCashier(db, context) {
  const branchId = cashierScope(context), actor = context.user.id;
  async function sale(id) {
    const [[row]] = await db.execute('SELECT id, folio, branch_id, web_order_id, status, subtotal_cents, discount_cents, total_cents FROM sales WHERE id = ? AND branch_id = ? FOR UPDATE', [id, branchId]);
    if (!row) fail(404, 'SALE_UNAVAILABLE');
    return row;
  }
  async function activeClaim(id) {
    const [[row]] = await db.execute('SELECT *, expires_at <= UTC_TIMESTAMP(6) AS expired FROM sale_payment_claims WHERE active_sale_id = ? FOR UPDATE', [id]);
    return row;
  }
  function owned(row, token) {
    if (!row || row.cashier_id !== actor || row.branch_id !== branchId || row.claim_token !== token) fail(409, 'CLAIM_NOT_OWNED');
  }
  async function claimReceipt(id, renewed) {
    const [[row]] = await db.execute(`SELECT sale_id, branch_id, cashier_id, claim_token,
      DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at,
      DATE_FORMAT(expires_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS expires_at,
      DATE_FORMAT(UTC_TIMESTAMP(6), '%Y-%m-%dT%H:%i:%s.%fZ') AS server_time
      FROM sale_payment_claims WHERE id = ?`, [id]);
    return { schema_version: 1, ...row, renewed };
  }
  async function paymentByKey(key) {
    const [[row]] = await db.execute('SELECT * FROM cashier_payments WHERE idempotency_key = ?', [key]);
    return row;
  }
  function receipt(saleRow, payment, replay) {
    if (payment.cashier_id !== actor || payment.branch_id !== branchId || payment.sale_id !== saleRow.id) fail(409, 'PAYMENT_IDEMPOTENCY_CONFLICT');
    const { request_hash, idempotency_key, ...safe } = payment;
    return { schema_version: 1, idempotent_replay: replay, sale: money(saleRow), payment: money(safe) };
  }
  async function operation(id) {
    const [[row]] = await db.execute(`SELECT
      DATE_FORMAT(s.created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at,
      u.full_name AS created_by_label,
      (SELECT COUNT(*) FROM sale_items i WHERE i.sale_id = s.id) AS item_count,
      CASE WHEN c.id IS NULL OR c.expires_at <= UTC_TIMESTAMP(6) THEN 'AVAILABLE'
        WHEN c.cashier_id = ? THEN 'CLAIMED_BY_ME' ELSE 'CLAIMED_BY_OTHER' END AS claim_state,
      CASE WHEN c.id IS NULL OR c.expires_at <= UTC_TIMESTAMP(6) THEN NULL
        ELSE DATE_FORMAT(c.expires_at, '%Y-%m-%dT%H:%i:%s.%fZ') END AS claim_expires_at,
      DATE_FORMAT(UTC_TIMESTAMP(6), '%Y-%m-%dT%H:%i:%s.%fZ') AS server_time
      FROM sales s LEFT JOIN users u ON u.id = s.created_by
      LEFT JOIN sale_payment_claims c ON c.active_sale_id = s.id
      WHERE s.id = ? AND s.branch_id = ?`, [actor, id, branchId]);
    if (!row) fail(404, 'SALE_UNAVAILABLE');
    return { ...row, item_count: Number(row.item_count) };
  }
  return {
    async receipts({ limit, beforeId }) {
      const [rows] = await db.execute(`SELECT p.id, p.sale_id, s.folio, p.method, p.amount_due_cents,
        DATE_FORMAT(p.created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at
        FROM cashier_payments p JOIN sales s ON s.id = p.sale_id AND s.branch_id = p.branch_id
        WHERE p.branch_id = ? AND p.cashier_id = ? AND (? IS NULL OR p.id < ?)
        ORDER BY p.id DESC LIMIT ?`, [branchId, actor, beforeId, beforeId, limit + 1]);
      return { schema_version: 1, items: rows.slice(0, limit).map(money), next_before_id: rows.length > limit ? rows[limit - 1].id : null };
    },
    async paymentReceipt(id) {
      const [[payment]] = await db.execute(`SELECT id, sale_id, cashier_id, branch_id, method,
        amount_due_cents, amount_received_cents, change_cents, reference,
        DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at
        FROM cashier_payments WHERE id = ? AND branch_id = ? AND cashier_id = ?`, [id, branchId, actor]);
      if (!payment) fail(404, 'PAYMENT_NOT_FOUND');
      const [[row]] = await db.execute(`SELECT id, folio, branch_id, web_order_id, status, subtotal_cents, discount_cents, total_cents
        FROM sales WHERE id = ? AND branch_id = ?`, [payment.sale_id, branchId]);
      if (!row || !['PAID', 'DELIVERED'].includes(row.status)) fail(409, 'SALE_STATUS_INVALID');
      const [items] = await db.execute('SELECT id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents FROM sale_items WHERE sale_id = ? ORDER BY id', [row.id]);
      const [[branch]] = await db.execute('SELECT id, name FROM branches WHERE id = ?', [branchId]);
      const [[refund]] = await db.execute('SELECT id, amount_cents, method FROM sale_refunds WHERE sale_id = ?', [row.id]);
      return { schema_version: 1, sale: money(row), payment: money(payment), items: items.map(money), branch, refund: refund ? money(refund) : null };
    },
    async list({ limit, beforeId, operations = false }) {
      const [rows] = await db.execute(`SELECT id, folio, created_by, status, total_cents, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at FROM sales
        WHERE branch_id = ? AND status = 'SENT_TO_CASHIER' AND (? IS NULL OR id < ?) ORDER BY id DESC LIMIT ?`, [branchId, beforeId, beforeId, limit + 1]);
      const items = rows.slice(0, limit).map(money);
      if (operations) for (const item of items) item.operation = await operation(item.id);
      return { schema_version: 1, items, next_before_id: rows.length > limit ? rows[limit - 1].id : null };
    },
    async detail(id, operations = false) {
      const row = await sale(id);
      const [items] = await db.execute('SELECT id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents FROM sale_items WHERE sale_id = ? ORDER BY id', [id]);
      return { schema_version: 1, sale: money(row), items: items.map(money), ...(operations ? { operation: await operation(id) } : {}) };
    },
    async claim(id, token) {
      const row = await sale(id);
      if (row.status !== 'SENT_TO_CASHIER') fail(409, 'SALE_STATUS_INVALID');
      const previous = await activeClaim(id);
      if (previous) {
        if (previous.expired) {
          if (token !== null) fail(409, 'CLAIM_EXPIRED');
          await db.execute("UPDATE sale_payment_claims SET released_at = UTC_TIMESTAMP(6), closed_reason = 'EXPIRED' WHERE id = ?", [previous.id]);
        } else {
          if (previous.cashier_id !== actor) fail(409, 'CLAIM_UNAVAILABLE');
          if (token !== null) {
            owned(previous, token);
            await db.execute('UPDATE sale_payment_claims SET expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE), renewed_at = UTC_TIMESTAMP(6), renewal_count = renewal_count + 1 WHERE id = ?', [previous.id]);
          }
          return claimReceipt(previous.id, token !== null);
        }
      } else if (token !== null) fail(409, 'CLAIM_NOT_OWNED');
      const [created] = await db.execute('INSERT INTO sale_payment_claims (sale_id, branch_id, cashier_id, claim_token, expires_at) VALUES (?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE))', [id, branchId, actor, randomBytes(32).toString('hex')]);
      return claimReceipt(created.insertId, false);
    },
    async release(id, token) {
      await sale(id);
      const row = await activeClaim(id);
      owned(row, token);
      const reason = row.expired ? 'EXPIRED' : 'RELEASED';
      await db.execute('UPDATE sale_payment_claims SET released_at = UTC_TIMESTAMP(6), closed_reason = ? WHERE id = ?', [reason, row.id]);
      const [[released]] = await db.execute("SELECT DATE_FORMAT(released_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS released_at FROM sale_payment_claims WHERE id = ?", [row.id]);
      return { schema_version: 1, sale_id: id, claim_token: token, released_at: released.released_at, closed_reason: reason };
    },
    async pay(id, input, key) {
      const row = await sale(id), keyHash = paymentKey(key, actor);
      // Claim tokens expire, but an already committed matching payment must replay.
      const requestHash = hash(JSON.stringify({ sale_id: id, branch_id: branchId, method: input.method, amount_received_cents: input.amount_received_cents, reference: input.reference }));
      const previous = await paymentByKey(keyHash);
      if (previous) {
        if (!previous.request_hash?.equals(requestHash)) fail(409, 'PAYMENT_IDEMPOTENCY_CONFLICT');
        return receipt(row, previous, true);
      }
      const [[retired]] = await db.execute('SELECT id FROM payment_attempt_retirements WHERE idempotency_hash = ?', [keyHash]);
      if (retired) fail(409, 'PAYMENT_ATTEMPT_RETIRED');
      const [[paid]] = await db.execute('SELECT id FROM cashier_payments WHERE sale_id = ?', [id]);
      if (paid) fail(409, 'SALE_ALREADY_PAID');
      if (row.status !== 'SENT_TO_CASHIER') fail(409, 'SALE_STATUS_INVALID');
      const total = Number(row.total_cents);
      if (!Number.isSafeInteger(total) || total < 1) fail(409, 'SALE_TOTAL_INVALID');
      const claim = await activeClaim(id);
      if (!claim) fail(409, 'CLAIM_REQUIRED');
      owned(claim, input.claim_token);
      if (claim.expired) fail(409, 'CLAIM_EXPIRED');
      const [[sum]] = await db.execute('SELECT COUNT(*) AS n, SUM(line_total_cents) AS total, SUM(list_price_cents * quantity) AS list_total, SUM(line_total_cents <> unit_price_cents * quantity) AS invalid FROM sale_items WHERE sale_id = ?', [id]);
      const expectedSum = row.web_order_id ? row.total_cents : row.subtotal_cents;
      // DECIMAL quantities make SQL return e.g. "2000.000" for integer cents.
      const listTotal = sum.list_total === null ? null : String(sum.list_total);
      if (!Number(sum.n) || Number(sum.invalid) || BigInt(sum.total) !== BigInt(expectedSum)
        || (row.web_order_id && (!/^\d+(?:\.0+)?$/.test(listTotal ?? '') || BigInt(listTotal.split('.')[0]) !== BigInt(row.subtotal_cents)))
        || BigInt(row.total_cents) !== BigInt(row.subtotal_cents) - BigInt(row.discount_cents)) fail(409, 'SALE_TOTAL_INVALID');
      const received = input.method === 'CASH' ? input.amount_received_cents : total;
      if (received < total) fail(400, 'CASH_AMOUNT_INSUFFICIENT');
      await db.execute(`INSERT INTO cashier_payments (sale_id, branch_id, cashier_id, claim_id, idempotency_key, request_hash, method,
        amount_due_cents, requested_amount_received_cents, amount_received_cents, change_cents, reference)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [id, branchId, actor, claim.id, keyHash, requestHash, input.method, total, input.amount_received_cents, received, received - total, input.reference]);
      await db.execute("UPDATE sales SET status = 'PAID', updated_at = UTC_TIMESTAMP() WHERE id = ?", [id]);
      await deductSaleStock(db, row, actor);
      await db.execute("UPDATE sale_payment_claims SET consumed_at = UTC_TIMESTAMP(6), closed_reason = 'CONFIRMED' WHERE id = ?", [claim.id]);
      await db.execute("INSERT INTO sale_status_history (sale_id, previous_status, new_status, changed_by, observation) VALUES (?, 'SENT_TO_CASHIER', 'PAID', ?, 'Payment confirmed')", [id, actor]);
      return receipt({ ...row, status: 'PAID' }, await paymentByKey(keyHash), false);
    },
    async recover(id, key) {
      const row = await sale(id), payment = await paymentByKey(paymentKey(key, actor));
      if (!payment || payment.sale_id !== id || row.status !== 'PAID') fail(404, 'PAYMENT_NOT_FOUND');
      return receipt(row, payment, true);
    },
    async retire(id, key) {
      const row = await sale(id), keyHash = paymentKey(key, actor);
      const previous = await paymentByKey(keyHash);
      if (previous) return { schema_version: 1, status: 'COMMITTED', receipt: receipt(row, previous, true) };
      const [[retired]] = await db.execute('SELECT actor_id, branch_id, sale_id FROM payment_attempt_retirements WHERE idempotency_hash = ?', [keyHash]);
      if (retired && (retired.actor_id !== actor || retired.branch_id !== branchId || retired.sale_id !== id)) fail(409, 'PAYMENT_IDEMPOTENCY_CONFLICT');
      if (!retired) await db.execute('INSERT INTO payment_attempt_retirements(actor_id,branch_id,sale_id,idempotency_hash) VALUES(?,?,?,?)', [actor, branchId, id, keyHash]);
      return { schema_version: 1, status: 'RETIRED', receipt: null };
    },
  };
}
