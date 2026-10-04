import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { validatePromotionSnapshot } from '../scripts/promotion-import.js';

const example = JSON.parse(await readFile(new URL('./fixtures/promotion-import-demo.json', import.meta.url), 'utf8'));
test('promotion imports convert decimal strings exactly and preserve source microseconds', () => {
  const result = validatePromotionSnapshot(example).rows[0];
  assert.equal(result.data.percentage_bps, 1250);
  assert.equal(result.data.created_at, '2025-01-01 00:00:00.000123');
  const fixed = structuredClone(example);
  fixed.promotions[0].promo_type = 'FIXED_AMOUNT';
  fixed.promotions[0].value = '999999999999.00';
  assert.equal(validatePromotionSnapshot(fixed).rows[0].data.fixed_amount_cents, 999999999999);
  fixed.promotions[0].value = '1.01';
  assert.throws(() => validatePromotionSnapshot(fixed), { code: 'INVALID_SOURCE_VALUE' });
});

test('promotion import rejects unsafe values, sale scope, missing dates and ambiguous selections', () => {
  for (const patch of [
    { value: 12.5 }, { value: '12.345' }, { value: '100.01' }, { value: '0.00' }, { value: '1e2' },
    { scope: 'SALE' }, { created_at: null }, { created_at: '2025-02-30T00:00:00.000000Z' },
    { starts_at: '2026-01-01T00:00:00.123456Z', ends_at: '2026-01-01T00:00:00.123456Z' },
    { product_ids: [] }, { product_ids: [1] },
    { product_ids: [...example.promotions[0].product_ids, ...example.promotions[0].product_ids] },
    { name: ' Trailing ' }, { is_active: null }, { extra: 1 },
  ]) assert.throws(() => validatePromotionSnapshot({ ...example, promotions: [{ ...example.promotions[0], ...patch }] }));
  const duplicate = structuredClone(example);
  duplicate.promotions.push(structuredClone(duplicate.promotions[0]));
  assert.throws(() => validatePromotionSnapshot(duplicate), { code: 'DUPLICATE_SOURCE_ID' });
});
