import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

const statuses = ['PENDING', 'CONFIRMED', 'READY', 'CANCELLED', 'COMPLETED'];
export function orderScope(context, write = false) {
  if (context.access_state !== 'ACTIVE' || !context.capabilities.some(name => ['VIEW_BRANCH_SALES', 'VIEW_ALL_SALES'].includes(name))) throw new ApiError(403, 'FORBIDDEN');
  if (!write && context.capabilities.includes('VIEW_ALL_SALES')) return null;
  requireCapabilities(context, [], context.branch?.id);
  if (!context.branch?.is_active) throw new ApiError(403, 'BRANCH_FORBIDDEN');
  return context.branch.id;
}
export function orderAdminQuery(params) {
  const keys = ['limit', 'before_id', 'branch_id', 'status', 'folio'];
  if ([...params.keys()].some(key => !keys.includes(key) || params.getAll(key).length !== 1)) throw new ApiError(400, 'INVALID_INPUT');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  if (limit > 100 || (params.has('status') && !statuses.includes(params.get('status')))) throw new ApiError(400, 'INVALID_INPUT');
  const folio = params.get('folio');
  if (folio !== null && !/^VW-[0-9]{1,10}$/.test(folio)) throw new ApiError(400, 'INVALID_INPUT');
  return { limit, beforeId: params.has('before_id') ? positiveId(params.get('before_id')) : null,
    ...(folio === null ? {} : { folio }),
    branchId: params.has('branch_id') ? positiveId(params.get('branch_id')) : null, status: params.get('status') };
}
export function validateOrderStatus(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 3
    || !['status', 'expected_revision', 'observation'].every(key => Object.hasOwn(input, key))
    || !Number.isInteger(input.expected_revision) || input.expected_revision < 0 || input.expected_revision > 4294967294
    || !['CONFIRMED', 'READY', 'CANCELLED', 'COMPLETED'].includes(input.status)) throw new ApiError(400, 'WEB_ORDER_STATUS_INVALID');
  const value = input.observation;
  if (value !== null && (typeof value !== 'string' || !value.isWellFormed() || [...value.trim()].length > 500 || /[\u0000-\u001f\u007f-\u009f]/u.test(value))) throw new ApiError(400, 'WEB_ORDER_STATUS_INVALID');
  return { ...input, observation: value?.trim() || null };
}
const columns = `id, branch_id, customer_name, customer_phone, customer_email, notes, status, revision,
  (SELECT s.id FROM sales s WHERE s.web_order_id = web_orders.id) AS cashier_sale_id,
  subtotal_cents, discount_cents, total_cents,
  DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at,
  DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS updated_at`;
