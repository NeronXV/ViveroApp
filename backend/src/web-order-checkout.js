import { randomBytes } from 'node:crypto';
import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

export function checkoutScope(context) {
  if (context.access_state !== 'ACTIVE' || !context.capabilities.some(name => ['OPERATE_CASHIER', 'VIEW_BRANCH_SALES', 'VIEW_ALL_SALES'].includes(name))) throw new ApiError(403, 'FORBIDDEN');
  requireCapabilities(context, [], context.branch?.id);
  if (!context.branch?.is_active) throw new ApiError(403, 'BRANCH_FORBIDDEN');
  return context.branch.id;
}
const fail = code => { throw new ApiError(409, code); };
export async function sendOrderToCashier(db, context, id) {
  const branch = checkoutScope(context);
  const [[order]] = await db.execute('SELECT id, status, subtotal_cents, discount_cents, total_cents FROM web_orders WHERE id = ? AND branch_id = ? FOR UPDATE', [id, branch]);
  if (!order) throw new ApiError(404, 'WEB_ORDER_NOT_FOUND');
  async function existing() {
    const [[sale]] = await db.execute('SELECT id, folio, status, total_cents FROM sales WHERE web_order_id = ?', [id]);
    return sale;
  }
  const receipt = (sale, replay) => ({ schema_version: 1, order_id: id, sale_id: sale.id, folio: sale.folio, status: sale.status, total_cents: Number(sale.total_cents), idempotent_replay: replay });
  const previous = await existing();
  if (previous) return receipt(previous, true);
  if (!['CONFIRMED', 'READY'].includes(order.status)) fail('WEB_ORDER_STATUS_INVALID');
  const [items] = await db.execute(`SELECT i.*, p.is_active AS product_active, c.is_active AS category_active
    FROM web_order_items i JOIN products p ON p.id = i.product_id JOIN categories c ON c.id = p.category_id
    WHERE i.order_id = ? ORDER BY i.product_id LOCK IN SHARE MODE`, [id]);
  if (!items.length || items.some(item => !item.product_active || !item.category_active)) fail('WEB_ORDER_ITEMS_UNAVAILABLE');
  let list = 0n, effective = 0n;
  for (const item of items) {
    list += BigInt(item.list_price_cents) * BigInt(item.quantity);
    effective += BigInt(item.line_total_cents);
    if (BigInt(item.line_total_cents) !== BigInt(item.unit_price_cents) * BigInt(item.quantity)) fail('WEB_ORDER_ITEMS_UNAVAILABLE');
  }
  if (list !== BigInt(order.subtotal_cents) || effective !== BigInt(order.total_cents)
    || list - effective !== BigInt(order.discount_cents) || effective <= 0n) fail('WEB_ORDER_ITEMS_UNAVAILABLE');
  const [sale] = await db.execute(`INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, discount_cents, total_cents, web_order_id)
    VALUES (?, ?, ?, 'SENT_TO_CASHIER', ?, ?, ?, ?)`, [`VD-${randomBytes(12).toString('hex').toUpperCase()}`, branch, context.user.id, order.subtotal_cents, order.discount_cents, order.total_cents, id]);
  for (const item of items) {
    await db.execute(`INSERT INTO sale_items (sale_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents, promotion_id, promotion_name)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [sale.insertId, item.product_id, item.product_name, item.internal_code, item.quantity,
      item.list_price_cents, item.unit_price_cents, item.line_total_cents, item.promotion_id, item.promotion_name]);
  }
  await db.execute(`INSERT INTO sale_status_history (sale_id, previous_status, new_status, changed_by, observation)
    VALUES (?, NULL, 'DRAFT', ?, 'Web order accepted by backend'), (?, 'DRAFT', 'SENT_TO_CASHIER', ?, 'Web order sent to cashier')`, [sale.insertId, context.user.id, sale.insertId, context.user.id]);
  return receipt(await existing(), false);
}
