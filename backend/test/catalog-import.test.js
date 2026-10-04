import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { importCatalog, validateSnapshot } from '../scripts/catalog-import.js';

const example = JSON.parse(await readFile(new URL('./fixtures/catalog-import-demo.json', import.meta.url), 'utf8'));
test('import validates a complete bounded snapshot without silently dropping or trimming data', () => {
  const valid = validateSnapshot(example);
  assert.equal(valid.categories[0].data.description, '');
  assert.equal(valid.products[0].data.price_cents, 12000);
  for (const change of [
    data => { data.products[0].minimum_stock = 5; },
    data => { delete data.products[0].watering_advice; },
    data => { data.products[0].common_name += ' '; },
    data => { data.products[0].price_cents = '12000'; },
    data => { data.products[0].id = 3; },
    data => { data.products[0].description = 'x'.repeat(2001); },
    data => { data.products[0].description = '\ud800'; },
    data => { data.source_key = 'https://example.invalid'; },
    data => { data.products = Array.from({ length: 1001 }, () => data.products[0]); },
  ]) {
    const data = structuredClone(example);
    change(data);
    assert.throws(() => validateSnapshot(data));
  }
});

test('import rejects duplicate UUIDs and incomplete category references', () => {
  const duplicate = structuredClone(example);
  duplicate.products.push(structuredClone(duplicate.products[0]));
  assert.throws(() => validateSnapshot(duplicate), { code: 'DUPLICATE_SOURCE_ID', entity: 'products', index: 1 });
  const absent = structuredClone(example);
  absent.categories = [];
  assert.throws(() => validateSnapshot(absent), { code: 'CATEGORY_NOT_IN_BATCH' });
});

test('invalid input cannot start an import transaction', async () => {
  const db = { execute: () => assert.fail('Must not access DB'), query: () => assert.fail('Must not access DB') };
  await assert.rejects(importCatalog(db, { schema_version: 1 }, { apply: true }), { code: 'INVALID_FIELDS' });
});
