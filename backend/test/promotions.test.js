import test from 'node:test';
import assert from 'node:assert/strict';
import { validatePromotion } from '../src/promotions.js';

const valid = { name: 'Demo', scope: 'SELECTED_PRODUCTS', promo_type: 'PERCENTAGE', percentage_bps: 1234, product_ids: [2, 1] };
test('promotion contract uses exact integer amounts and strict product selection', () => {
  const result = validatePromotion(valid);
  assert.equal(result.data.percentage_bps, 1234);
  assert.deepEqual(result.productIds, [1, 2]);
  for (const patch of [
    { percentage_bps: 10001 }, { percentage_bps: 0 }, { percentage_bps: 12.34 },
    { percentage_bps: '1234' }, { fixed_amount_cents: 100 }, { scope: 'SALE' },
    { product_ids: [] }, { product_ids: [1, 1] }, { product_ids: ['1'] },
    { scope: 'ALL_PRODUCTS' }, { product_ids: Array.from({ length: 1001 }, (_, i) => i + 1) },
    { min_purchase_cents: -1 }, { min_purchase_cents: null }, { is_active: null }, { max_discount_cents: 0 }, { id: 1 },
  ]) assert.throws(() => validatePromotion({ ...valid, ...patch }));
  const fixed = { ...valid, promo_type: 'FIXED_AMOUNT', percentage_bps: null, fixed_amount_cents: Number.MAX_SAFE_INTEGER };
  assert.equal(validatePromotion(fixed).data.fixed_amount_cents, Number.MAX_SAFE_INTEGER);
  for (const fixed_amount_cents of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => validatePromotion({ ...fixed, fixed_amount_cents }));
});

test('promotion dates reject local zones, rollover dates and empty/reversed windows', () => {
  const starts_at = '2026-09-29T12:00:00.000Z';
  assert.equal(validatePromotion({ ...valid, starts_at }).data.starts_at, '2026-09-29 12:00:00.000000');
  assert.equal(validatePromotion({ ...valid, starts_at: '2026-09-29T12:00:00.123456Z' }).data.starts_at, '2026-09-29 12:00:00.123456');
  assert.throws(() => validatePromotion({ ...valid, starts_at, ends_at: '2026-09-29T12:00:00.000000Z' }));
  for (const starts_at of ['2026-02-30T12:00:00.000Z', '2026-09-29', '2026-09-29T12:00:00.000-06:00', '0000-01-01T00:00:00.000Z']) assert.throws(() => validatePromotion({ ...valid, starts_at }));
  for (const ends_at of [starts_at, '2026-09-28T12:00:00.000Z']) assert.throws(() => validatePromotion({ ...valid, starts_at, ends_at }));
});
