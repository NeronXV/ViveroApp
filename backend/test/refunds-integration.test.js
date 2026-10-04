import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Refund tests require isolated local Compose');
test('refund SQL/HTTP: scope, exact payment, concurrency, recovery, delivery guard and rollback', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], sales = [];
  const key = () => randomBytes(32).toString('hex');
  let branch, other, order, trigger = false;
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`REF-${suffix}`, 'Demo devoluciones']);
    [other] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`REF2-${suffix}`, 'Otra demo']);
    async function user(role, branchId = branch.insertId) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`ref-${suffix}-${users.length}@example.invalid`, 'Demo', role, branchId]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const manager = await user('MANAGER'), cashier = await user('CASHIER'), elsewhere = await user('MANAGER', other.insertId);
    for (let i = 0; i < 3; i++) {
      const [row] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, ?, 900, 900)", [`REF-${suffix}-${i}`, branch.insertId, users[0], i === 2 ? 'SENT_TO_CASHIER' : 'PAID']);
      sales.push(row.insertId);
      if (i < 2) await db.execute("INSERT INTO cashier_payments (sale_id, cashier_id, branch_id, idempotency_key, method, amount_due_cents, amount_received_cents, change_cents) VALUES (?, ?, ?, ?, 'CASH', 900, 1000, 100)", [row.insertId, users[0], branch.insertId, key()]);
    }
    async function request(id, body, token = manager, retry = key()) {
      const r = await fetch(`${process.env.API_URL}/api/v1/cashier/sales/${id}/refunds`, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': retry }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const input = { reason: 'Devolucion sintetica', method: 'CASH', restock: true, money_returned: true };
    [order] = await db.execute(`INSERT INTO web_orders (branch_id, idempotency_hash, request_hash, customer_name, customer_email, status, subtotal_cents, discount_cents, total_cents)
      VALUES (?, ?, ?, 'Demo pedido', 'demo@example.invalid', 'READY', 900, 0, 900)`, [branch.insertId, randomBytes(32), randomBytes(32)]);
    await db.execute('UPDATE sales SET web_order_id = ? WHERE id = ?', [order.insertId, sales[0]]);
    assert.equal((await request(sales[0], undefined, cashier)).status, 403);
    assert.equal((await request(sales[0], input, elsewhere)).status, 404);
    assert.equal((await request(sales[2], input)).status, 409);
    assert.equal((await request(sales[0], { ...input, money_returned: false })).status, 400);
    assert.equal((await request(sales[0])).data.amount_cents, 900);
    async function lookup(folio, token = manager) {
      const response = await fetch(`${process.env.API_URL}/api/v1/cashier/refunds/lookup?${new URLSearchParams({ folio })}`, { headers: { Authorization: `Bearer ${token}` } });
      return { status: response.status, data: await response.json() };
    }
    const folio = `REF-${suffix}-0`;
    assert.equal((await lookup(folio)).data.sale_id, sales[0]);
    assert.equal((await lookup(folio.toLowerCase())).status, 404);
    assert.equal((await lookup(folio, elsewhere)).status, 404);
    assert.equal((await lookup(folio, cashier)).status, 403);
    assert.equal((await lookup(`REF-${suffix}-2`)).status, 409);
    const retry = key();
    const race = await Promise.all([request(sales[0], input, manager, retry), request(sales[0], input, manager, retry)]);
    assert.deepEqual(race.map(x => x.status).sort(), [200, 201]);
    assert.equal(race[0].data.refund.id, race[1].data.refund.id);
    assert.equal(race[0].data.refund.amount_cents, 900); // exclude received/change
    assert.equal((await request(sales[0])).data.already_refunded, true);
    assert.equal((await lookup(folio)).data.already_refunded, true);
    assert.equal((await request(sales[0], input)).data.error, 'REFUND_ALREADY_RECORDED');
    assert.equal((await request(sales[0], { ...input, method: 'CARD' }, manager, retry)).data.error, 'REFUND_IDEMPOTENCY_CONFLICT');
    assert.equal((await request(sales[1], input, manager, retry)).data.error, 'REFUND_IDEMPOTENCY_CONFLICT');
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM sale_refunds WHERE sale_id = ?', [sales[0]]);
    assert.equal(Number(count.n), 1);
    await assert.rejects(db.execute("UPDATE web_orders SET status = 'COMPLETED' WHERE id = ?", [order.insertId]), e => e.code === 'ER_SIGNAL_EXCEPTION');
    const completed = await fetch(`${process.env.API_URL}/api/v1/admin/web-orders/${order.insertId}`, { method: 'PATCH', headers: { Authorization: `Bearer ${manager}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'COMPLETED', expected_revision: 0, observation: null }) });
    assert.equal(completed.status, 409);
    // A late insert failure must not reserve the operation key or record a refund.
    await db.query(`CREATE TRIGGER refund_test_failure_${suffix} BEFORE INSERT ON sale_refunds FOR EACH ROW SET NEW.reason = IF(NEW.sale_id = ${sales[1]}, NULL, NEW.reason)`);
    trigger = true;
    const rollbackKey = key();
    assert.equal((await request(sales[1], input, manager, rollbackKey)).status, 503);
    assert.equal((await request(sales[1])).data.already_refunded, false);
    await db.query(`DROP TRIGGER refund_test_failure_${suffix}`); trigger = false;
    assert.equal((await request(sales[1], { ...input, method: 'TRANSFER', restock: false }, manager, rollbackKey)).status, 201);
    const [[columns]] = await db.execute('SELECT status FROM sales WHERE id = ?', [sales[0]]);
    assert.equal(columns.status, 'PAID'); // payment history is preserved
    const [[moves]] = await db.execute('SELECT COUNT(*) AS n FROM inventory_movements WHERE branch_id = ?', [branch.insertId]);
    assert.equal(Number(moves.n), 0); // no undeducted stock can be added
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try { await assert.rejects(runtime.execute('UPDATE sale_refunds SET reason = ? WHERE sale_id = ?', ['Cambiar auditoria', sales[0]]), e => e.code === 'ER_TABLEACCESS_DENIED_ERROR'); } finally { await runtime.end(); }
  } finally {
    if (trigger) await db.query(`DROP TRIGGER refund_test_failure_${suffix}`);
    for (const id of sales) { await db.execute('DELETE FROM sale_refunds WHERE sale_id = ?', [id]); await db.execute('DELETE FROM cashier_payments WHERE sale_id = ?', [id]); await db.execute('DELETE FROM sales WHERE id = ?', [id]); }
    if (order) await db.execute('DELETE FROM web_orders WHERE id = ?', [order.insertId]);
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]); await db.execute('DELETE FROM users WHERE id = ?', [id]); }
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    if (other) await db.execute('DELETE FROM branches WHERE id = ?', [other.insertId]);
    await db.end();
  }
});
