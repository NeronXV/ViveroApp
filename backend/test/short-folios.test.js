import test from 'node:test';
import assert from 'node:assert/strict';
import { shortFolioResponse } from '../src/short-folios.js';
test('short representation preserves IDs, linked orders, money, original payload and exact response shapes', async () => {
  const date = new Date('2026-10-09T12:00:00Z');
  const legacy = { schema_version: 1, sale: { id: 7, folio: 'VD-LEGACY', web_order_id: 2, total_cents: 100 }, order: { id: 2, order_number: 'VW-2' }, history: [{ created_at: date }] };
  const db = { execute: async sql => [sql.includes('sale_folio_aliases') ? [{ folio: 'VD-LEGACY', short_folio: 'VD-0007' }] : [{ order_id: 2, short_folio: 'VW-0002' }]] };
  const result = await shortFolioResponse(db, legacy);
  assert.deepEqual(result, { schema_version: 1, sale: { id: 7, folio: 'VD-0007', web_order_id: 2, total_cents: 100 }, order: { id: 2, order_number: 'VW-0002' }, history: [{ created_at: date }] });
  assert.equal(JSON.parse(JSON.stringify(result)).history[0].created_at, date.toISOString());
  assert.equal(legacy.sale.folio, 'VD-LEGACY'); assert.equal(legacy.order.order_number, 'VW-2');
  await assert.rejects(shortFolioResponse({ execute: async () => [[]] }, legacy));
});
