import { createHash } from 'node:crypto';
import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { requireInventoryOwner, inventoryPermissions } from './inventory-policy.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const maxMilli = 99999999999999n;
export function inventoryScope(context, write = false) {
  const capability = write ? 'MANAGE_INVENTORY' : null;
  if (context.access_state !== 'ACTIVE' || (capability
    ? !context.capabilities.includes(capability)
    : !context.capabilities.some(name => ['MANAGE_INVENTORY', 'VIEW_INVENTORY_ALERTS'].includes(name)))) fail(403, 'INVENTORY_UNAUTHORIZED');
  requireCapabilities(context, [], context.branch?.id);
  if (!context.branch?.is_active) fail(403, 'INVENTORY_UNAUTHORIZED');
  return context.branch.id;
}

// Exact decimal strings keep DECIMAL(14,3) quantities out of binary floats.
export function parseMilli(value, { zero = false, integer = false } = {}) {
  if (typeof value !== 'string' || !/^(?:0|[1-9][0-9]{0,10})(?:\.[0-9]{1,3})?$/.test(value)) fail(400, 'INVENTORY_QUANTITY_INVALID');
  const [whole, fraction = ''] = value.split('.');
  const milli = BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
  if (milli > maxMilli || (!zero && milli === 0n) || (integer && milli % 1000n !== 0n)) fail(400, 'INVENTORY_QUANTITY_INVALID');
  return milli;
}
export const formatMilli = value => {
  const milli = BigInt(value);
  const absolute = milli < 0n ? -milli : milli;
  return `${milli < 0n ? '-' : ''}${absolute / 1000n}.${(absolute % 1000n).toString().padStart(3, '0')}`;
};
export const milliFromDb = value => {
  const text = String(value);
  const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
  return (text.startsWith('-') ? -1n : 1n) * (BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0')));
};
export function requestKey(value, operation) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9._:-]{16,128}$/.test(value)) fail(400, 'INVENTORY_IDEMPOTENCY_KEY_REQUIRED');
  return createHash('sha256').update(`${operation}\0`).update(value).digest();
}
function exactObject(value, expected) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== expected.length || expected.some(key => !Object.hasOwn(value, key))) fail(400, 'INVENTORY_INPUT_INVALID');
}
export function inventoryBody(path, input, key) {
  const idempotencyHash = requestKey(key, path);
  if (path === 'reception') {
    exactObject(input, ['product_id', 'quantity', 'notes']);
    if (typeof input.product_id !== 'number') fail(400, 'INVENTORY_INPUT_INVALID');
    const notes = input.notes === null ? null : input.notes;
    if (notes !== null && (typeof notes !== 'string' || !notes.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(notes) || [...notes.trim()].length > 240)) fail(400, 'INVENTORY_INPUT_INVALID');
    const quantity = parseMilli(input.quantity, { integer: true });
    return { productId: positiveId(input.product_id), quantityMilli: quantity, quantity: formatMilli(quantity), notes: notes?.trim() || null, idempotencyHash };
  }
  exactObject(input, ['product_id', 'counted_quantity', 'reason']);
  if (typeof input.product_id !== 'number' || typeof input.reason !== 'string' || !input.reason.isWellFormed()
    || /[\u0000-\u001f\u007f-\u009f]/u.test(input.reason)) fail(400, 'INVENTORY_INPUT_INVALID');
  const reason = input.reason.trim();
  if ([...reason].length < 3 || [...reason].length > 240) fail(400, 'INVENTORY_INPUT_INVALID');
  const quantity = parseMilli(input.counted_quantity, { zero: true, integer: true });
  return { productId: positiveId(input.product_id), quantityMilli: quantity, quantity: formatMilli(quantity), reason, idempotencyHash };
}
export function inventoryQuery(params, history = false) {
  const allowed = history ? ['limit', 'before_id', 'product_id'] : ['limit', 'after_product_id'];
  if ([...params.keys()].some(key => !allowed.includes(key) || params.getAll(key).length !== 1)) fail(400, 'INVENTORY_QUERY_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : history ? 50 : 100;
  if (limit > 100) fail(400, 'INVENTORY_QUERY_INVALID');
  return { limit, beforeId: params.has('before_id') ? positiveId(params.get('before_id')) : null,
    productId: params.has('product_id') ? positiveId(params.get('product_id')) : null,
    afterProductId: params.has('after_product_id') ? positiveId(params.get('after_product_id')) : 0 };
}

const hashReplay = (row, request, actorId, branchId) => row && row.created_by === actorId && row.branch_id === branchId
  && row.product_id === request.productId && milliFromDb(row.quantity).toString() === request.quantityMilli.toString()
  && row.notes === request.notes;

export async function lockInventoryRow(db, branchId, productId) {
  // Serialize branch inventory before INSERT IGNORE's shared duplicate-key lock
  // can race with another transaction upgrading the same balance to FOR UPDATE.
  await db.execute('SELECT id FROM branches WHERE id = ? FOR UPDATE', [branchId]);
  await db.execute('INSERT IGNORE INTO inventory (branch_id, product_id, quantity) VALUES (?, ?, 0)', [branchId, productId]);
  const [[balance]] = await db.execute('SELECT quantity, minimum_stock FROM inventory WHERE branch_id = ? AND product_id = ? FOR UPDATE', [branchId, productId]);
  if (!balance) fail(503, 'INVENTORY_BALANCE_UNAVAILABLE');
  return balance;
}

export function activationInput(input) {
  exactObject(input, ['initial_count_confirmed']);
  if (input.initial_count_confirmed !== true) fail(400, 'INVENTORY_INITIAL_COUNT_REQUIRED');
  return input;
}
export function createInventoryActivation(db, context) {
  const branchId = inventoryScope(context, true);
  async function state() {
    const [[row]] = await db.execute(`SELECT inventory_enabled AS enabled,
      DATE_FORMAT(inventory_activated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS activated_at,
      inventory_activated_by AS activated_by FROM branches WHERE id = ? FOR UPDATE`, [branchId]);
    return { schema_version: 1, branch_id: branchId, ...row, enabled: Boolean(row.enabled) };
  }
  return {
    state,
    async activate() {
      requireInventoryOwner(context);
      const before = await state();
      if (!before.enabled) await db.execute('UPDATE branches SET inventory_enabled = 1, inventory_activated_at = UTC_TIMESTAMP(6), inventory_activated_by = ? WHERE id = ?', [context.user.id, branchId]);
      return { ...await state(), idempotent_replay: before.enabled };
    },
  };
}

export function createInventory(db, context) {
  const branchId = inventoryScope(context);
  async function lockBalance(productId) {
    // Same branch-before-product lock order as authenticated API operations.
    await db.execute('SELECT id FROM branches WHERE id = ? FOR UPDATE', [branchId]);
    const [[product]] = await db.execute(`SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id
      WHERE p.id = ? AND p.is_active = 1 AND c.is_active = 1 LOCK IN SHARE MODE`, [productId]);
    if (!product) fail(409, 'INVENTORY_PRODUCT_UNAVAILABLE');
    return lockInventoryRow(db, branchId, productId);
  }
  return {
    async result(request, operation) {
      inventoryScope(context, true);
      if (operation === 'receptions') {
        const [[row]] = await db.execute('SELECT id, branch_id, product_id, movement_type, quantity, notes, created_by FROM inventory_movements WHERE idempotency_hash = ?', [request.idempotencyHash]);
        if (!row) fail(404, 'INVENTORY_OPERATION_NOT_FOUND');
        if (!hashReplay(row, request, context.user.id, branchId) || row.movement_type !== 'RECEPTION') fail(409, 'INVENTORY_IDEMPOTENCY_CONFLICT');
        return { schema_version: 1, idempotent_replay: true, movement_id: row.id, product_id: row.product_id, quantity: formatMilli(milliFromDb(row.quantity)), total_quantity: await currentQuantity(row.product_id) };
      }
      const [[row]] = await db.execute('SELECT id, branch_id, product_id, previous_quantity, counted_quantity, adjustment_quantity, reason, counted_by FROM inventory_counts WHERE idempotency_hash = ?', [request.idempotencyHash]);
      if (!row) fail(404, 'INVENTORY_OPERATION_NOT_FOUND');
      if (row.branch_id !== branchId || row.product_id !== request.productId || row.counted_by !== context.user.id || milliFromDb(row.counted_quantity) !== request.quantityMilli || row.reason !== request.reason) fail(409, 'INVENTORY_IDEMPOTENCY_CONFLICT');
      return { schema_version: 1, idempotent_replay: true, count_id: row.id, product_id: row.product_id, previous_quantity: formatMilli(milliFromDb(row.previous_quantity)), counted_quantity: formatMilli(milliFromDb(row.counted_quantity)), adjustment_quantity: formatMilli(milliFromDb(row.adjustment_quantity)), total_quantity: formatMilli(milliFromDb(row.counted_quantity)) };
    },
    async dashboard({ limit, afterProductId }) {
      const [rows] = await db.execute(`SELECT p.id AS product_id, p.common_name AS product_name, p.internal_code AS product_code, p.unit AS product_unit,
        COALESCE(i.quantity, 0) AS total_quantity, COALESCE(i.minimum_stock, 0) AS minimum_stock, i.updated_at AS balance_updated_at
        FROM products p JOIN categories c ON c.id = p.category_id
        LEFT JOIN inventory i ON i.product_id = p.id AND i.branch_id = ?
        WHERE c.is_active = 1 AND p.id > ? ORDER BY p.id LIMIT ?`, [branchId, afterProductId, limit + 1]);
      const items = rows.slice(0, limit).map(row => ({ product_id: row.product_id, product_name: row.product_name, product_code: row.product_code,
        product_unit: row.product_unit, total_quantity: formatMilli(milliFromDb(row.total_quantity)), minimum_stock: formatMilli(milliFromDb(row.minimum_stock)),
        is_low_stock: milliFromDb(row.total_quantity) <= milliFromDb(row.minimum_stock), balance_updated_at: row.balance_updated_at }));
      return { schema_version: 1, branch_id: branchId, permissions: inventoryPermissions(context), items, has_more: rows.length > limit, next_product_id: rows.length > limit ? items.at(-1).product_id : null };
    },
    async history({ limit, beforeId, productId }) {
      const [rows] = await db.execute(`SELECT m.id, m.product_id, p.common_name AS product_name, p.internal_code AS product_code,
        m.movement_type, m.quantity, m.notes, m.created_at, u.full_name AS created_by_label
        FROM inventory_movements m JOIN products p ON p.id = m.product_id LEFT JOIN users u ON u.id = m.created_by
        WHERE m.branch_id = ? AND (? IS NULL OR m.product_id = ?) AND (? IS NULL OR m.id < ?)
        ORDER BY m.id DESC LIMIT ?`, [branchId, productId, productId, beforeId, beforeId, limit + 1]);
      const items = rows.slice(0, limit).map(row => ({ ...row, quantity: formatMilli(milliFromDb(row.quantity)),
        created_by_label: row.created_by_label ?? (row.movement_type === 'OPENING' ? 'Carga inicial de migración' : null) }));
      return { schema_version: 1, branch_id: branchId, items, has_more: rows.length > limit, next_before_id: rows.length > limit ? items.at(-1).id : null };
    },
    async reception(request) {
      inventoryScope(context, true);
      requireInventoryOwner(context);
      await lockBalance(request.productId);
      const [[previous]] = await db.execute('SELECT id, branch_id, product_id, movement_type, quantity, notes, created_by FROM inventory_movements WHERE idempotency_hash = ? FOR UPDATE', [request.idempotencyHash]);
      if (previous) {
        if (!hashReplay(previous, request, context.user.id, branchId) || previous.movement_type !== 'RECEPTION') fail(409, 'INVENTORY_IDEMPOTENCY_CONFLICT');
        return { schema_version: 1, idempotent_replay: true, movement_id: previous.id, product_id: previous.product_id, quantity: formatMilli(milliFromDb(previous.quantity)), total_quantity: await currentQuantity(request.productId) };
      }
      const [result] = await db.execute(`INSERT INTO inventory_movements
        (branch_id, product_id, movement_type, quantity, idempotency_hash, notes, created_by)
        VALUES (?, ?, 'RECEPTION', ?, ?, ?, ?)`, [branchId, request.productId, request.quantity, request.idempotencyHash, request.notes, context.user.id]);
      return { schema_version: 1, idempotent_replay: false, movement_id: result.insertId, product_id: request.productId,
        quantity: request.quantity, total_quantity: await currentQuantity(request.productId) };
    },
    async reconcile(request) {
      inventoryScope(context, true);
      await lockBalance(request.productId);
      const [[previous]] = await db.execute(`SELECT c.id, c.branch_id, c.product_id, c.previous_quantity, c.counted_quantity, c.adjustment_quantity, c.reason, c.counted_by
        FROM inventory_counts c WHERE c.idempotency_hash = ? FOR UPDATE`, [request.idempotencyHash]);
      if (previous) {
        if (previous.branch_id !== branchId || previous.product_id !== request.productId || previous.counted_by !== context.user.id
          || milliFromDb(previous.counted_quantity).toString() !== request.quantityMilli.toString() || previous.reason !== request.reason) fail(409, 'INVENTORY_IDEMPOTENCY_CONFLICT');
        return { schema_version: 1, idempotent_replay: true, count_id: previous.id, product_id: previous.product_id,
          previous_quantity: formatMilli(milliFromDb(previous.previous_quantity)), counted_quantity: request.quantity,
          adjustment_quantity: formatMilli(milliFromDb(previous.adjustment_quantity)), total_quantity: request.quantity };
      }
      // Preserve recovery of completed historic requests, but do not interpret an
      // old client count as a newly approved adjustment without a snapshot.
      fail(409, 'INVENTORY_OBSERVATION_REQUIRED');
    },
  };
  async function currentQuantity(productId) {
    const [[row]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branchId, productId]);
    return formatMilli(milliFromDb(row.quantity));
  }
}
