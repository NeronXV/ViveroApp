import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { SOURCE_TABLES } from '../scripts/source-export-tables.js';
import { prepareSourceCatalog } from '../scripts/source-catalog.js';
const demo = JSON.parse(await readFile(new URL('./fixtures/catalog-import-demo.json', import.meta.url), 'utf8'));
function fixture() {
  const tables = Object.fromEntries(SOURCE_TABLES.map(name => [name, []]));
  tables.categories = demo.categories.map(row => ({ ...row, created_at: '2026-10-01T12:00:00Z', updated_at: '2026-10-01T12:00:00Z' }));
  tables.products = demo.products.map(row => ({ ...row, price_cents: String(row.price_cents), wholesale_price_cents: null,
    minimum_stock: 2.125, created_at: '2026-10-01T12:00:00Z', updated_at: '2026-10-01T12:00:00Z' }));
  return { schema_version: 1, authority: 'supabase-export', exported_at: '2026-10-01T12:00:00Z', tables,
    primary_keys: SOURCE_TABLES.filter(name => name !== 'auth_users').map(table => ({ table, columns: ['id'] })), foreign_keys: [] };
}
function prepare(value) {
  const bytes = Buffer.from(JSON.stringify(value));
  return prepareSourceCatalog(bytes, { sourceKey: 'synthetic-source', expectedSha256: createHash('sha256').update(bytes).digest('hex') });
}
test('catalog adapter converts exact cents and retains minima and dates separately', () => {
  const value = fixture(), result = prepare(value);
  assert.equal(result.catalog.products[0].price_cents, 12000);
  assert.equal(result.metadata.inventory_minimums[0].minimum_quantity_milli, '2125');
  assert.equal(result.metadata.timestamps.products[0].created_at, value.tables.products[0].created_at);
  assert.equal(Object.hasOwn(result.catalog.products[0], 'minimum_stock'), false);
  assert.equal(value.tables.products[0].price_cents, '12000');
});
test('catalog adapter rejects unsafe money, extra columns, sub-milli minima and mismatched hash', () => {
  let value = fixture(); value.tables.products[0].price_cents = '9007199254740992';
  assert.throws(() => prepare(value), /CATALOG_MONEY_UNREPRESENTABLE/);
  value = fixture(); value.tables.products[0].unexpected = 'data';
  assert.throws(() => prepare(value), /SOURCE_CATALOG_COLUMNS_CHANGED/);
  value = fixture(); value.tables.products[0].minimum_stock = 0.0001;
  assert.throws(() => prepare(value), /CATALOG_MINIMUM_UNREPRESENTABLE/);
  assert.throws(() => prepareSourceCatalog(Buffer.from(JSON.stringify(fixture())), { sourceKey: 'demo', expectedSha256: '0'.repeat(64) }), /INPUT_HASH_MISMATCH/);
});
