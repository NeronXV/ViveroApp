import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { createReports } from '../src/reports.js';

// Explicit invocation only: no production hosts, migrations, existing database,
// transaction updates, SQLite adapter, or external credentials are accepted.
const workspace = fileURLToPath(new URL('../../', import.meta.url));
const configPath = resolve(process.env.REPORTS_NATIVE_CONFIG ?? '');
assert.ok(configPath.startsWith(resolve(workspace, 'tmp') + sep), 'Use a private configuration under workspace/tmp');
const config = JSON.parse(await readFile(configPath, 'utf8'));
assert.ok(Number.isInteger(config.port) && config.port > 1024);
const expectedDataDir = resolve(dirname(configPath), 'mariadb-data').replaceAll('\\', '/').toLowerCase();
const fixtures = [
  ['direct without discount', 1000, 0, 1000, 1000, 1000, 1, 0],
  ['direct promotion', 800, 0, 800, 1000, 800, 1, 200],
  ['Web promotion already in header', 1000, 200, 800, 1000, 800, 1, 200],
  ['general discount only', 1000, 100, 900, 1000, 1000, 1, 100],
  ['independent general and line discounts', 800, 100, 700, 1000, 800, 1, 300],
  ['header includes line and general discounts', 1000, 300, 700, 1000, 800, 1, 300],
  ['partial overlap', 900, 200, 700, 1000, 800, 1, 300],
  ['historical missing list price', 1000, 100, 900, null, 1000, 1, 100],
  ['historical backfilled price', 800, 0, 800, 800, 800, 1, 0],
  ['imported without Web origin', 1000, 200, 800, 1000, 800, 1, 200],
  ['multiple units', 2100, 100, 2000, 1000, 700, 3, 1000],
  ['historical decimal quantity', 1000, 100, 900, 1000, 800, '1.250', 350],
  ['cancelled with historical payment', 1000, 200, 800, 1000, 800, 1, 200, 'CANCELLED'],
  ['refunded, gross report unchanged', 1000, 200, 800, 1000, 800, 1, 200, 'CANCELLED', 800],
  ['delivered with historical payment', 1000, 200, 800, 1000, 800, 1, 200, 'DELIVERED'],
  ['cancelled without payment', 1000, 200, 800, 1000, 800, 1, 0, 'CANCELLED', null, false],
  ['historical header without items', 1000, 100, 900, null, null, 1, 100],
  ['multiple item snapshots', 1500, 0, 1500, 1000, 1000, 1, 100],
];

