import test from 'node:test';
import assert from 'node:assert/strict';
import { refundInput, refundScope } from '../src/refunds.js';

test('refund requires both capabilities and own active branch', () => {
  const context = { access_state: 'ACTIVE', capabilities: ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'], branch: { id: 2, is_active: true } };
  assert.equal(refundScope(context), 2);
  for (const override of [{ capabilities: ['OPERATE_CASHIER'] }, { capabilities: ['VIEW_ALL_SALES'] }, { branch: null }, { access_state: 'INACTIVE' }]) assert.throws(() => refundScope({ ...context, ...override }));
});
test('refund requires explicit money returned and full server-calculated amount', () => {
  const input = { reason: '  Devolucion demo  ', method: 'CASH', restock: true, money_returned: true };
  assert.equal(refundInput(input).reason, 'Devolucion demo');
  assert.deepEqual(refundInput({ money_returned: true, restock: true, method: 'CASH', reason: input.reason }), refundInput(input));
  for (const change of [{ money_returned: false }, { restock: null }, { amount_cents: 1 }, { reason: 'corto\n' }, { reason: 'abc' }, { method: 'OTHER' }]) assert.throws(() => refundInput({ ...input, ...change }));
});

test('refund folio lookup accepts one exact bounded folio only', async () => {
  const { refundFolio } = await import('../src/refunds.js');
  assert.equal(refundFolio(new URLSearchParams({ folio: ' VD-DEMO ' })), 'VD-DEMO');
  for (const query of ['', 'folio=', 'folio=one&folio=two', 'folio=one&extra=1', 'folio=' + 'a'.repeat(41), 'folio=bad%0Avalue']) assert.throws(() => refundFolio(new URLSearchParams(query)));
});
