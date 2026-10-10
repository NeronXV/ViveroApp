import { createHash } from 'node:crypto';
import { ApiError, positiveId } from './catalog.js';
import { inventoryScope, inventoryBody, lockInventoryRow, requestKey, milliFromDb, formatMilli, parseMilli } from './inventory.js';
import { requireInventoryOwner, inventoryPermissions } from './inventory-policy.js';

const fail = (code, status = 409) => { throw new ApiError(status, code); };
const hash = value => createHash('sha256').update(value).digest();

export function observationInput(input, key) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 5
    || !['product_id', 'counted_quantity', 'reason', 'baseline_quantity', 'baseline_movement_id'].every(k => Object.hasOwn(input, k))
    || !Number.isInteger(input.baseline_movement_id) || input.baseline_movement_id < 0 || input.baseline_movement_id > 4294967295) fail('INVENTORY_INPUT_INVALID', 400);
  const request = inventoryBody('count', { product_id: input.product_id, counted_quantity: input.counted_quantity, reason: input.reason }, key);
  return { ...request, baselineMilli: parseMilli(input.baseline_quantity, { zero: true }), baselineRevision: input.baseline_movement_id,
    idempotencyHash: requestKey(key, 'count-observation') };
}
export function decisionInput(input, key) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 2
    || !['APPROVE', 'REJECT'].includes(input.decision) || typeof input.reason !== 'string'
    || !input.reason.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(input.reason)
    || [...input.reason.trim()].length < 3 || [...input.reason.trim()].length > 240) fail('INVENTORY_INPUT_INVALID', 400);
  return { decision: input.decision, reason: input.reason.trim(), key: requestKey(key, 'count-decision') };
}
export function observationQuery(params) {
  if ([...params.keys()].some(k => !['limit', 'after_id', 'status'].includes(k) || params.getAll(k).length !== 1)) fail('INVENTORY_QUERY_INVALID', 400);
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  const status = params.get('status') ?? 'PENDING';
  if (limit > 100 || !['PENDING', 'APPLIED', 'CLOSED', 'REJECTED', 'ALL'].includes(status)) fail('INVENTORY_QUERY_INVALID', 400);
  return { limit, after: params.has('after_id') ? positiveId(params.get('after_id')) : 0, status };
}

