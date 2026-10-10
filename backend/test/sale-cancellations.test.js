import test from 'node:test';
import assert from 'node:assert/strict';
import { cancellationInput } from '../src/sale-cancellations.js';
test('cancellation requires a bounded, well-formed reason and no extra fields', () => {
  assert.deepEqual(cancellationInput({ reason: '  Cliente cambió de opinión  ' }), { reason: 'Cliente cambió de opinión' });
  for (const value of [null, {}, [], { reason: '' }, { reason: 'ab' }, { reason: 'x'.repeat(241) }, { reason: 'mal\ntexto' }, { reason: '\ud800ab' }, { reason: 'correcto', user: 1 }]) {
    assert.throws(() => cancellationInput(value), error => error.status === 400 && error.code === 'CANCELLATION_REASON_INVALID');
  }
});