test('MariaDB 11.4 native reports: exact discounts, immutable transactions and original API shape', async t => {
  const db = await mysql.createConnection({ host: '127.0.0.1', port: config.port, user: 'root', password: config.password,
    supportBigNumbers: true, bigNumberStrings: true, dateStrings: true });
  try {
    const [[instance]] = await db.query('SELECT VERSION() AS version, @@datadir AS datadir');
    assert.match(instance.version, /^11\.4\./);
    assert.equal(instance.datadir.replaceAll('\\', '/').replace(/\/$/, '').toLowerCase(), expectedDataDir);
    t.diagnostic(`Native server: ${instance.version}; isolated data directory verified`);
    // CREATE DATABASE deliberately fails on reuse; no existing database is altered.
    await db.query('CREATE DATABASE p1_reports_native');
    await db.query('USE p1_reports_native');
    for (const sql of [
      'CREATE TABLE branches(id INT UNSIGNED PRIMARY KEY, name VARCHAR(80)) ENGINE=InnoDB',
      `CREATE TABLE sales(id INT UNSIGNED PRIMARY KEY, branch_id INT UNSIGNED, status VARCHAR(24), web_order_id INT UNSIGNED NULL,
        subtotal_cents BIGINT, discount_cents BIGINT, total_cents BIGINT, created_at DATETIME(6), CHECK(total_cents=subtotal_cents-discount_cents)) ENGINE=InnoDB`,
      `CREATE TABLE sale_items(id INT UNSIGNED PRIMARY KEY, sale_id INT UNSIGNED, product_id INT UNSIGNED, product_name VARCHAR(80), internal_code VARCHAR(40),
        list_price_cents BIGINT NULL, unit_price_cents BIGINT, quantity DECIMAL(14,3), line_total_cents BIGINT) ENGINE=InnoDB`,
      `CREATE TABLE cashier_payments(id INT UNSIGNED PRIMARY KEY, sale_id INT UNSIGNED UNIQUE, amount_due_cents BIGINT,
        amount_received_cents BIGINT, change_cents BIGINT, created_at DATETIME(6)) ENGINE=InnoDB`,
      'CREATE TABLE sale_refunds(id INT UNSIGNED PRIMARY KEY, sale_id INT UNSIGNED, amount_cents BIGINT) ENGINE=InnoDB',
    ]) await db.query(sql);
    await db.query("INSERT INTO branches VALUES(1,'Sucursal sintética'),(2,'Otra sucursal sintética')");
    const minute = index => `2026-10-12 12:${String(index).padStart(2, '0')}:00`;
    for (const [index, [, subtotal, discount, total, list, unit, quantity, , status, refund, paid]] of fixtures.entries()) {
      const id = index + 1;
      await db.execute('INSERT INTO sales VALUES(?,?,?,?,?,?,?,?)', [id, 1, status ?? 'PAID', index === 2 || index === 5 ? id : null,
        subtotal, discount, total, '2020-01-01 12:00:00']);
      if (unit !== null) await db.execute('INSERT INTO sale_items VALUES(?,?,?,?,?,?,?,?,?)',
        [id, id, 1, 'Nombre histórico', 'TEST', list, unit, quantity, Number(unit) * Number(quantity)]);
      if (paid !== false) await db.execute('INSERT INTO cashier_payments VALUES(?,?,?,?,?,?)', [id, id, total, total + 100, 100, minute(index)]);
      if (refund) await db.execute('INSERT INTO sale_refunds VALUES(?,?,?)', [id, id, refund]);
    }
    await db.execute('INSERT INTO sale_items VALUES(100,18,2,?,?,300,250,2,500)', ['Otro snapshot', 'TEST-2']);
    // Other branch and JSON-boundary fixtures are outside the main aggregation.
    await db.query("INSERT INTO sales VALUES(101,2,'PAID',NULL,1000,200,800,'2020-01-01'),(102,1,'PAID',NULL,9007199254740991,1,9007199254740990,'2020-01-01'),(103,1,'PAID',NULL,9007199254740991,1,9007199254740990,'2020-01-01'),(104,1,'PAID',NULL,400,0,400,'2020-01-01')");
    await db.query("INSERT INTO cashier_payments VALUES(101,101,800,800,0,'2026-10-12 12:00:00'),(102,102,9007199254740990,9007199254740990,0,'2026-10-14 12:00:00'),(103,103,9007199254740990,9007199254740990,0,'2026-10-14 13:00:00'),(104,104,400,400,0,'2026-10-15 12:00:00')");
    await db.query("INSERT INTO sale_items VALUES(104,104,1,'Decimal','TEST',1001,800,0.500,400)");
    const snapshot = async () => Promise.all(['sales', 'sale_items', 'cashier_payments', 'sale_refunds'].map(async table => (await db.query(`SELECT * FROM ${table} ORDER BY id`))[0]));
    const before = await snapshot();
    await db.query('SET TRANSACTION READ ONLY');
    await db.beginTransaction();
    const context = { access_state: 'ACTIVE', capabilities: ['VIEW_REPORTS'], branch: { id: 1 } };
    let statement, parameters;
    const reports = createReports({ execute: async (sql, params) => { statement = sql; parameters = params; return db.execute(sql, params); } }, context);
    const query = { branch_id: 1, start: '2026-10-12 00:00:00', end: '2026-10-13 00:00:00', limit: 200 };
    for (const [index, [name, , , total, , , , expected, , , paid]] of fixtures.entries()) {
      await t.test(name, async () => {
        const result = await reports.daily({ ...query, start: minute(index), end: minute(index + 1) });
        if (paid === false) assert.deepEqual(result.items, []);
        else {
          assert.equal(result.items.length, 1);
          assert.equal(result.items[0].discount_cents, expected);
          assert.equal(result.items[0].revenue_cents, total);
          assert.equal(result.items[0].sales_count, 1);
        }
      });
    }
    await t.test('daily aggregation, branch/date scope and unchanged response contract', async () => {
      const result = await reports.daily(query);
      assert.deepEqual(Object.keys(result).sort(), ['branch_id', 'end_exclusive', 'has_more', 'items', 'schema_version', 'start', 'timezone']);
      assert.deepEqual(result.items.map(row => [row.sales_count, row.revenue_cents, row.discount_cents]), [[17, 15800, 3850]]);
      assert.equal(result.timezone, 'UTC');
      assert.equal(result.schema_version, 1);
      const originalSql = statement.replace('s.discount_cents + GREATEST(COALESCE(d.list_subtotal,s.subtotal_cents)-s.subtotal_cents,0)', 's.discount_cents + COALESCE(d.item_discount,0)')
        .replace('SUM(COALESCE(list_price_cents,unit_price_cents)*quantity) AS list_subtotal', 'SUM((COALESCE(list_price_cents,unit_price_cents)-unit_price_cents)*quantity) AS item_discount');
      const [[original]] = await db.execute(originalSql, parameters);
      assert.equal(Number(original.revenue_cents), 15800);
      assert.equal(Number(original.discount_cents), 5150);
      t.diagnostic('Daily SQL: 17 payments; revenue 15800 unchanged; old discount 5150; corrected discount 3850 cents');
      await assert.rejects(reports.daily({ ...query, branch_id: 2 }), { code: 'REPORT_BRANCH_FORBIDDEN' });
      assert.deepEqual((await reports.daily({ ...query, start: query.end, end: '2026-10-14 00:00:00' })).items, []);
    });
    await t.test('native DECIMAL and JSON integer boundaries', async () => {
      const precise = await reports.daily({ ...query, start: '2026-10-14 12:00:00', end: '2026-10-14 13:00:00' });
      assert.equal(precise.items[0].revenue_cents, 9007199254740990);
      assert.equal(precise.items[0].discount_cents, 1);
      await assert.rejects(reports.daily({ ...query, start: '2026-10-14 00:00:00', end: '2026-10-15 00:00:00' }), { code: 'REPORT_TOTAL_INVALID' });
      await assert.rejects(reports.daily({ ...query, start: '2026-10-15 00:00:00', end: '2026-10-16 00:00:00' }), { code: 'REPORT_TOTAL_INVALID' });
    });
    assert.deepEqual(await snapshot(), before, 'all native transaction rows must remain identical');
    await db.rollback();
    t.diagnostic('Read-only native transaction passed; sales, items, payments and refunds unchanged');
  } finally { await db.end(); }
});
