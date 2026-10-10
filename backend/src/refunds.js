import { createHash } from 'node:crypto';
import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { restockRefund } from './stock.js';

const fail = (status, code) => { throw new ApiError(status, code); };
export function refundFolio(params) {
  const value = params.get('folio');
  if ([...params.keys()].length !== 1 || params.getAll('folio').length !== 1 || typeof value !== 'string'
    || !value.isWellFormed() || !value.trim() || [...value.trim()].length > 40
    || /[\u0000-\u001f\u007f-\u009f]/u.test(value)) fail(400, 'REFUND_FOLIO_INVALID');
  return value.trim();
}
export function refundScope(context) {
  requireCapabilities(context, ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'], context.branch?.id);
  if (!context.branch?.is_active) fail(403, 'REFUND_UNAUTHORIZED');
  return context.branch.id;
}
export function refundInput(input) {
  const keys = ['reason', 'method', 'restock', 'money_returned'];
  if (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length !== keys.length
    || keys.some(key => !Object.hasOwn(input, key)) || input.money_returned !== true
    || typeof input.restock !== 'boolean' || !['CASH', 'CARD', 'TRANSFER'].includes(input.method)
    || typeof input.reason !== 'string' || !input.reason.isWellFormed()
    || /[\u0000-\u001f\u007f-\u009f]/u.test(input.reason)
    || [...input.reason.trim()].length < 5 || [...input.reason.trim()].length > 300) fail(400, 'REFUND_DATA_INVALID');
  return { reason: input.reason.trim(), method: input.method, restock: input.restock, money_returned: true };
}
function refundKey(value, actor) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) fail(400, 'REFUND_KEY_REQUIRED');
  return createHash('sha256').update(`refund:${actor}:${value}`).digest('hex');
}
// Only inside auth.withAccess READ COMMITTED transactions. Order-before-sale locks
// serialize refunds against delivery without reversing checkout's lock order.
export function createRefunds(db, context) {
  const branch = refundScope(context), actor = context.user.id;
  async function sale(id) {
    const [[link]] = await db.execute('SELECT web_order_id FROM sales WHERE id = ? AND branch_id = ?', [id, branch]);
    if (!link) fail(404, 'REFUND_SALE_UNAVAILABLE');
    if (link.web_order_id) await db.execute('SELECT id FROM web_orders WHERE id = ? FOR UPDATE', [link.web_order_id]);
    const [[row]] = await db.execute('SELECT id, folio, status FROM sales WHERE id = ? AND branch_id = ? FOR UPDATE', [id, branch]);
    if (!row || row.status !== 'PAID') fail(409, 'REFUND_SALE_UNAVAILABLE');
    const [[payment]] = await db.execute('SELECT id, amount_due_cents FROM cashier_payments WHERE sale_id = ?', [id]);
    if (!payment) fail(409, 'REFUND_PAYMENT_REQUIRED');
    return { row, payment };
  }
  function receipt(row, replay) {
    const { idempotency_key, request_hash, ...safe } = row;
    return { schema_version: 1, refund: { ...safe, amount_cents: Number(row.amount_cents), restock: Boolean(row.restock) }, idempotent_replay: replay };
  }
  async function preview(id) {
    const { row, payment } = await sale(id);
    const [[refund]] = await db.execute('SELECT id FROM sale_refunds WHERE sale_id = ?', [id]);
    const [[stock]] = await db.execute("SELECT id FROM inventory_movements WHERE sale_id = ? AND movement_type = 'SALE' LIMIT 1", [id]);
      return { schema_version: 1, sale_id: id, folio: row.folio, amount_cents: Number(payment.amount_due_cents), already_refunded: Boolean(refund), restock_available: Boolean(stock) && !refund };
  }
  return {
    preview,
    async lookup(folio) {
      const [[row]] = await db.execute(`SELECT s.id FROM sales s LEFT JOIN sale_folio_aliases a ON a.sale_id = s.id
        WHERE s.branch_id = ? AND (BINARY s.folio = BINARY ? OR a.short_folio = BINARY ?)`, [branch, folio, folio]);
      if (!row) fail(404, 'REFUND_SALE_UNAVAILABLE');
      return { ...await preview(row.id), folio }; // Preserve exact lookup echo for existing clients.
    },
    async refund(id, input, key) {
      const keyHash = refundKey(key, actor);
      const { payment } = await sale(id);
      const requestHash = createHash('sha256').update(JSON.stringify({ sale_id: id, branch_id: branch, ...input })).digest();
      const [[previous]] = await db.execute('SELECT * FROM sale_refunds WHERE idempotency_key = ?', [keyHash]);
      if (previous) {
        if (previous.refunded_by !== actor || previous.branch_id !== branch || previous.sale_id !== id || !previous.request_hash.equals(requestHash)) fail(409, 'REFUND_IDEMPOTENCY_CONFLICT');
        return receipt(previous, true);
      }
      const [[existing]] = await db.execute('SELECT id FROM sale_refunds WHERE sale_id = ?', [id]);
      if (existing) fail(409, 'REFUND_ALREADY_RECORDED');
      const [created] = await db.execute(`INSERT INTO sale_refunds
        (sale_id, payment_id, branch_id, refunded_by, amount_cents, method, reason, restock, idempotency_key, request_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [id, payment.id, branch, actor, payment.amount_due_cents, input.method, input.reason, input.restock, keyHash, requestHash]);
      const [[row]] = await db.execute('SELECT * FROM sale_refunds WHERE id = ?', [created.insertId]);
      if (input.restock) await restockRefund(db, row, actor);
      return receipt(row, false);
    },
  };
}
