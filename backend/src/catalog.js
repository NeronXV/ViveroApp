import { pricedCatalogQuery } from './catalog-pricing.js';
import { priceCatalogPage } from './catalog-pricing.js';
import { lockInventoryRow } from './inventory.js';

export class ApiError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

const invalid = () => { throw new ApiError(400, 'INVALID_INPUT'); };
const fields = {
  internal_code: [2, 40], barcode: [4, 128, true], common_name: [2, 160],
  scientific_name: [0, 160, true], description: [0, 2000],
  watering_advice: [0, 2000], light_type: [0, 160], recommended_climate: [0, 160],
};

function text(value, [min, max, nullable]) {
  if (nullable && value === null) return null;
  if (typeof value !== 'string') return invalid();
  const trimmed = value.trim();
  if ([...trimmed].length < min || [...trimmed].length > max || /[\u0000-\u0008\u000b-\u001f\u007f]/u.test(trimmed)) return invalid();
  return trimmed;
}

export function positiveId(value) {
  if (typeof value === 'string' && !/^[1-9][0-9]*$/.test(value)) return invalid();
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1 || number > 4294967295) return invalid();
  return number;
}

function object(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalid();
}

export function validateProduct(value, partial = false) {
  object(value);
  const output = {};
  for (const [key, input] of Object.entries(value)) {
    if (Object.hasOwn(fields, key)) output[key] = text(input, fields[key]);
    else if (key === 'category_id') {
      if (typeof input !== 'number') invalid();
      output[key] = positiveId(input);
    } else if (key === 'price_cents' || key === 'wholesale_price_cents') {
      if (!(key === 'wholesale_price_cents' && input === null) && (!Number.isSafeInteger(input) || input < 0)) invalid();
      output[key] = input;
    } else if (key === 'minimum_stock') {
      if (typeof input !== 'number' || !Number.isFinite(input) || input < 0 || input > 99999999999.999 || !/^\d+(?:\.\d{1,3})?$/.test(String(input))) invalid();
      output[key] = input;
    } else if (key === 'unit') {
      if (!['pieza', 'maceta', 'charola', 'bolsa', 'kg'].includes(input)) invalid();
      output[key] = input;
    } else if (key === 'is_active') {
      if (typeof input !== 'boolean') invalid();
      output[key] = input;
    } else invalid();
  }
  if (!Object.keys(output).length) invalid();
  if (!partial) {
    for (const key of ['internal_code', 'common_name', 'category_id', 'price_cents']) {
      if (!Object.hasOwn(output, key)) invalid();
    }
  }
  return output;
}

export function validateCategory(value, partial = false) {
  object(value);
  const output = {};
  for (const [key, input] of Object.entries(value)) {
    if (key === 'name') output.name = text(input, [2, 100]);
    else if (key === 'description') output.description = text(input, [0, 2000]);
    else if (key === 'is_active' && typeof input === 'boolean') output.is_active = input;
    else invalid();
  }
  if (!Object.keys(output).length || (!partial && !Object.hasOwn(output, 'name'))) invalid();
  return output;
}

export function pagination(params) {
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  if (limit > 100) invalid();
  return { limit, afterId: params.has('after_id') ? positiveId(params.get('after_id')) : 0 };
}

export function productFilters(params) {
  const search = (params.get('search') ?? '').trim();
  if (search.length > 80 || !search.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(search)) invalid();
  return { search, categoryId: params.has('category_id') ? positiveId(params.get('category_id')) : null };
}

export function scanInput(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 1 || !Object.hasOwn(input, 'code')
    || typeof input.code !== 'string' || !input.code.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(input.code)) throw new ApiError(400, 'PRODUCT_SCAN_CODE_INVALID');
  const code = input.code.trim();
  if ([...code].length < 2 || [...code].length > 128) throw new ApiError(400, 'PRODUCT_SCAN_CODE_INVALID');
  return code;
}

function serialize(row) {
  const result = { ...row, is_active: Boolean(row.is_active) };
  if (Object.hasOwn(row, 'image_id')) {
    result.image = row.image_id ? { id: row.image_id, url: `/api/v1/images/${row.image_id}`, alt_text: row.image_alt } : null;
    delete result.image_id;
    delete result.image_alt;
  }
  if (Object.hasOwn(row, 'price_cents')) {
    result.price_cents = Number(row.price_cents);
    if (!Number.isSafeInteger(result.price_cents)) throw new Error('Invalid stored price');
  }
  if (Object.hasOwn(row, 'wholesale_price_cents')) {
    result.wholesale_price_cents = row.wholesale_price_cents === null ? null : Number(row.wholesale_price_cents);
    if (result.wholesale_price_cents !== null && !Number.isSafeInteger(result.wholesale_price_cents)) throw new Error('Invalid stored wholesale price');
  }
  if (Object.hasOwn(row, 'effective_price_cents')) {
    result.effective_price_cents = Number(row.effective_price_cents);
    if (!Number.isSafeInteger(result.effective_price_cents)) throw new Error('Invalid effective price');
    result.active_promotion = row.promotion_id === null ? null : {
      id: row.promotion_id, name: row.promotion_name, discount_percent: Number(row.discount_percent),
    };
    delete result.promotion_id;
    delete result.promotion_name;
    delete result.discount_percent;
  }
  return result;
}

