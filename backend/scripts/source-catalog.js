import { inspectSourceExport } from './source-export-preflight.js';
import { validateSnapshot } from './catalog-import.js';

export class SourceCatalogError extends Error {}
const fail = code => { throw new SourceCatalogError(code); };
const categoryFields = ['id', 'name', 'description', 'is_active'];
const productFields = ['id', 'category_id', 'internal_code', 'barcode', 'common_name', 'scientific_name', 'description', 'price_cents', 'wholesale_price_cents', 'unit', 'watering_advice', 'light_type', 'recommended_climate', 'is_active'];
function pick(row, fields, extras) {
  const allowed = [...fields, ...extras];
  if (Object.keys(row).some(key => !allowed.includes(key)) || allowed.some(key => !Object.hasOwn(row, key))) fail('SOURCE_CATALOG_COLUMNS_CHANGED');
  return Object.fromEntries(fields.map(key => [key, row[key]]));
}
function cents(value, nullable = false) {
  if (value === null && nullable) return null;
  if (typeof value !== 'string' || !/^\d+$/.test(value) || BigInt(value) > BigInt(Number.MAX_SAFE_INTEGER)) fail('CATALOG_MONEY_UNREPRESENTABLE');
  return Number(value);
}
function milli(value) {
  const text = String(value);
  if (!['number', 'string'].includes(typeof value) || !/^\d{1,11}(?:\.\d{1,3})?$/.test(text)) fail('CATALOG_MINIMUM_UNREPRESENTABLE');
  const [whole, fraction = ''] = text.split('.');
  return (BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'))).toString();
}
// Build private files for the existing catalog importer. Operational inventory
// minima and source timestamps remain separate; no balances or movements run.
export function prepareSourceCatalog(bytes, { sourceKey, expectedSha256 }) {
  const report = inspectSourceExport(bytes);
  if (report.input_sha256 !== expectedSha256) fail('INPUT_HASH_MISMATCH');
  const { tables } = JSON.parse(bytes.toString('utf8'));
  const catalog = { schema_version: 1, source_key: sourceKey,
    categories: tables.categories.map(row => pick(row, categoryFields, ['created_at', 'updated_at'])),
    products: tables.products.map(row => {
      const product = pick(row, productFields, ['minimum_stock', 'created_at', 'updated_at']);
      product.price_cents = cents(product.price_cents);
      product.wholesale_price_cents = cents(product.wholesale_price_cents, true);
      return product;
    }) };
  validateSnapshot(catalog);
  const metadata = { input_sha256: report.input_sha256, source_key: sourceKey,
    inventory_minimums: tables.products.map(row => ({ source_product_id: row.id, minimum_quantity_milli: milli(row.minimum_stock) })),
    timestamps: Object.fromEntries(['categories', 'products'].map(entity => [entity, tables[entity].map(row => ({ source_id: row.id, created_at: row.created_at, updated_at: row.updated_at }))])),
    pending: ['Apply per-product minima when importing branch inventory', 'Import source image files separately'] };
  return { catalog, metadata };
}
