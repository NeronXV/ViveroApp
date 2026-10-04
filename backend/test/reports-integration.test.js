import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Reports require isolated local Compose');
test('reports HTTP/SQL: payment day, exact discounts, snapshots, paid-only ranking and branch authorization', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), branches = [], users = [], sales = [];
  let product;
  try {
    for (let i = 0; i < 2; i++) { const [b] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`REP-${suffix}-${i}`, 'Demo reportes']); branches.push(b.insertId); }
    async function user(role) {
      const [u] = await db.execute('INSERT INTO users(email,full_name,role_id,branch_id,is_active) VALUES(?,?,(SELECT id FROM roles WHERE name=?),?,1)', [`rep-${suffix}-${users.length}@example.invalid`, 'Demo', role, branches[0]]); users.push(u.insertId);
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [u.insertId, sessionDigest(token)]); return token;
    }
    const owner = await user('OWNER'), manager = await user('MANAGER'), seller = await user('SALES');
    const [p] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?,?,1,999)', [`REP-${suffix}`, 'Nombre actual']); product = p.insertId;
    async function sale(branch, status, day, payDay, amount = 180) {
      const [s] = await db.execute('INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,discount_cents,total_cents,created_at) VALUES(?,?,?,?,200,20,180,?)', [`REP-${suffix}-${sales.length}`, branch, users[0], status, day]); sales.push(s.insertId);
      await db.execute('INSERT INTO sale_items(sale_id,product_id,product_name,internal_code,quantity,unit_price_cents,list_price_cents,line_total_cents) VALUES(?,?,?,?,2,100,150,200)', [s.insertId, product, 'Nombre histórico', 'COD-HIST']);
      if (payDay) await db.execute("INSERT INTO cashier_payments(sale_id,cashier_id,idempotency_key,method,amount_due_cents,amount_received_cents,change_cents,created_at,branch_id) VALUES(?,?,?,'CASH',?,?,0,?,?)", [s.insertId, users[0], `report-${suffix}-${s.insertId}`, amount, amount, payDay, branch]);
    }
    await sale(branches[0], 'PAID', '2026-09-01 12:00:00', '2026-09-30 23:59:59');
    await sale(branches[0], 'CANCELLED', '2026-09-01 12:00:00', '2026-09-30 13:00:00'); // Historical payment still counted.
    await sale(branches[0], 'DRAFT', '2026-09-01 12:00:00', null);
    await sale(branches[0], 'PAID', '2026-09-01 12:00:00', '2026-10-01 00:00:00');
    await sale(branches[1], 'PAID', '2026-09-01 12:00:00', '2026-09-30 12:00:00');
    async function call(kind, query = '', token = owner) {
      const r = await fetch(process.env.API_URL + `/api/v1/reports/${kind}?${query}`, { headers: { Authorization: `Bearer ${token}` } }); return { status: r.status, data: await r.json() };
    }
    const range = 'start_date=2026-09-30&end_date=2026-09-30';
    assert.equal((await call('daily-sales', range, seller)).status, 403);
    const daily = await call('daily-sales', range, manager); assert.equal(daily.status, 200);
    assert.deepEqual(daily.data.items.map(r => [r.day,r.sales_count,r.revenue_cents,r.discount_cents]), [['2026-09-30',2,360,240]]);
    assert.equal((await call('daily-sales', range + `&branch_id=${branches[1]}`, manager)).status, 403);
    assert.equal((await call('daily-sales', range + `&branch_id=${branches[1]}`)).data.items[0].revenue_cents, 180);
    const top = await call('top-products', `branch_id=${branches[0]}`);
    assert.deepEqual(top.data.items.map(r => [r.product_name,r.product_code,r.total_quantity,r.total_revenue_cents]), [['Nombre histórico','COD-HIST','4.000',400]]);
    assert.deepEqual((await call('top-products', range + `&branch_id=${branches[0]}`)).data.items, []); // Ranking uses sale creation date.
    assert.equal((await call('daily-sales', 'start_date=2026-02-30')).status, 400);
    assert.equal((await call('top-products', 'limit=101')).status, 400);
    assert.deepEqual((await call('daily-sales', `start_date=2025-01-01&end_date=2025-01-01&branch_id=${branches[0]}`)).data.items, []);
    // Totals beyond JSON safe integers fail explicitly rather than lose cents.
    for (const id of sales.slice(0, 2)) await db.execute('UPDATE cashier_payments SET amount_due_cents=?,amount_received_cents=? WHERE sale_id=?', ['9007199254740991','9007199254740991',id]);
    assert.equal((await call('daily-sales', range, manager)).status, 409);
  } finally {
    for (const id of sales) { await db.execute('DELETE FROM cashier_payments WHERE sale_id=?', [id]); await db.execute('DELETE FROM sale_items WHERE sale_id=?', [id]); await db.execute('DELETE FROM sales WHERE id=?', [id]); }
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id=?', [id]); await db.execute('DELETE FROM users WHERE id=?', [id]); }
    if (product) await db.execute('DELETE FROM products WHERE id=?', [product]);
    for (const id of branches) await db.execute('DELETE FROM branches WHERE id=?', [id]);
    await db.end();
  }
});
