import test from 'node:test';
import assert from 'node:assert/strict';
import { draftInput, supplierInput, presentationInput, resolutionInput, purchaseKey, purchaseQuery } from '../src/purchases.js';
import { receptionInput } from '../src/purchase-reception.js';

const line = { line_number: 1, raw_description: 'Demo planta', container_code: 'm1', suggested_common_name: null, suggested_presentation: null, quantity: 2, unit_cost_cents: 101 };
const draft = { supplier_id: 1, document_date: '2026-09-30', external_reference: null, payment_terms: 'CREDIT', expected_total_cents: 202, source_file_name: null, items: [line] };
test('purchase drafts validate exact cents, dates, unique lines and canonical replay order', () => {
  assert.equal(draftInput(draft).items[0].container_code, 'M1');
  assert.equal(draftInput({ ...draft, expected_total_cents: 0, items: [{ ...line, quantity: 0 }] }).items[0].quantity, 0);
  for (const patch of [{ document_date: '2026-02-30' }, { expected_total_cents: 201 }, { items: [line, line] }, { items: [{ ...line, quantity: 1.5 }] }, { items: [{ ...line, unit_cost_cents: Number.MAX_SAFE_INTEGER }] }, { currency: 'USD' }]) assert.throws(() => draftInput({ ...draft, ...patch }));
  const input = { ...draft, expected_total_cents: 404, items: [line, { ...line, line_number: 2 }] };
  assert.deepEqual(draftInput(input), draftInput({ ...input, items: [...input.items].reverse() }));
});
test('supplier, presentations, resolution and keys reject ambiguous input', () => {
  assert.equal(supplierInput({ code: ' demo-01 ', name: 'Demo', is_active: true }).code, 'DEMO-01');
  const presentation = { code: 'm1', display_name: null, nominal_size: '1.25', size_unit: 'l', notes: null };
  assert.equal(presentationInput(presentation).nominal_size, '1.25');
  for (const patch of [{ nominal_size: 1.25 }, { size_unit: null }, { nominal_size: '0' }]) assert.throws(() => presentationInput({ ...presentation, ...patch }));
  assert.throws(() => resolutionInput({ resolution_status: 'IGNORED', product_id: 1, suggested_common_name: null, suggested_presentation: null }));
  assert.equal(resolutionInput({ resolution_status: 'MATCHED', product_id: 1, suggested_common_name: null, suggested_presentation: null, retail_price_cents: 8500 }).retail_price_cents, 8500);
  assert.throws(() => resolutionInput({ resolution_status: 'MATCHED', product_id: 1, suggested_common_name: null, suggested_presentation: null, retail_price_cents: -50 }));
  assert.throws(() => purchaseKey('short', 'draft'));
  assert.notDeepEqual(purchaseKey('demo-request-key-001', 'draft'), purchaseKey('demo-request-key-001', 'confirmation'));
  for (const q of ['limit=101', 'status=X', 'branch_id=1', 'limit=1&limit=2']) assert.throws(() => purchaseQuery(new URLSearchParams(q)));
});
test('a reviewed line with zero received is ignored without a product resolution', () => {
  const ignored = { id: 1, quantity: 0, product_id: null, new_product: null };
  assert.deepEqual(receptionInput({ revision: 1, items: [ignored] }).items, [ignored]);
  assert.throws(() => receptionInput({ revision: 1, items: [{ ...ignored, new_product: { name: 'Ficus', presentation: 'M6', category_id: 1 } }] }));
  assert.throws(() => receptionInput({ revision: 1, items: [{ ...ignored, product_id: 1 }] }));
  assert.deepEqual(receptionInput({ revision: 1, items: [{ ...ignored, quantity: 2, new_product: { name: 'Ficus', presentation: null, category_id: null } }] }).items[0].new_product,
    { name: 'Ficus', presentation: null, category_id: null });
});
