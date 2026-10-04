import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSale, saleKey, saleScope, saleQuery } from '../src/sales.js';

test('sales accept only products, integer quantities and expected total; never client prices', () => {
  const input = { items: [{ product_id: 2, quantity: 100000 }, { product_id: 1, quantity: 1 }], expected_total_cents: 500 };
  assert.equal(validateSale(input).items[0].product_id, 1);
  for (const value of [{ ...input, total_cents: 1 }, { ...input, expected_total_cents: 1.1 }, { ...input, items: [] },
    { ...input, items: [{ product_id: 1, quantity: 0.5 }] }, { ...input, items: [{ product_id: 1, quantity: 100001 }] },
    { ...input, items: [{ product_id: 1, quantity: 1, unit_price_cents: 0 }] },
    { ...input, items: [{ product_id: 1, quantity: 1 }, { product_id: 1, quantity: 2 }] }]) assert.throws(() => validateSale(value));
});

test('sale keys belong to an actor and branch access has no administrator bypass', () => {
  const key = 'a'.repeat(64);
  assert.notDeepEqual(saleKey(key, 1), saleKey(key, 2));
  assert.throws(() => saleKey('short', 1));
  const context = { access_state: 'ACTIVE', capabilities: ['CREATE_SALES'], branch: { id: 1, is_active: true } };
  assert.equal(saleScope(context), 1);
  assert.throws(() => saleScope({ ...context, branch: null }));
  assert.throws(() => saleScope({ ...context, capabilities: ['VIEW_ALL_SALES'] }));
  assert.throws(() => saleQuery(new URLSearchParams('limit=101')));
  assert.throws(() => saleQuery(new URLSearchParams('branch_id=2')));
});
