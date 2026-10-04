import test from 'node:test';
import assert from 'node:assert/strict';
import { reportQuery, reportScope, reportInteger } from '../src/reports.js';

test('reports bound UTC ranges, inclusive dates and safe exact aggregates', () => {
  const query = reportQuery(new URLSearchParams('start_date=2026-09-01&end_date=2026-09-30'));
  assert.equal(query.end, '2026-10-01 00:00:00');
  for (const q of ['start_date=2026-02-30', 'start_date=2027-01-01&end_date=2026-01-01', 'start_date=2020-01-01&end_date=2026-01-01', 'limit=501', 'limit=1&limit=2', 'unknown=1']) assert.throws(() => reportQuery(new URLSearchParams(q)));
  assert.equal(reportQuery(new URLSearchParams(), true).start, null);
  assert.equal(reportInteger('9007199254740991.000'), Number.MAX_SAFE_INTEGER);
  for (const n of ['9007199254740992', '1.500', '-1']) assert.throws(() => reportInteger(n));
});
test('reports restrict branch without VIEW_ALL_SALES and never bypass VIEW_REPORTS', () => {
  const context = { access_state: 'ACTIVE', capabilities: ['VIEW_REPORTS'], branch: { id: 2 } };
  assert.equal(reportScope(context, null), 2);
  assert.throws(() => reportScope(context, 1));
  assert.equal(reportScope({ ...context, capabilities: ['VIEW_REPORTS', 'VIEW_ALL_SALES'] }, null), null);
  assert.throws(() => reportScope({ ...context, capabilities: ['VIEW_ALL_SALES'] }, null));
});
