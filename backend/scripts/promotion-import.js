import { validatePromotion } from '../src/promotions.js';
import { promotionDate } from '../src/promotion-dates.js';
import { ImportError, fail, canonical, hash, uuid, exactKeys, runImportTransaction } from './catalog-import.js';

const fields = ['id', 'name', 'description', 'scope', 'promo_type', 'value', 'min_purchase_cents', 'max_discount_cents', 'starts_at', 'ends_at', 'is_active', 'created_at', 'product_ids'];
function sourceAmount(value, type) {
  // Supabase numeric(14,2), transported as text to avoid floating-point conversion.
  if (typeof value !== 'string' || !/^(0|[1-9][0-9]{0,11})(\.[0-9]{1,2})?$/.test(value)) fail('INVALID_SOURCE_VALUE');
  const [whole, fraction = ''] = value.split('.');
  const hundredths = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  if (type === 'PERCENTAGE') {
    if (hundredths < 1n || hundredths > 10000n) fail('INVALID_SOURCE_VALUE');
    return { percentage_bps: Number(hundredths), fixed_amount_cents: null };
  }
  if (type !== 'FIXED_AMOUNT' || hundredths < 100n || hundredths % 100n !== 0n) fail('INVALID_SOURCE_VALUE');
  return { percentage_bps: null, fixed_amount_cents: Number(hundredths / 100n) };
}

export function validatePromotionSnapshot(input) {
  exactKeys(input, ['schema_version', 'source_key', 'promotions']);
  if (input.schema_version !== 2 || typeof input.source_key !== 'string' || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(input.source_key)) fail('INVALID_ENVELOPE');
  if (!Array.isArray(input.promotions) || !input.promotions.length || input.promotions.length > 100) fail('BATCH_LIMIT');
  const seen = new Set();
  const rows = input.promotions.map((row, index) => {
    try {
      exactKeys(row, fields);
      const sourceId = uuid(row.id);
      if (seen.has(sourceId)) fail('DUPLICATE_SOURCE_ID');
      seen.add(sourceId);
      if (Object.values(row).some(value => typeof value === 'string' && !value.isWellFormed())) fail('INVALID_UNICODE');
      if (row.scope === 'SALE') fail('SALE_PROMOTION_OUT_OF_SCOPE');
      if (!Array.isArray(row.product_ids) || row.product_ids.length > 1000) fail('INVALID_PRODUCT_IDS');
      const sourceProductIds = row.product_ids.map(uuid).sort();
      if (new Set(sourceProductIds).size !== sourceProductIds.length) fail('DUPLICATE_PRODUCT_ID');
      const { id: _id, value: _value, created_at: createdAt, product_ids: _products, ...raw } = row;
      if (typeof row.is_active !== 'boolean' || row.min_purchase_cents === null) fail('INVALID_RECORD');
      const created_at = promotionDate(createdAt);
      if (!created_at) fail('CREATED_AT_REQUIRED');
      const data = validatePromotion({ ...raw, ...sourceAmount(row.value, row.promo_type), product_ids: sourceProductIds.map((_, i) => i + 1) }).data;
      if (data.name !== row.name || data.description !== (row.description ?? '')) fail('NORMALIZATION_REQUIRED');
      data.created_at = created_at;
      return { sourceId, data, sourceProductIds, sourceHash: hash({ ...data, product_ids: sourceProductIds }) };
    } catch (error) { fail(error instanceof ImportError ? error.code : 'INVALID_RECORD', 'promotions', index); }
  });
  return { sourceKey: input.source_key, rows };
}

async function promotionPlan(db, snapshot, apply) {
  const report = { mode: apply ? 'apply' : 'dry-run', can_apply: true, items: [] };
  const resolved = [];
  const lock = apply ? ' FOR UPDATE' : '';
  for (const [index, row] of snapshot.rows.entries()) {
    const [[mapping]] = await db.execute(`SELECT promotion_id, source_hash FROM catalog_promotion_sources WHERE source_key = ? AND source_id = ?${lock}`, [snapshot.sourceKey, row.sourceId]);
    const item = { entity: 'promotions', index, action: mapping ? 'reuse' : 'create', target_id: mapping?.promotion_id ?? null };
    const conflict = code => { item.action = 'conflict'; item.code ??= code; report.can_apply = false; };
    const productIds = [];
    if (row.sourceProductIds.length) {
      const [maps] = await db.execute(`SELECT m.product_id FROM catalog_product_sources m JOIN products p ON p.id = m.product_id
        WHERE m.source_key = ? AND m.source_id IN (${row.sourceProductIds.map(() => '?').join(',')}) ORDER BY m.product_id${lock}`, [snapshot.sourceKey, ...row.sourceProductIds]);
      if (maps.length !== row.sourceProductIds.length) conflict('PRODUCT_MAPPING_REQUIRED');
      productIds.push(...maps.map(map => map.product_id));
    }
    resolved.push(productIds);
    if (mapping) {
      if (!mapping.source_hash.equals(row.sourceHash)) conflict('SOURCE_CHANGED');
      const [[stored]] = await db.execute(`SELECT name, description, scope, promo_type, percentage_bps, fixed_amount_cents,
        min_purchase_cents, max_discount_cents, is_active,
        DATE_FORMAT(starts_at, '%Y-%m-%d %H:%i:%s.%f') AS starts_at,
        DATE_FORMAT(ends_at, '%Y-%m-%d %H:%i:%s.%f') AS ends_at,
        DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at FROM promotions WHERE id = ?${lock}`, [mapping.promotion_id]);
      if (!stored) conflict('MISSING_TARGET');
      else {
        stored.is_active = Boolean(stored.is_active);
        for (const key of ['fixed_amount_cents', 'min_purchase_cents', 'max_discount_cents']) if (stored[key] !== null) stored[key] = Number(stored[key]);
        if (canonical(stored) !== canonical(row.data)) conflict('TARGET_CHANGED');
        const [links] = await db.execute(`SELECT product_id FROM promotion_products WHERE promotion_id = ? AND is_active = 1 ORDER BY product_id${lock}`, [mapping.promotion_id]);
        if (JSON.stringify(links.map(link => link.product_id)) !== JSON.stringify(productIds)) conflict('TARGET_SELECTION_CHANGED');
      }
    }
    report.items.push(item);
  }
  return { report, resolved };
}

export async function importPromotions(db, input, { apply = false } = {}) {
  const snapshot = validatePromotionSnapshot(input);
  return runImportTransaction(db, apply, '007_promotion_imports', async () => {
    const { report, resolved } = await promotionPlan(db, snapshot, apply);
    if (!apply || !report.can_apply) return report;
    for (const item of report.items) {
      if (item.action !== 'create') continue;
      const row = snapshot.rows[item.index];
      const keys = Object.keys(row.data);
      const [created] = await db.execute(`INSERT INTO promotions (${keys.join(',')}) VALUES (${keys.map(() => '?').join(',')})`, Object.values(row.data));
      item.target_id = created.insertId;
      const ids = resolved[item.index];
      if (ids.length) await db.execute(`INSERT INTO promotion_products (promotion_id, product_id) VALUES ${ids.map(() => '(?, ?)').join(',')}`, ids.flatMap(id => [item.target_id, id]));
      await db.execute('INSERT INTO catalog_promotion_sources (source_key, source_id, promotion_id, source_hash) VALUES (?, ?, ?, ?)', [snapshot.sourceKey, row.sourceId, item.target_id, row.sourceHash]);
    }
    return report;
  });
}