// Identifiers are internal constants / keys returned by strict validators.
// All client values are prepared-statement parameters.
export function createCatalog(db, context = null) {
  const branchId = context?.branch?.is_active ? context.branch.id : null;
  async function minimum(productId, value) {
    if (value === undefined) return;
    if (!branchId) throw new ApiError(403, 'BRANCH_FORBIDDEN');
    await lockInventoryRow(db, branchId, productId);
    await db.execute('UPDATE inventory SET minimum_stock = ? WHERE branch_id = ? AND product_id = ?', [value, branchId, productId]);
  }
  return {
    async scan(code) {
      // One pricing snapshot; count active matches before hiding an inactive category.
      // Binary collation preserves accents while LOWER provides case-insensitive codes.
      const [rows] = await db.execute(priceCatalogPage(`SELECT p.id, p.internal_code, p.barcode, p.common_name,
        p.scientific_name, p.description, p.category_id, p.price_cents, p.unit, p.is_active,
        p.watering_advice, p.light_type, p.recommended_climate, c.is_active AS category_active,
        i.id AS image_id, i.alt_text AS image_alt FROM products p JOIN categories c ON c.id=p.category_id
        LEFT JOIN product_images i ON i.primary_product_id=p.id
        WHERE p.is_active=1 AND (LOWER(TRIM(p.internal_code)) COLLATE utf8mb4_bin=LOWER(?) COLLATE utf8mb4_bin
          OR LOWER(TRIM(p.barcode)) COLLATE utf8mb4_bin=LOWER(?) COLLATE utf8mb4_bin)
        ORDER BY p.id LIMIT 2`), [code, code]);
      if (rows.length > 1) throw new ApiError(409, 'PRODUCT_SCAN_CODE_AMBIGUOUS');
      if (!rows.length || !rows[0].category_active) return { schema_version: 1, item: null };
      const { category_active, ...product } = rows[0];
      return { schema_version: 1, item: serialize(product) };
    },
    async list(resource, { limit, afterId, search = '', categoryId = null }, all) {
      const table = resource === 'products' ? 'products' : 'categories';
      const columns = table === 'products'
        ? `p.id, p.internal_code, p.barcode, p.common_name, p.scientific_name, p.description, p.category_id, p.price_cents, p.unit, p.is_active, p.watering_advice, p.light_type, p.recommended_climate, i.id AS image_id, i.alt_text AS image_alt${all ? ", p.wholesale_price_cents, COALESCE((SELECT minimum_stock FROM inventory WHERE branch_id = ? AND product_id = p.id), 0) AS minimum_stock, DATE_FORMAT(p.created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at, DATE_FORMAT(p.updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS updated_at" : ''}`
        : `p.id, p.name, p.description, p.is_active${all ? ", DATE_FORMAT(p.created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at, DATE_FORMAT(p.updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS updated_at" : ''}`;
      const active = table === 'products'
        ? 'p.is_active = 1 AND EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id AND c.is_active = 1)'
        : 'p.is_active = 1';
      const [rows] = await db.execute(
        table === 'products' ? pricedCatalogQuery(columns, all)
          : `SELECT ${columns} FROM ${table} p WHERE p.id > ? ${all ? '' : `AND ${active}`} ORDER BY p.id LIMIT ?`,
        table === 'products' ? [...(all ? [branchId] : []), afterId, categoryId, categoryId, search, search, search, limit + 1] : [afterId, limit + 1],
      );
      const items = rows.slice(0, limit).map(serialize);
      return { items, next_after_id: rows.length > limit ? items.at(-1).id : null };
    },
    async create(resource, data) {
      const table = resource === 'products' ? 'products' : 'categories';
      const { minimum_stock, ...stored } = data;
      const keys = Object.keys(stored);
      const [result] = await db.execute(
        `INSERT INTO ${table} (${keys.join(', ')}) VALUES (${keys.map(() => '?').join(', ')})`,
        Object.values(stored),
      );
      await minimum(result.insertId, minimum_stock);
      return { id: result.insertId };
    },
    async update(id, data, resource = 'products') {
      const table = resource === 'categories' ? 'categories' : 'products';
      const { minimum_stock, ...stored } = data;
      if (Object.hasOwn(data, 'minimum_stock')) {
        const [[existing]] = await db.execute('SELECT id FROM products WHERE id = ?', [id]);
        if (!existing) throw new ApiError(404, 'NOT_FOUND');
        await minimum(id, minimum_stock);
      }
      if (!Object.keys(stored).length) return { id };
      const [result] = await db.execute(
        `UPDATE ${table} SET ${Object.keys(stored).map(key => `${key} = ?`).join(', ')} WHERE id = ?`,
        [...Object.values(stored), id],
      );
      if (!result.affectedRows) throw new ApiError(404, 'NOT_FOUND');
      return { id };
    },
  };
}
