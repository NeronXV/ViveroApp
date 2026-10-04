import test from 'node:test';
import assert from 'node:assert/strict';
import { closingInput, closingKey, closingTotals } from '../src/closings.js';

test('closing requires exact nonnegative integer cents and actor-scoped key', () => {
  assert.deepEqual(closingInput({ counted_cash_cents: 500, opening_cash_cents: 0 }), { opening_cash_cents: 0, counted_cash_cents: 500 });
  for (const input of [{ opening_cash_cents: -1, counted_cash_cents: 0 }, { opening_cash_cents: 1.5, counted_cash_cents: 0 }, { opening_cash_cents: 0 }, { opening_cash_cents: 0, counted_cash_cents: 0, cash_sales_cents: 100 }, { opening_cash_cents: 0, counted_cash_cents: Number.MAX_SAFE_INTEGER + 1 }]) assert.throws(() => closingInput(input));
  assert.notEqual(closingKey('a'.repeat(64), 1), closingKey('a'.repeat(64), 2));
  assert.throws(() => closingKey('invalid', 1));
});
test('closing sums due amounts exactly, permits negative cash and rejects unsafe aggregates', () => {
  const result = closingTotals([{ method: 'CASH', amount_due_cents: '900', amount_received_cents: 1000 }, { method: 'CARD', amount_due_cents: '500' }, { method: 'TRANSFER', amount_due_cents: '600' }], [{ method: 'CASH', amount_cents: '1200' }, { method: 'CARD', amount_cents: '200' }], 100, 20);
  assert.deepEqual(result, { cash_sales_cents: 900, card_sales_cents: 500, transfer_sales_cents: 600, cash_refunds_cents: 1200, other_refunds_cents: 200, expected_cash_cents: -200, difference_cents: 220 });
  assert.throws(() => closingTotals([{ method: 'CASH', amount_due_cents: String(Number.MAX_SAFE_INTEGER) }, { method: 'CASH', amount_due_cents: '1' }], []), e => e.code === 'CLOSING_TOTAL_INVALID');
  assert.throws(() => closingTotals([], [{ method: 'CASH', amount_cents: String(Number.MAX_SAFE_INTEGER) }], 0, Number.MAX_SAFE_INTEGER));
});
