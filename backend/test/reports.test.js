import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { createReports, reportQuery, reportScope, reportInteger } from '../src/reports.js';

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

// Execute the production SELECT in an isolated, in-memory SQL engine. These
// fixtures do not connect to MariaDB or substitute a JS discount calculation.
// Only MariaDB's date formatting and GREATEST need SQLite compatibility functions.
const discountCases = [
  { name: 'direct without discount', subtotal: 1000, discount: 0, total: 1000, list: 1000, unit: 1000, expected: 0 },
  { name: 'direct line promotion', subtotal: 800, discount: 0, total: 800, list: 1000, unit: 800, expected: 200 },
  { name: 'Web line promotion already in header', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, web: 1, expected: 200 },
  { name: 'general discount only', subtotal: 1000, discount: 100, total: 900, list: 1000, unit: 1000, expected: 100 },
  { name: 'independent general and line discounts', subtotal: 800, discount: 100, total: 700, list: 1000, unit: 800, expected: 300 },
  { name: 'header includes line and independent general discount', subtotal: 1000, discount: 300, total: 700, list: 1000, unit: 800, web: 1, expected: 300 },
  { name: 'partial overlap between header and lines', subtotal: 900, discount: 200, total: 700, list: 1000, unit: 800, expected: 300 },
  { name: 'legacy missing list price', subtotal: 1000, discount: 100, total: 900, list: null, unit: 1000, expected: 100 },
  { name: 'legacy backfilled list price', subtotal: 800, discount: 0, total: 800, list: 800, unit: 800, expected: 0 },
  { name: 'imported without origin marker', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, expected: 200 },
  { name: 'multiple discounted units', subtotal: 2100, discount: 100, total: 2000, list: 1000, unit: 700, quantity: 3, expected: 1000 },
  { name: 'historical fractional quantity with exact cents', subtotal: 1000, discount: 100, total: 900, list: 1000, unit: 800, quantity: '1.250', expected: 350 },
  { name: 'historical payment on cancelled sale', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, status: 'CANCELLED', expected: 200 },
  { name: 'refunded sale retains gross payment report', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, status: 'CANCELLED', refund: 800, expected: 200 },
  { name: 'delivered sale retains gross payment report', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, status: 'DELIVERED', expected: 200 },
  { name: 'cancelled without payment excluded', subtotal: 1000, discount: 200, total: 800, list: 1000, unit: 800, status: 'CANCELLED', paid: false, expected: 0 },
  { name: 'historical header without item snapshots', subtotal: 1000, discount: 100, total: 900, noItems: true, expected: 100 },
];
const reportContext = { access_state: 'ACTIVE', capabilities: ['VIEW_REPORTS'], branch: { id: 1 } };
const discountQuery = { branch_id: 1, start: '2026-10-12 00:00:00', end: '2026-10-13 00:00:00', limit: 200 };
function isolatedReports(fixtures) {
  const db = new DatabaseSync(':memory:');
  db.function('DATE_FORMAT', (value, format) => { assert.equal(format, '%Y-%m-%d'); return value.slice(0, 10); });
  db.function('GREATEST', { useBigIntArguments: true }, (left, right) => left > right ? left : right);
  db.exec(`CREATE TABLE branches(id INTEGER PRIMARY KEY, name TEXT);
    CREATE TABLE sales(id INTEGER PRIMARY KEY, branch_id INTEGER, status TEXT, web_order_id INTEGER,
      subtotal_cents INTEGER, discount_cents INTEGER, total_cents INTEGER, created_at TEXT,
      CHECK(total_cents = subtotal_cents - discount_cents));
    CREATE TABLE sale_items(id INTEGER PRIMARY KEY, sale_id INTEGER, product_id INTEGER, product_name TEXT,
      internal_code TEXT, list_price_cents INTEGER, unit_price_cents INTEGER, quantity DECIMAL(14,3), line_total_cents INTEGER);
    CREATE TABLE cashier_payments(id INTEGER PRIMARY KEY, sale_id INTEGER UNIQUE, amount_due_cents INTEGER,
      amount_received_cents INTEGER, change_cents INTEGER, created_at TEXT);
    CREATE TABLE sale_refunds(id INTEGER PRIMARY KEY, sale_id INTEGER, amount_cents INTEGER);
    INSERT INTO branches VALUES(1,'Sucursal sintética'),(2,'Otra sucursal sintética');`);
  for (const [index, row] of fixtures.entries()) {
    const id = index + 1, quantity = row.quantity ?? 1, branch = row.branch ?? 1;
    db.prepare('INSERT INTO sales VALUES(?,?,?,?,?,?,?,?)').run(id, branch, row.status ?? 'PAID', row.web ?? null,
      row.subtotal, row.discount, row.total, '2020-01-01 12:00:00');
    if (!row.noItems) db.prepare('INSERT INTO sale_items VALUES(?,?,?,?,?,?,?,?,?)').run(id, id, 1, 'Nombre histórico', 'TEST',
      row.list, row.unit, quantity, row.lineTotal ?? Number(row.unit) * Number(quantity));
    if (row.paid !== false) db.prepare('INSERT INTO cashier_payments VALUES(?,?,?,?,?,?)').run(id, id, row.total, row.total, 0, '2026-10-12 12:00:00');
    if (row.refund) db.prepare('INSERT INTO sale_refunds VALUES(?,?,?)').run(id, id, row.refund);
  }
  const read = (sql, params = []) => {
    const statement = db.prepare(sql);
    statement.setReadBigInts(true);
    return statement.all(...params).map(row => ({ ...row, ...(row.branch_id === undefined ? {} : { branch_id: Number(row.branch_id) }) }));
  };
  const snapshot = () => ['sales', 'sale_items', 'cashier_payments', 'sale_refunds'].map(table => read(`SELECT * FROM ${table} ORDER BY id`));
  // Enforce report-only access after fixture setup; any write fails the test.
  db.exec('PRAGMA query_only = ON');
  const reports = createReports({ execute: async (sql, params) => [read(sql, params)] }, reportContext);
  return { db, reports, snapshot };
}
for (const fixture of discountCases) {
  test(`reports SQL: ${fixture.name}`, async () => {
    const { db, reports, snapshot } = isolatedReports([fixture]);
    try {
      const before = snapshot(), result = await reports.daily(discountQuery);
      if (fixture.paid === false) assert.deepEqual(result.items, []);
      else {
        assert.equal(result.items.length, 1);
        assert.equal(result.items[0].discount_cents, fixture.expected);
        assert.equal(result.items[0].revenue_cents, fixture.total);
        assert.equal(result.items[0].sales_count, 1);
      }
      assert.deepEqual(snapshot(), before, 'reports must not alter transaction snapshots');
    } finally { db.close(); }
  });
}
test('reports SQL: several sales and item snapshots aggregate once per payment and preserve branch/date filters', async () => {
  const other = { ...discountCases[2], branch: 2 };
  const { db, reports, snapshot } = isolatedReports([...discountCases, other]);
  try {
    // Another item on the direct sale: 2 units, list 300, effective 250.
    db.exec('PRAGMA query_only = OFF');
    db.prepare('INSERT INTO sale_items VALUES(?,?,?,?,?,?,?,?,?)').run(100, 1, 2, 'Otro snapshot', 'TEST-2', 300, 250, 2, 500);
    db.prepare('UPDATE sales SET subtotal_cents=1500,total_cents=1500 WHERE id=1').run();
    db.prepare('UPDATE cashier_payments SET amount_due_cents=1500,amount_received_cents=1500 WHERE sale_id=1').run();
    db.exec('PRAGMA query_only = ON');
    const before = snapshot(), result = await reports.daily(discountQuery);
    assert.equal(result.items.length, 1);
    assert.equal(result.items[0].sales_count, discountCases.filter(row => row.paid !== false).length);
    assert.equal(result.items[0].discount_cents, discountCases.reduce((sum, row) => sum + row.expected, 100));
    assert.equal(result.items[0].revenue_cents, discountCases.filter(row => row.paid !== false).reduce((sum, row) => sum + row.total, 500));
    assert.deepEqual((await reports.daily({ ...discountQuery, start: discountQuery.end, end: '2026-10-14 00:00:00' })).items, []);
    await assert.rejects(reports.daily({ ...discountQuery, branch_id: 2 }), { code: 'REPORT_BRANCH_FORBIDDEN' });
    assert.deepEqual(snapshot(), before);
  } finally { db.close(); }
});
test('reports SQL: integer cent precision and explicit aggregate overflow rejection', async () => {
  const large = { subtotal: 9007199254740991n, discount: 1n, total: 9007199254740990n, noItems: true };
  const { db, reports, snapshot } = isolatedReports([large]);
  try {
    const before = snapshot(), result = await reports.daily(discountQuery);
    assert.equal(result.items[0].revenue_cents, 9007199254740990);
    assert.equal(result.items[0].discount_cents, 1);
    assert.deepEqual(snapshot(), before);
  } finally { db.close(); }
  const overflow = isolatedReports([large, large]);
  try { await assert.rejects(overflow.reports.daily(discountQuery), { code: 'REPORT_TOTAL_INVALID' }); }
  finally { overflow.db.close(); }
});
test('reports SQL: fractional discounts fail instead of rounding stored amounts', async () => {
  const { db, reports, snapshot } = isolatedReports([{ subtotal: 400, discount: 0, total: 400, list: 1001, unit: 800, quantity: '0.500' }]);
  try {
    const before = snapshot();
    await assert.rejects(reports.daily(discountQuery), { code: 'REPORT_TOTAL_INVALID' });
    assert.deepEqual(snapshot(), before);
  } finally { db.close(); }
});
