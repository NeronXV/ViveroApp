import test from 'node:test';
import assert from 'node:assert/strict';
import { checkoutScope } from '../src/web-order-checkout.js';

test('sending orders to cashier requires operational capability and own active branch', () => {
  const context = { access_state: 'ACTIVE', capabilities: ['OPERATE_CASHIER'], branch: { id: 2, is_active: true } };
  assert.equal(checkoutScope(context), 2);
  assert.equal(checkoutScope({ ...context, capabilities: ['VIEW_ALL_SALES'] }), 2);
  assert.throws(() => checkoutScope({ ...context, capabilities: ['CREATE_SALES'] }));
  assert.throws(() => checkoutScope({ ...context, branch: null }));
  assert.throws(() => checkoutScope({ ...context, branch: { id: 2, is_active: false } }));
});
