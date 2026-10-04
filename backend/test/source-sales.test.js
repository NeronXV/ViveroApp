import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { reconcileSourceSales } from '../scripts/source-sales.js';
import { historyDate } from '../scripts/history-import.js';
import { sourceHistoryFixture } from './fixtures/source-history-demo.js';
const inspect = input => { const bytes=Buffer.from(JSON.stringify(input)); return reconcileSourceSales(bytes,createHash('sha256').update(bytes).digest('hex')); };
test('paid source history reconciles exact amounts without exposing folios or identity', () => {
  const report=inspect(sourceHistoryFixture());
  assert.deepEqual(report.totals,{total_cents:'400',received_cents:'500',change_cents:'100'});
  assert.equal(report.import_applied,false);
  assert.equal(JSON.stringify(report).includes('DEMO-2026-001'),false);
  assert.equal(JSON.stringify(report).includes('owner@example.invalid'),false);
});
test('history rejects totals, payment duplication, open claims, unsupported states and transitions', () => {
  for (const mutate of [
    t => {t.sale_items[0].line_total_cents='401';},
    t => {t.sales[0].total_cents='399';},
    t => {t.sale_payments[0].change_cents='99';},
    t => {t.sale_payments.push({...t.sale_payments[0],id:'00000000-0000-4000-8000-000000000099'});},
    t => {t.sale_payment_claims[0].consumed_at=null;t.sale_payment_claims[0].closed_reason=null;},
    t => {t.sales[0].status='SENT_TO_CASHIER';},
    t => {t.sale_status_history.pop();},
    t => {t.sale_items[0].quantity=1.5;},
    t => {t.sale_items[0].discount_cents='1';},
  ]) {const source=sourceHistoryFixture();mutate(source.tables);assert.throws(() => inspect(source));}
});
test('historical UTC conversion preserves microseconds and rejects local time', () => {
  assert.equal(historyDate('2026-10-01T12:00:00.123456+00:00'),'2026-10-01 12:00:00.123456');
  assert.equal(historyDate('2026-10-01T12:00:00Z'),'2026-10-01 12:00:00.000000');
  assert.throws(() => historyDate('2026-10-01T12:00:00'),/HISTORY_UTC_DATE_REQUIRED/);
  assert.throws(() => historyDate('2026-02-31T12:00:00Z'),/HISTORY_UTC_DATE_REQUIRED/);
});
