import { createHash, randomBytes } from 'node:crypto';
import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { quoteOrderProducts } from './web-orders.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const digest = text => createHash('sha256').update(text).digest();
export function saleScope(context, capability = 'CREATE_SALES') {
  requireCapabilities(context, [capability], context.branch?.id);
  if (!context.branch?.is_active) fail(403, 'BRANCH_FORBIDDEN');
  return context.branch.id;
}
export function saleKey(value, actorId) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) fail(400, 'SALE_KEY_REQUIRED');
  return digest(`sale:${actorId}:${value}`);
}
export function validateSale(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)
    || Object.keys(input).some(k => !['items', 'expected_total_cents', 'customer_id'].includes(k)) || !Object.hasOwn(input, 'items') || !Object.hasOwn(input, 'expected_total_cents')
    || !Number.isSafeInteger(input.expected_total_cents) || input.expected_total_cents < 1
    || !Array.isArray(input.items) || input.items.length < 1 || input.items.length > 25) fail(400, 'SALE_INPUT_INVALID');
  const items = input.items.map(row => {
    if (!row || typeof row !== 'object' || Array.isArray(row) || Object.keys(row).length !== 2
      || !Object.hasOwn(row, 'product_id') || !Object.hasOwn(row, 'quantity') || typeof row.product_id !== 'number'
      || !Number.isInteger(row.quantity) || row.quantity < 1 || row.quantity > 100000) fail(400, 'SALE_ITEMS_INVALID');
    return { product_id: positiveId(row.product_id), quantity: row.quantity };
  }).sort((a, b) => a.product_id - b.product_id);
  if (new Set(items.map(row => row.product_id)).size !== items.length) fail(400, 'SALE_ITEMS_INVALID');
  const customer = Object.hasOwn(input, 'customer_id') ? input.customer_id : undefined;
  if (customer !== undefined && customer !== null && typeof customer !== 'number') fail(400, 'SALE_INPUT_INVALID');
  return { items, expected_total_cents: input.expected_total_cents,
    ...(customer === undefined ? {} : { customer_id: customer === null ? null : positiveId(customer) }) };
}
export function saleQuery(params) {
  if ([...params.keys()].some(key => !['limit', 'before_id', 'folio'].includes(key) || params.getAll(key).length !== 1)) fail(400, 'SALE_QUERY_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  if (limit > 100) fail(400, 'SALE_QUERY_INVALID');
  const folio = params.get('folio');
  if (folio !== null && (!folio.trim() || [...folio].length > 40 || /[\u0000-\u001f\u007f-\u009f]/u.test(folio))) fail(400, 'SALE_QUERY_INVALID');
  return { limit, beforeId: params.has('before_id') ? positiveId(params.get('before_id')) : null, ...(folio === null ? {} : { folio: folio.trim() }) };
}
const columns = `id, folio, branch_id, created_by, status, subtotal_cents, discount_cents, total_cents,
  DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%sZ') AS created_at`;
const money = row => Object.fromEntries(Object.entries(row).map(([key, value]) => [key, key.endsWith('_cents') && value !== null ? Number(value) : value]));

// Every operation runs inside auth.withAccess's transaction. Its user-row lock
// serializes this actor's submissions, even across tokens/API instances.
export function createSales(db, context) {
  const branchId = saleScope(context);
  async function saved(hash) {
    const [[row]] = await db.execute(`SELECT ${columns}, request_hash FROM sales
      WHERE idempotency_hash = ?`, [hash]);
    return row;
  }
  async function retired(hash) {
    const [[row]] = await db.execute('SELECT actor_id, branch_id FROM sale_attempt_retirements WHERE idempotency_hash = ?', [hash]);
    if (row && (row.actor_id !== context.user.id || row.branch_id !== branchId)) fail(409, 'SALE_IDEMPOTENCY_CONFLICT');
    return Boolean(row);
  }
  function receipt(row, replay) {
    if (row.created_by !== context.user.id || row.branch_id !== branchId) fail(409, 'SALE_IDEMPOTENCY_CONFLICT');
    const { request_hash, ...result } = row;
    return { schema_version: 1, ...money(result), idempotent_replay: replay };
  }
  return {
    async quote(input) {
      if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 1 || !Object.hasOwn(input, 'items')) fail(400, 'SALE_INPUT_INVALID');
      const data = validateSale({ items: input.items, expected_total_cents: 1 });
      return quoteOrderProducts(db, { branch_id: branchId, items: data.items });
    },
    async submit(input, key) {
      const data = validateSale(input), hash = saleKey(key, context.user.id);
      const requestHash = digest(JSON.stringify({ branch_id: branchId, ...data }));
      const previous = await saved(hash);
      if (previous) {
        if (!previous.request_hash?.equals(requestHash)) fail(409, 'SALE_IDEMPOTENCY_CONFLICT');
        return receipt(previous, true);
      }
      if (await retired(hash)) fail(409, 'SALE_ATTEMPT_RETIRED');
      if (data.customer_id != null) {
        const [[customer]] = await db.execute('SELECT id FROM customers WHERE id = ? AND is_active = 1 LOCK IN SHARE MODE', [data.customer_id]);
        if (!customer) fail(409, 'SALE_CUSTOMER_UNAVAILABLE');
      }
      const priced = await quoteOrderProducts(db, { branch_id: branchId, items: data.items });
      if (priced.total_cents !== data.expected_total_cents) fail(409, 'SALE_PRICE_CHANGED');
      // Match Supabase: subtotal is the effective-priced sum, discount is zero.
      const [sale] = await db.execute(`INSERT INTO sales
        (folio, branch_id, created_by, status, subtotal_cents, discount_cents, total_cents, idempotency_hash, request_hash, customer_id)
        VALUES (?, ?, ?, 'SENT_TO_CASHIER', ?, 0, ?, ?, ?, ?)`,
      [`VD-${randomBytes(12).toString('hex').toUpperCase()}`, branchId, context.user.id, priced.total_cents, priced.total_cents, hash, requestHash, data.customer_id ?? null]);
      for (const item of priced.items) {
        await db.execute(`INSERT INTO sale_items
          (sale_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents, promotion_id, promotion_name)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [sale.insertId, item.product_id, item.product_name, item.internal_code, item.quantity,
          item.list_price_cents, item.unit_price_cents, item.line_total_cents, item.promotion_id, item.promotion_name]);
      }
      await db.execute(`INSERT INTO sale_status_history (sale_id, previous_status, new_status, changed_by, observation)
        VALUES (?, NULL, 'DRAFT', ?, 'Draft accepted by backend'), (?, 'DRAFT', 'SENT_TO_CASHIER', ?, 'Order sent to cashier')`,
      [sale.insertId, context.user.id, sale.insertId, context.user.id]);
      return receipt(await saved(hash), false);
    },
    async recover(key) {
      const row = await saved(saleKey(key, context.user.id));
      if (!row) fail(404, 'SALE_NOT_FOUND');
      return receipt(row, true);
    },
    async retire(key) {
      const hash = saleKey(key, context.user.id), row = await saved(hash);
      if (row) return { schema_version: 1, status: 'COMMITTED', sale: receipt(row, true) };
      if (!await retired(hash)) await db.execute('INSERT INTO sale_attempt_retirements (actor_id, branch_id, idempotency_hash) VALUES (?, ?, ?)', [context.user.id, branchId, hash]);
      return { schema_version: 1, status: 'RETIRED', sale: null };
    },
    async list({ limit, beforeId, folio = null }) {
      saleScope(context, 'VIEW_OWN_SALES');
      const [rows] = await db.execute(`SELECT ${columns} FROM sales
        WHERE created_by = ? AND branch_id = ? AND (? IS NULL OR id < ?)
        AND (? IS NULL OR BINARY sales.folio = BINARY ? OR id IN (SELECT sale_id FROM sale_folio_aliases WHERE short_folio = BINARY ?))
        ORDER BY id DESC LIMIT ?`,
      [context.user.id, branchId, beforeId, beforeId, folio, folio, folio, limit + 1]);
      return { schema_version: 1, items: rows.slice(0, limit).map(money), next_before_id: rows.length > limit ? rows[limit - 1].id : null };
    },
    async detail(id) {
      saleScope(context, 'VIEW_OWN_SALES');
      const [[sale]] = await db.execute(`SELECT ${columns} FROM sales WHERE id = ? AND created_by = ? AND branch_id = ?`, [id, context.user.id, branchId]);
      if (!sale) fail(404, 'SALE_NOT_FOUND');
      const [items] = await db.execute('SELECT id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents, promotion_id, promotion_name FROM sale_items WHERE sale_id = ? ORDER BY id', [id]);
      const [history] = await db.execute('SELECT previous_status, new_status, observation, created_at FROM sale_status_history WHERE sale_id = ? ORDER BY id', [id]);
      return { schema_version: 1, sale: money(sale), items: items.map(money), history };
    },
  };
}
