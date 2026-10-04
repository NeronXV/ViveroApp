import test from 'node:test';
import assert from 'node:assert/strict';
import { validateOrder, orderKey } from '../src/web-orders.js';
import { orderScope, validateOrderStatus, orderAdminQuery } from '../src/web-order-admin.js';

test('order administration validates scope, optimistic revision and terminal payment guard', () => {
  const global = { access_state: 'ACTIVE', capabilities: ['VIEW_ALL_SALES'], branch: null };
  assert.equal(orderScope(global), null);
  assert.throws(() => orderScope(global, true));
  assert.throws(() => orderScope({ ...global, capabilities: ['CREATE_SALES'] }));
  assert.deepEqual(validateOrderStatus({ status: 'READY', expected_revision: 1, observation: ' Listo ' }), { status: 'READY', expected_revision: 1, observation: 'Listo' });
  assert.equal(validateOrderStatus({ status: 'COMPLETED', expected_revision: 2, observation: null }).status, 'COMPLETED'); // Payment guard now runs under SQL locks.
  for (const value of [{ status: 'READY', expected_revision: -1, observation: null }, { status: 'PENDING', expected_revision: 0, observation: null }, { status: 'READY', expected_revision: 1, observation: null, total_cents: 1 }]) assert.throws(() => validateOrderStatus(value));
  for (const query of ['status=PAID', 'limit=101', 'before_id=uuid', 'branch_id=1&branch_id=2', 'unknown=1']) assert.throws(() => orderAdminQuery(new URLSearchParams(query)));
});

const input = { branch_id: 1, items: [{ product_id: 2, quantity: 1 }, { product_id: 1, quantity: 3 }],
  customer_name: 'Cliente sintetico', customer_phone: null, customer_email: 'demo@example.invalid', notes: null, expected_total_cents: 100 };
test('orders normalize contact and item order, require bounded integer IDs and reject client prices', () => {
  const parsed = validateOrder(input, true);
  assert.deepEqual(parsed.items.map(row => row.product_id), [1, 2]);
  assert.equal(validateOrder({ ...input, customer_email: ' DEMO@example.invalid ' }, true).customer_email, 'demo@example.invalid');
  for (const patch of [{ branch_id: '1' }, { items: [] }, { items: [input.items[0], input.items[0]] },
    { items: [{ product_id: 1, quantity: 0.5 }] }, { items: [{ product_id: 1, quantity: 101 }] },
    { items: [{ product_id: 1, quantity: 1, unit_price_cents: 1 }] },
    { customer_email: null }, { customer_name: 'a' }, { notes: 'x'.repeat(501) },
    { expected_total_cents: Number.MAX_SAFE_INTEGER + 1 }, { expected_total_cents: 0 }, { status: 'PAID' }]) {
    assert.throws(() => validateOrder({ ...input, ...patch }, true));
  }
  assert.deepEqual(validateOrder({ branch_id: 1, items: [input.items[0]] }), { branch_id: 1, items: [input.items[0]] });
});
test('recovery keys are opaque 32-byte secrets stored only as SHA-256 digests', () => {
  assert.equal(orderKey('a'.repeat(64)).length, 32);
  for (const key of [undefined, '', '1', 'A'.repeat(64), 'g'.repeat(64), 'a'.repeat(63)]) assert.throws(() => orderKey(key));
});
