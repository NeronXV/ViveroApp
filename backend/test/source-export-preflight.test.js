import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectSourceExport } from '../scripts/source-export-preflight.js';
import { SOURCE_TABLES, LEGACY_ABSENT_TABLES } from '../scripts/source-export-tables.js';

function fixture() {
  return { schema_version: 1, authority: 'supabase-export', exported_at: '2026-10-01T12:00:00.000000Z',
    tables: Object.fromEntries(SOURCE_TABLES.map(name => [name, []])),
    primary_keys: SOURCE_TABLES.filter(name => name !== 'auth_users').map(table => ({ table, columns: ['id'] })),
    foreign_keys: [{ table: 'products', columns: ['category_id'], referenced_table: 'categories', referenced_columns: ['id'] }] };
}
const inspect = value => inspectSourceExport(Buffer.from(JSON.stringify(value)));
test('source snapshot inspection reports only hashes and counts and never applies data', () => {
  const value = fixture();
  value.tables.categories.push({ id: 'source-category', name: 'Demo privado' });
  value.tables.products.push({ id: 'source-product', category_id: 'source-category', price_cents: '9007199254740993' });
  const result = inspect(value);
  assert.equal(result.import_applied, false);
  assert.equal(result.source_validated, true);
  assert.match(result.input_sha256, /^[a-f0-9]{64}$/);
  assert.equal(result.tables.find(row => row.name === 'products').row_count, 1);
  assert.equal(JSON.stringify(result).includes('Demo privado'), false);
  assert.equal(JSON.stringify(result).includes('source-product'), false);
});
test('incomplete tables, primary keys, duplicate IDs and orphan references stop preparation', () => {
  let value = fixture(); delete value.tables.sales;
  assert.throws(() => inspect(value), /EXPORT_TABLES_INCOMPLETE/);
  value = fixture(); value.primary_keys.pop();
  assert.throws(() => inspect(value), /EXPORT_PRIMARY_KEYS_INCOMPLETE/);
  value = fixture(); value.tables.categories = [{ id: 'same' }, { id: 'same' }];
  assert.throws(() => inspect(value), /EXPORT_PRIMARY_KEY_INVALID/);
  value = fixture(); value.tables.products = [{ id: 'demo', category_id: 'missing' }];
  assert.throws(() => inspect(value), /EXPORT_FOREIGN_KEY_ORPHAN/);
});
test('unsafe numeric cents and credentials never reach a successful report', () => {
  for (const column of ['password_hash', 'encrypted_password', 'claim_token', 'confirmation_token', 'unsubscribe_token']) {
    const value = fixture(); value.tables.products.push({ id: 'demo', category_id: null, [column]: 'secret' });
    assert.throws(() => inspect(value), /EXPORT_CREDENTIALS_FORBIDDEN/);
  }
  for (const price_cents of [10, 0.1, '1.5', '9223372036854775808']) {
    const value = fixture(); value.tables.products.push({ id: 'demo', category_id: null, price_cents });
    assert.throws(() => inspect(value), /EXPORT_MONEY_INVALID/);
  }
});
test('composite source foreign keys match the complete tuple and permit source nulls', () => {
  const value = fixture();
  value.tables.categories.push({ id: 'category', code: 'DEMO' });
  value.tables.products.push({ id: 'product', category_id: 'category', category_code: 'OTHER' });
  value.foreign_keys = [{ table: 'products', columns: ['category_id', 'category_code'], referenced_table: 'categories', referenced_columns: ['id', 'code'] }];
  assert.throws(() => inspect(value), /EXPORT_FOREIGN_KEY_ORPHAN/);
  value.tables.products[0].category_code = 'DEMO';
  assert.equal(inspect(value).source_validated, true);
  value.tables.products[0].category_id = null;
  assert.equal(inspect(value).source_validated, true);
});

function legacyFixture() {
  const value = fixture();
  value.schema_version = 2;
  value.absent_tables = [...LEGACY_ABSENT_TABLES];
  for (const name of value.absent_tables) delete value.tables[name];
  value.primary_keys = value.primary_keys.filter(row => !value.absent_tables.includes(row.table));
  return value;
}
test('older source schema reports absent tables distinctly from empty tables', () => {
  const result = inspect(legacyFixture());
  assert.equal(result.schema_version, 2);
  assert.equal(result.tables.length, 31); // thirty public tables plus auth_users
  assert.deepEqual(result.absent_tables, LEGACY_ABSENT_TABLES);
  assert.equal(result.tables.some(row => row.name === 'sale_refunds'), false);
  assert.equal(result.tables.find(row => row.name === 'sales').row_count, 0);
  assert.equal(result.import_applied, false);
});
test('absent table declarations cannot hide mandatory data or conflicting metadata', () => {
  let value = legacyFixture(); value.absent_tables.push('sales'); delete value.tables.sales;
  assert.throws(() => inspect(value), /EXPORT_ABSENT_TABLES_INVALID/);
  value = legacyFixture(); value.tables.sale_refunds = [];
  assert.throws(() => inspect(value), /EXPORT_ABSENT_TABLES_INVALID/);
  value = legacyFixture(); value.absent_tables.push(value.absent_tables[0]);
  assert.throws(() => inspect(value), /EXPORT_ABSENT_TABLES_INVALID/);
  value = legacyFixture(); delete value.absent_tables;
  assert.throws(() => inspect(value), /EXPORT_ABSENT_TABLES_INVALID/);
  value = legacyFixture(); delete value.tables.products;
  assert.throws(() => inspect(value), /EXPORT_TABLES_INCOMPLETE/);
  value = legacyFixture(); value.primary_keys.push({table: 'sale_refunds', columns: ['id']});
  assert.throws(() => inspect(value), /EXPORT_CONSTRAINT_INVALID/);
  value = legacyFixture(); value.foreign_keys.push({table: 'sales', columns: ['refund_id'], referenced_table: 'sale_refunds', referenced_columns: ['id']});
  assert.throws(() => inspect(value), /EXPORT_CONSTRAINT_INVALID/);
  value = fixture(); value.absent_tables = [];
  assert.throws(() => inspect(value), /EXPORT_ABSENT_TABLES_INVALID/);
});