function money(row) {
  const result = { ...row };
  for (const key of Object.keys(result).filter(key => key.endsWith('_cents'))) result[key] = Number(result[key]);
  return result;
}
export function createOrderAdmin(db, context) {
  return {
    async list(query) {
      const scope = orderScope(context);
      if (scope !== null && query.branchId !== null && scope !== query.branchId) throw new ApiError(403, 'BRANCH_FORBIDDEN');
      const branch = scope ?? query.branchId;
      const [rows] = await db.execute(`SELECT ${columns} FROM web_orders
        WHERE (? IS NULL OR branch_id = ?) AND (? IS NULL OR id < ?) AND (? IS NULL OR status = ?)
        AND (? IS NULL OR CONCAT('VW-', id) = BINARY ? OR id IN (SELECT order_id FROM web_order_folio_aliases WHERE short_folio = BINARY ?))
        ORDER BY id DESC LIMIT ?`, [branch, branch, query.beforeId, query.beforeId, query.status, query.status, query.folio ?? null, query.folio ?? null, query.folio ?? null, query.limit + 1]);
      const items = rows.slice(0, query.limit).map(money);
      return { schema_version: 1, items, next_before_id: rows.length > query.limit ? items.at(-1).id : null };
    },
    async detail(id, operations = false) {
      const scope = orderScope(context);
      const [[order]] = await db.execute(`SELECT ${columns} FROM web_orders WHERE id = ? AND (? IS NULL OR branch_id = ?)`, [id, scope, scope]);
      if (!order) throw new ApiError(404, 'WEB_ORDER_NOT_FOUND');
      const [items] = await db.execute(`SELECT id, product_id, product_name, internal_code, quantity,
        list_price_cents, unit_price_cents, discount_cents, line_total_cents, promotion_id, promotion_name
        FROM web_order_items WHERE order_id = ? ORDER BY id`, [id]);
      const [history] = await db.execute(`SELECT revision, previous_status, new_status, changed_by, observation,
        DATE_FORMAT(changed_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS changed_at
        FROM web_order_status_history WHERE order_id = ? ORDER BY revision`, [id]);
      const result = { schema_version: 1, order: money(order), items: items.map(money), history };
      if (operations) {
        const [[branch]] = await db.execute('SELECT id, code, name FROM branches WHERE id = ?', [order.branch_id]);
        const [[checkout]] = await db.execute('SELECT id, folio, status, total_cents FROM sales WHERE web_order_id = ?', [id]);
        const [[clock]] = await db.execute("SELECT DATE_FORMAT(UTC_TIMESTAMP(6), '%Y-%m-%dT%H:%i:%s.%fZ') AS server_time");
        result.operation = { branch, checkout: checkout ? money(checkout) : null, server_time: clock.server_time, order_number: `VW-${order.id}` };
      }
      return result;
    },
    async update(id, input) {
      const scope = orderScope(context, true);
      const [[order]] = await db.execute(`SELECT id, status, revision FROM web_orders WHERE id = ? AND branch_id = ? FOR UPDATE`, [id, scope]);
      if (!order) throw new ApiError(404, 'WEB_ORDER_NOT_FOUND');
      if (order.revision === input.expected_revision + 1 && order.status === input.status) {
        const [[event]] = await db.execute('SELECT changed_by, observation FROM web_order_status_history WHERE order_id = ? AND revision = ?', [id, order.revision]);
        if (event?.changed_by === context.user.id && event.observation === input.observation) return { id, status: order.status, revision: order.revision, idempotent_replay: true };
      }
      if (order.revision !== input.expected_revision) throw new ApiError(409, 'WEB_ORDER_VERSION_CONFLICT');
      const transitions = { PENDING: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['READY', 'CANCELLED'], READY: ['COMPLETED', 'CANCELLED'], CANCELLED: [], COMPLETED: [] };
      if (!transitions[order.status]?.includes(input.status)) throw new ApiError(409, 'WEB_ORDER_STATUS_INVALID');
      const [[checkout]] = await db.execute(`SELECT s.status, p.id AS payment_id, r.id AS refund_id FROM sales s
        LEFT JOIN cashier_payments p ON p.sale_id = s.id LEFT JOIN sale_refunds r ON r.sale_id = s.id WHERE s.web_order_id = ?`, [id]);
      if (input.status === 'CANCELLED' && checkout) throw new ApiError(409, 'WEB_ORDER_ALREADY_IN_CASHIER');
      if (input.status === 'COMPLETED' && (!checkout || checkout.status !== 'PAID' || !checkout.payment_id || checkout.refund_id)) throw new ApiError(409, 'WEB_ORDER_PAYMENT_REQUIRED');
      await db.execute('UPDATE web_orders SET status = ?, revision = revision + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = ?', [input.status, id]);
      await db.execute(`INSERT INTO web_order_status_history (order_id, revision, previous_status, new_status, changed_by, observation)
        VALUES (?, ?, ?, ?, ?, ?)`, [id, order.revision + 1, order.status, input.status, context.user.id, input.observation]);
      return { id, status: input.status, revision: order.revision + 1, idempotent_replay: false };
    },
  };
}
