import { ApiError, positiveId } from './catalog.js';
import { promotionDate } from './promotion-dates.js';

const invalid = () => { throw new ApiError(400, 'INVALID_PROMOTION'); };
function text(value, min, max) {
  if (typeof value !== 'string' || /[\u0000-\u001f\u007f]/u.test(value)) invalid();
  const result = value.trim();
  if ([...result].length < min || [...result].length > max) invalid();
  return result;
}
function money(value, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) invalid();
  return value;
}
function date(value) {
  try { return promotionDate(value); } catch { invalid(); }
}

export function validatePromotion(input) {
  const keys = ['name', 'description', 'scope', 'promo_type', 'percentage_bps', 'fixed_amount_cents', 'min_purchase_cents', 'max_discount_cents', 'starts_at', 'ends_at', 'is_active', 'product_ids'];
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !keys.includes(key))) invalid();
  const result = {
    name: text(input.name, 2, 100), description: text(input.description ?? '', 0, 500),
    scope: input.scope, promo_type: input.promo_type,
    percentage_bps: input.percentage_bps ?? null, fixed_amount_cents: input.fixed_amount_cents ?? null,
    min_purchase_cents: money(input.min_purchase_cents === undefined ? 0 : input.min_purchase_cents),
    max_discount_cents: input.max_discount_cents == null ? null : money(input.max_discount_cents, 1),
    starts_at: date(input.starts_at ?? null), ends_at: date(input.ends_at ?? null),
    is_active: input.is_active === undefined ? true : input.is_active,
  };
  if (!['ALL_PRODUCTS', 'SELECTED_PRODUCTS'].includes(result.scope) || typeof result.is_active !== 'boolean') invalid();
  if (result.promo_type === 'PERCENTAGE') {
    if (!Number.isInteger(result.percentage_bps) || result.percentage_bps < 1 || result.percentage_bps > 10000 || result.fixed_amount_cents !== null) invalid();
  } else if (result.promo_type === 'FIXED_AMOUNT') {
    money(result.fixed_amount_cents, 1);
    if (result.percentage_bps !== null) invalid();
  } else invalid();
  if (result.starts_at && result.ends_at && result.starts_at >= result.ends_at) invalid();
  const ids = input.product_ids;
  if (!Array.isArray(ids) || ids.length > 1000 || new Set(ids).size !== ids.length) invalid();
  if ((result.scope === 'ALL_PRODUCTS' && ids.length) || (result.scope === 'SELECTED_PRODUCTS' && !ids.length)) invalid();
  for (const id of ids) { if (typeof id !== 'number') invalid(); positiveId(id); }
  return { data: result, productIds: [...ids].sort((a, b) => a - b) };
}

export function createPromotions(db) {
  return {
    async list({ limit, afterId }) {
      const [rows] = await db.execute(`SELECT id, name, description, scope, promo_type, percentage_bps,
        fixed_amount_cents, min_purchase_cents, max_discount_cents, is_active,
        DATE_FORMAT(starts_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS starts_at,
        DATE_FORMAT(ends_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS ends_at,
        DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at,
        DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS updated_at
        FROM promotions WHERE id > ? ORDER BY id LIMIT ?`, [afterId, limit + 1]);
      const items = rows.slice(0, limit).map(row => ({ ...row, is_active: Boolean(row.is_active),
        fixed_amount_cents: row.fixed_amount_cents === null ? null : Number(row.fixed_amount_cents),
        min_purchase_cents: Number(row.min_purchase_cents), max_discount_cents: row.max_discount_cents === null ? null : Number(row.max_discount_cents), product_ids: [] }));
      if (items.length) {
        const [links] = await db.execute(`SELECT promotion_id, product_id FROM promotion_products WHERE is_active = 1 AND promotion_id IN (${items.map(() => '?').join(',')}) ORDER BY product_id`, items.map(item => item.id));
        const byId = new Map(items.map(item => [item.id, item]));
        for (const link of links) byId.get(link.promotion_id).product_ids.push(link.product_id);
      }
      return { items, next_after_id: rows.length > limit ? items.at(-1).id : null };
    },
    async save(id, { data, productIds }) {
      if (id !== null) {
        const [[current]] = await db.execute('SELECT id FROM promotions WHERE id = ? FOR UPDATE', [id]);
        if (!current) throw new ApiError(404, 'NOT_FOUND');
      }
      if (productIds.length) {
        const [products] = await db.execute(`SELECT id FROM products WHERE id IN (${productIds.map(() => '?').join(',')}) ORDER BY id LOCK IN SHARE MODE`, productIds);
        if (products.length !== productIds.length) throw new ApiError(409, 'REFERENCE_CONFLICT');
      }
      const keys = Object.keys(data);
      if (id === null) {
        const [created] = await db.execute(`INSERT INTO promotions (${keys.join(',')}) VALUES (${keys.map(() => '?').join(',')})`, Object.values(data));
        id = created.insertId;
      } else {
        await db.execute(`UPDATE promotions SET ${keys.map(key => `${key} = ?`).join(',')} WHERE id = ?`, [...Object.values(data), id]);
      }
      await db.execute('UPDATE promotion_products SET is_active = 0 WHERE promotion_id = ?', [id]);
      if (productIds.length) {
        await db.execute(`INSERT INTO promotion_products (promotion_id, product_id) VALUES ${productIds.map(() => '(?, ?)').join(',')}
          ON DUPLICATE KEY UPDATE is_active = 1`, productIds.flatMap(productId => [id, productId]));
      }
      return { id };
    },
    async deactivate(id) {
      const [result] = await db.execute('UPDATE promotions SET is_active = 0 WHERE id = ?', [id]);
      if (!result.affectedRows) throw new ApiError(404, 'NOT_FOUND');
      return { id };
    },
  };
}
