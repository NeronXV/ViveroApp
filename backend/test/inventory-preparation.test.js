import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { requireInventoryOwner, inventoryPermissions } from '../src/inventory-policy.js';
import { observationInput, decisionInput, observationQuery } from '../src/inventory-observations.js';
import { preparePlantLists } from '../scripts/plant-list-preparation.js';

const manager = { access_state: 'ACTIVE', role: { name: 'MANAGER' }, user: { id: 7 }, branch: { id: 2, is_active: true }, capabilities: ['MANAGE_INVENTORY'] };
test('owner policy also requires capability and active assigned branch; names do not grant rights', () => {
  for (const context of [manager, { ...manager, role: { name: 'ADMIN' } }, { ...manager, role: { name: 'OWNER' }, capabilities: [] },
    { ...manager, role: { name: 'OWNER' }, branch: null }, { ...manager, role: { name: 'OWNER' }, access_state: 'NO_ROLE' }]) assert.throws(() => requireInventoryOwner(context));
  assert.doesNotThrow(() => requireInventoryOwner({ ...manager, role: { name: 'OWNER' } }));
  assert.equal(inventoryPermissions(manager).can_record_count, true);
  assert.equal(inventoryPermissions(manager).can_receive, false);
});
test('count contracts reject extra scope, malformed keys, decisions and quantities', () => {
  const body = { product_id: 3, counted_quantity: '0', reason: 'Conteo inicial', baseline_quantity: '0.000', baseline_movement_id: 0 }, key = 'synthetic-key-for-count-001';
  assert.equal(observationInput(body, key).quantity, '0.000');
  for (const invalid of [{ ...body, branch_id: 7 }, { ...body, counted_quantity: '-1' }, { ...body, counted_quantity: '1.2' }]) assert.throws(() => observationInput(invalid, key));
  for (const invalid of [{ decision: 'APPLY', reason: 'Prueba' }, { decision: 'APPROVE', reason: 'ab' }, { decision: 'APPROVE', reason: 'Prueba', user_id: 5 }]) assert.throws(() => decisionInput(invalid, key));
  assert.throws(() => observationQuery(new URLSearchParams('limit=101')));
  assert.throws(() => observationQuery(new URLSearchParams('status=PAID')));
});
const input = { schema_version: 1, source_key: 'plantas_sinteticas', items: [{ entry_id: 'f-001', original_name: 'Planta sintetica', category: 'Follaje', size: 'Maceta 10 cm' }] };
test('offline lists preserve original name, stable identity and unknown commercial data', () => {
  const first = preparePlantLists(input, { products: [], categories: [] });
  const second = preparePlantLists(input, { products: [], categories: [] });
  assert.deepEqual(first.catalog, second.catalog); assert.equal(first.can_apply, true);
  assert.equal(first.catalog.products[0].common_name, input.items[0].original_name);
  assert.equal(first.catalog.products[0].price_cents, 0); assert.equal(first.catalog.products[0].is_active, false);
  assert.equal(Object.hasOwn(first.catalog.products[0], 'quantity'), false);
});
test('names, scientific names, barcodes, codes and presentations require review instead of overwriting', () => {
  const sameName = preparePlantLists(input, { categories: [], products: [{ id: 1, common_name: 'PLANTA SINTETICA', internal_code: 'OTHER' }] });
  assert.equal(sameName.can_apply, false); assert.deepEqual(sameName.report.at(-1).candidates, [1]);
  const botanical = { ...input, items: [{ ...input.items[0], scientific_name: 'Species test', barcode: '00123' }] };
  assert.equal(preparePlantLists(botanical, { categories: [], products: [{ id: 2, scientific_name: 'Species test', internal_code: 'OTHER', barcode: '00123' }] }).can_apply, false);
  assert.throws(() => preparePlantLists({ ...input, items: [...input.items, ...input.items] }, { products: [], categories: [] }));
});
test('normalization is proposed without replacing original names silently', () => {
  const spaced = { ...input, items: [{ ...input.items[0], original_name: ' Planta sintetica ' }] };
  const preview = preparePlantLists(spaced, { categories: [], products: [] });
  assert.equal(preview.can_apply, false); assert.equal(preview.report[0].original_name, ' Planta sintetica ');
  const approved = preparePlantLists({ ...input, items: [{ ...spaced.items[0], approved_name: 'Planta sintetica' }] }, { categories: [], products: [] });
  assert.equal(approved.can_apply, true); assert.equal(approved.report[0].original_name, ' Planta sintetica ');
});
test('migration is additive, keeps historic products, and never widens role permissions', async () => {
  const sql = await readFile(new URL('../../database/mysql/migrations/033_inventory_preparation.sql', import.meta.url), 'utf8');
  assert.match(sql, /DEFAULT 'LEGACY'/); assert.match(sql, /SET DEFAULT 'PENDING'/);
  assert.doesNotMatch(sql, /\b(?:DELETE|TRUNCATE|DROP)\s+(?:FROM|TABLE|DATABASE)|UPDATE\s+(?:sales|inventory|role_permissions)|INSERT\s+INTO\s+role_permissions/i);
  assert.match(sql, /decision_key BINARY\(32\) NULL UNIQUE/);
  assert.match(sql, /FOREIGN KEY \(count_id\) REFERENCES inventory_counts/);
});
