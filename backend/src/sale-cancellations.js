import { createHash } from 'node:crypto';
import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { cashierScope } from './cashier.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const hash = value => createHash('sha256').update(value).digest();
export function cancellationInput(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 1
      || typeof input.reason !== 'string' || !input.reason.isWellFormed()) fail(400, 'CANCELLATION_REASON_INVALID');
  const reason = input.reason.trim();
  if ([...reason].length < 3 || [...reason].length > 240 || /[\u0000-\u001f\u007f-\u009f]/u.test(reason)) fail(400, 'CANCELLATION_REASON_INVALID');
  return { reason };
}
function keyHash(key, actor) {
  if (typeof key !== 'string' || !/^[a-f0-9]{64}$/.test(key)) fail(400, 'CANCELLATION_KEY_INVALID');
  return hash(`sale-cancellation:${actor}:${key}`);
}
// Caller supplies auth.withAccess READ COMMITTED transaction. Lock order matches
// web checkout/status changes: web order first, then sale. Payment locks sale.
export function createSaleCancellations(db, context) {
  const branch = cashierScope(context), actor = context.user.id;
  const authorized = context.capabilities.includes('MANAGE_DISCOUNTS');
  function permission() { requireCapabilities(context, ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'], branch); }
  async function locked(id) {
    const [[reference]] = await db.execute('SELECT web_order_id FROM sales WHERE id = ? AND branch_id = ?', [id, branch]);
    if (!reference) fail(404, 'SALE_UNAVAILABLE');
    let order = null;
    if (reference.web_order_id) {
      [[order]] = await db.execute('SELECT id, status, revision FROM web_orders WHERE id = ? AND branch_id = ? FOR UPDATE', [reference.web_order_id, branch]);
      if (!order) fail(409, 'CANCELLATION_RECONCILIATION_REQUIRED');
    }
    const [[sale]] = await db.execute('SELECT id, folio, branch_id, web_order_id, status, total_cents FROM sales WHERE id = ? AND branch_id = ? FOR UPDATE', [id, branch]);
    if (!sale || sale.web_order_id !== reference.web_order_id) fail(409, 'CANCELLATION_RECONCILIATION_REQUIRED');
    return { sale, order };
  }
  async function blocked(sale, order) {
    const [[payment]] = await db.execute('SELECT id FROM cashier_payments WHERE sale_id = ?', [sale.id]);
    if (payment || ['PAID', 'DELIVERED'].includes(sale.status)) return 'CANCELLATION_REFUND_REQUIRED';
    if (sale.status === 'CANCELLED') return 'SALE_ALREADY_CANCELLED';
    if (sale.status !== 'SENT_TO_CASHIER') return 'CANCELLATION_RECONCILIATION_REQUIRED';
    const [[claim]] = await db.execute('SELECT id FROM sale_payment_claims WHERE active_sale_id = ? FOR UPDATE', [sale.id]);
    const [[movement]] = await db.execute('SELECT id FROM inventory_movements WHERE sale_id = ? LIMIT 1', [sale.id]);
    if (claim || movement || order?.status === 'COMPLETED') return 'CANCELLATION_RECONCILIATION_REQUIRED';
    return null;
  }
  async function previous(key) {
    const [[row]] = await db.execute('SELECT * FROM sale_cancellations WHERE idempotency_hash = ?', [key]);
    return row;
  }
  async function receipt(sale, cancellation, replay) {
    const [[saved]] = await db.execute(`SELECT id, sale_id, branch_id, cancelled_by, reason,
      DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at FROM sale_cancellations WHERE id = ?`, [cancellation.id]);
    return { schema_version: 1, idempotent_replay: replay,
      sale: { id: sale.id, folio: sale.folio, branch_id: branch, status: 'CANCELLED', total_cents: Number(sale.total_cents) },
      cancellation: saved, web_order_id: sale.web_order_id };
  }
  return {
    async options(id) {
      const { sale, order } = await locked(id);
      const code = authorized ? await blocked(sale, order) : 'FORBIDDEN';
      return { schema_version: 1, sale_id: id, can_cancel: code === null, blocked_reason: code };
    },
    async recover(id, key) {
      permission();
      const { sale } = await locked(id), saved = await previous(keyHash(key, actor));
      if (!saved || saved.sale_id !== id || saved.branch_id !== branch || saved.cancelled_by !== actor) fail(404, 'CANCELLATION_NOT_FOUND');
      return receipt(sale, saved, true);
    },
    async cancel(id, input, key) {
      permission();
      const { sale, order } = await locked(id), digest = keyHash(key, actor);
      const request = hash(JSON.stringify({ sale_id: id, branch_id: branch, reason: input.reason }));
      const saved = await previous(digest);
      if (saved) {
        if (saved.sale_id !== id || saved.branch_id !== branch || !saved.request_hash.equals(request)) fail(409, 'CANCELLATION_IDEMPOTENCY_CONFLICT');
        return receipt(sale, saved, true);
      }
      const code = await blocked(sale, order);
      if (code) fail(409, code);
      const [created] = await db.execute(`INSERT INTO sale_cancellations(sale_id, branch_id, cancelled_by, reason, idempotency_hash, request_hash)
        VALUES (?, ?, ?, ?, ?, ?)`, [id, branch, actor, input.reason, digest, request]);
      await db.execute("UPDATE sales SET status = 'CANCELLED', updated_at = UTC_TIMESTAMP() WHERE id = ?", [id]);
      await db.execute("INSERT INTO sale_status_history(sale_id, previous_status, new_status, changed_by, observation) VALUES (?, 'SENT_TO_CASHIER', 'CANCELLED', ?, ?)", [id, actor, input.reason]);
      if (order && order.status !== 'CANCELLED') {
        await db.execute("UPDATE web_orders SET status = 'CANCELLED', revision = revision + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = ?", [order.id]);
        await db.execute(`INSERT INTO web_order_status_history(order_id, revision, previous_status, new_status, changed_by, observation)
          VALUES (?, ?, ?, 'CANCELLED', ?, ?)`, [order.id, order.revision + 1, order.status, actor, input.reason]);
      }
      return receipt(sale, { id: created.insertId }, false);
    },
  };
}
