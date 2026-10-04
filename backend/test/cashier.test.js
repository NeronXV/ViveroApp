import test from 'node:test';
import assert from 'node:assert/strict';
import { claimInput, paymentInput, paymentKey, cashierScope } from '../src/cashier.js';

const claim_token = 'a'.repeat(64);
test('cashier validates methods, integer cents and reference data before SQL', () => {
  const cash = { claim_token, method: 'CASH', amount_received_cents: 1000, reference: null };
  assert.equal(paymentInput(cash).amount_received_cents, 1000);
  for (const input of [{ ...cash, amount_received_cents: 1.1 }, { ...cash, reference: 'extra' }, { ...cash, method: 'OTHER' }, { ...cash, total_cents: 1 }]) assert.throws(() => paymentInput(input));
  const card = { claim_token, method: 'CARD', amount_received_cents: null, reference: null };
  assert.equal(paymentInput(card).reference, null);
  for (const reference of ['123', '1234', '4111 1111 1111 1111', 'bad\nreference']) assert.throws(() => paymentInput({ ...card, reference }));
  assert.throws(() => paymentInput({ ...card, amount_received_cents: 1000 }));
  assert.throws(() => paymentInput({ ...card, method: 'TRANSFER' }));
  assert.equal(paymentInput({ ...card, method: 'TRANSFER', reference: ' Ref demo ' }).reference, 'Ref demo');
});

test('cashier claim tokens, scoped retry keys and branch permissions are strict', () => {
  assert.equal(claimInput({ claim_token: null }, true), null);
  assert.throws(() => claimInput({ claim_token: null }));
  assert.throws(() => claimInput({ claim_token: 'short' }));
  assert.notEqual(paymentKey(claim_token, 1), paymentKey(claim_token, 2));
  const context = { access_state: 'ACTIVE', capabilities: ['OPERATE_CASHIER'], branch: { id: 2, is_active: true } };
  assert.equal(cashierScope(context), 2);
  assert.throws(() => cashierScope({ ...context, branch: null }));
  assert.throws(() => cashierScope({ ...context, capabilities: ['VIEW_ALL_SALES'] }));
});