export function createInventoryObservations(db, context) {
  const branchId = inventoryScope(context, true);
  async function snapshot(productId) {
    await db.execute('SELECT id FROM branches WHERE id = ? FOR UPDATE', [branchId]);
    const [[product]] = await db.execute('SELECT id FROM products WHERE id = ? FOR UPDATE', [productId]);
    if (!product) fail('INVENTORY_PRODUCT_UNAVAILABLE');
    const [[balance]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ? FOR UPDATE', [branchId, productId]);
    const [[movement]] = await db.execute('SELECT COALESCE(MAX(id), 0) AS revision FROM inventory_movements WHERE branch_id = ? AND product_id = ?', [branchId, productId]);
    return { quantity: balance?.quantity ?? '0.000', revision: Number(movement.revision) };
  }
  async function get(id, lock = false) {
    const [[row]] = await db.execute(`SELECT * FROM inventory_count_observations WHERE id = ? AND branch_id = ?${lock ? ' FOR UPDATE' : ''}`, [id, branchId]);
    if (!row) fail('INVENTORY_OBSERVATION_NOT_FOUND', 404);
    return row;
  }
  function receipt(row, replay = false) {
    // Never expose recovery/decision hashes or private identity data.
    return { schema_version: 1, observation_id: row.id, product_id: row.product_id, branch_id: row.branch_id,
      observed_quantity: formatMilli(milliFromDb(row.observed_quantity)), baseline_quantity: formatMilli(milliFromDb(row.baseline_quantity)),
      status: row.status, reason: row.reason, observed_by: row.observed_by, observed_at: row.observed_at,
      decided_by: row.decided_by, decided_at: row.decided_at, decision_reason: row.decision_reason,
      count_id: row.count_id, idempotent_replay: replay };
  }
  async function saved(request) {
    const [[row]] = await db.execute('SELECT * FROM inventory_count_observations WHERE idempotency_hash = ?', [request.idempotencyHash]);
    if (row && (row.branch_id !== branchId || row.product_id !== request.productId || row.observed_by !== context.user.id
      || milliFromDb(row.observed_quantity) !== request.quantityMilli || row.reason !== request.reason
      || milliFromDb(row.baseline_quantity) !== request.baselineMilli || row.baseline_movement_id !== request.baselineRevision)) fail('INVENTORY_IDEMPOTENCY_CONFLICT');
    return row;
  }
  return {
    async baseline(productId) {
      const before = await snapshot(productId);
      return { schema_version: 1, product_id: productId, branch_id: branchId, quantity: before.quantity, movement_id: before.revision };
    },
    async list(query) {
      const [rows] = await db.execute(`SELECT id FROM inventory_count_observations WHERE branch_id = ? AND id > ?
        AND (? = 'ALL' OR status = ?) ORDER BY id LIMIT ?`, [branchId, query.after, query.status, query.status, query.limit + 1]);
      return { schema_version: 1, branch_id: branchId, permissions: inventoryPermissions(context),
        items: await Promise.all(rows.slice(0, query.limit).map(async row => receipt(await get(row.id)))),
        next_after_id: rows.length > query.limit ? rows[query.limit - 1].id : null };
    },
    async result(request) {
      const row = await saved(request);
      if (!row) fail('INVENTORY_OPERATION_NOT_FOUND', 404);
      return receipt(row, true);
    },
    async observe(request) {
      const before = await snapshot(request.productId);
      const previous = await saved(request);
      if (previous) return receipt(previous, true);
      if (before.revision !== request.baselineRevision || milliFromDb(before.quantity) !== request.baselineMilli) fail('INVENTORY_COUNT_STALE');
      const [created] = await db.execute(`INSERT INTO inventory_count_observations
        (branch_id, product_id, observed_quantity, baseline_quantity, baseline_movement_id, reason, observed_by, idempotency_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)`, [branchId, request.productId, request.quantity, before.quantity, before.revision,
        request.reason, context.user.id, request.idempotencyHash]);
      return receipt(await get(created.insertId));
    },
    async decide(id, input) {
      requireInventoryOwner(context);
      // Branch first prevents approve/reception/payment races and lock inversion.
      await db.execute('SELECT id FROM branches WHERE id = ? FOR UPDATE', [branchId]);
      const row = await get(id, true);
      const fingerprint = hash(JSON.stringify([id, context.user.id, branchId, input.decision, input.reason]));
      if (row.status !== 'PENDING') {
        if (row.decided_by !== context.user.id || !row.decision_key?.equals(input.key) || !row.decision_hash?.equals(fingerprint)) fail('INVENTORY_DECISION_CONFLICT');
        return receipt(row, true);
      }
      const [[used]] = await db.execute('SELECT id FROM inventory_count_observations WHERE decision_key = ?', [input.key]);
      if (used) fail('INVENTORY_IDEMPOTENCY_CONFLICT');
      let countId = null, status = 'REJECTED';
      if (input.decision === 'APPROVE') {
        const current = await snapshot(row.product_id);
        if (Number(current.revision) !== row.baseline_movement_id || milliFromDb(current.quantity) !== milliFromDb(row.baseline_quantity)) fail('INVENTORY_COUNT_STALE');
        const adjustment = milliFromDb(row.observed_quantity) - milliFromDb(current.quantity);
        await lockInventoryRow(db, branchId, row.product_id);
        let movementId = null;
        if (adjustment !== 0n) {
          const [movement] = await db.execute(`INSERT INTO inventory_movements
            (branch_id, product_id, movement_type, quantity, idempotency_hash, notes, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)`,
          [branchId, row.product_id, adjustment > 0n ? 'ADJUSTMENT_ADD' : 'ADJUSTMENT_SUB', formatMilli(adjustment),
            hash(`approved-observation:${id}`), input.reason, context.user.id]);
          movementId = movement.insertId;
        }
        const [count] = await db.execute(`INSERT INTO inventory_counts
          (movement_id, idempotency_hash, branch_id, product_id, previous_quantity, counted_quantity, adjustment_quantity, reason, counted_by)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`, [movementId, hash(`approved-count:${id}`), branchId, row.product_id,
            current.quantity, row.observed_quantity, formatMilli(adjustment), row.reason, row.observed_by]);
        countId = count.insertId;
        status = adjustment === 0n ? 'CLOSED' : 'APPLIED';
        await db.execute(`INSERT INTO product_branch_preparation (product_id, branch_id, initial_count_confirmed_at, initial_count_confirmed_by)
          VALUES (?, ?, UTC_TIMESTAMP(6), ?) ON DUPLICATE KEY UPDATE product_id = VALUES(product_id)`, [row.product_id, branchId, context.user.id]);
      }
      await db.execute(`UPDATE inventory_count_observations SET status = ?, decided_by = ?, decided_at = UTC_TIMESTAMP(6),
        decision_reason = ?, decision_key = ?, decision_hash = ?, count_id = ? WHERE id = ? AND branch_id = ? AND status = 'PENDING'`,
      [status, context.user.id, input.reason, input.key, fingerprint, countId, id, branchId]);
      return receipt(await get(id));
    },
  };
}
