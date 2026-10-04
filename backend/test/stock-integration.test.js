import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Stock tests require isolated local Compose');
test('stock HTTP/SQL: explicit activation, historical payments, exact aggregation, rollback, replay and overselling', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], sales = [], products = [];
  let branch, category, historyTrigger = false, refundTrigger = false;
  const key = () => randomBytes(32).toString('hex');
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`STOCK-${suffix}`, 'Demo stock']);
    [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`STOCK-${suffix}`]);
    for (let i = 0; i < 2; i++) {
      const [p] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 100)', [`STOCK-${suffix}-${i}`, 'Demo planta', category.insertId]);
      products.push(p.insertId);
    }
    async function user(role) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`stock-${suffix}-${users.length}@example.invalid`, 'Demo', role, branch.insertId]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const manager = await user('MANAGER'), cashier = await user('CASHIER');
    async function call(path, body, token = manager, retry = key()) {
      const r = await fetch(process.env.API_URL + path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': retry }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const activation = '/api/v1/inventory/activation';
    assert.equal((await call(activation)).data.enabled, false);
    assert.equal((await call(activation, { initial_count_confirmed: true }, cashier)).status, 403);
    assert.equal((await call(activation, { initial_count_confirmed: false })).status, 400);
    assert.equal((await call('/api/v1/inventory/receptions', { product_id: products[0], quantity: '10', notes: null })).status, 201);
    async function sale(items) {
      const total = items.reduce((n, item) => n + item.quantity * 100, 0);
      const [s] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, 'SENT_TO_CASHIER', ?, ?)", [`STOCK-${suffix}-${sales.length}`, branch.insertId, users[0], total, total]);
      sales.push(s.insertId);
      for (const item of items) await db.execute('INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price_cents, line_total_cents) VALUES (?, ?, ?, ?, 100, ?)', [s.insertId, item.product_id, 'Demo planta', item.quantity, item.quantity * 100]);
      const claim = (await call(`/api/v1/cashier/sales/${s.insertId}/claim`, { claim_token: null }, cashier)).data.claim_token;
      return { id: s.insertId, body: { claim_token: claim, method: 'CASH', amount_received_cents: total, reference: null }, key: key() };
    }
    const pay = s => call(`/api/v1/cashier/sales/${s.id}/payments`, s.body, cashier, s.key);
    const refund = (s, restock = true, retry = key()) => call(`/api/v1/cashier/sales/${s.id}/refunds`, { reason: 'Devolucion sintetica', method: 'CASH', restock, money_returned: true }, manager, retry);
    async function quantity(productId = products[0]) {
      const [[row]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branch.insertId, productId]);
      return row?.quantity ?? '0.000';
    }
    const historic = await sale([{ product_id: products[0], quantity: 2 }]);
    assert.equal((await pay(historic)).status, 201);
    assert.equal(await quantity(), '10.000');
    const activated = await call(activation, { initial_count_confirmed: true });
    assert.equal(activated.data.enabled, true);
    const replayActivation = await call(activation, { initial_count_confirmed: true });
    assert.equal(replayActivation.data.idempotent_replay, true);
    assert.equal(replayActivation.data.activated_at, activated.data.activated_at);
    assert.equal((await pay(historic)).status, 200); // no historical backfill
    assert.equal((await refund(historic)).status, 201);
    assert.equal(await quantity(), '10.000');
    const grouped = await sale([{ product_id: products[0], quantity: 2 }, { product_id: products[0], quantity: 3 }]);
    const paid = await Promise.all([pay(grouped), pay(grouped)]);
    assert.deepEqual(paid.map(r => r.status).sort(), [200, 201]);
    assert.equal(await quantity(), '5.000');
    const [[movement]] = await db.execute("SELECT COUNT(*) AS n, SUM(quantity) AS quantity FROM inventory_movements WHERE sale_id = ? AND movement_type = 'SALE'", [grouped.id]);
    assert.equal(Number(movement.n), 1);
    assert.equal(movement.quantity, '-5.000');
    assert.equal((await call(`/api/v1/cashier/sales/${grouped.id}/refunds`)).data.restock_available, true);
    const refundKey = key();
    const returned = await Promise.all([refund(grouped, true, refundKey), refund(grouped, true, refundKey)]);
    assert.deepEqual(returned.map(r => r.status).sort(), [200, 201]);
    assert.equal(await quantity(), '10.000');
    const mixed = await sale([{ product_id: products[0], quantity: 2 }, { product_id: products[1], quantity: 1 }]);
    assert.equal((await pay(mixed)).data.error, 'INVENTORY_INSUFFICIENT');
    assert.equal(await quantity(), '10.000'); // first product's deduction rolled back
    const [[noPayment]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_payments WHERE sale_id = ?', [mixed.id]);
    assert.equal(Number(noPayment.n), 0);
    const [[unpaid]] = await db.execute('SELECT status FROM sales WHERE id = ?', [mixed.id]);
    assert.equal(unpaid.status, 'SENT_TO_CASHIER');
    assert.equal((await call('/api/v1/inventory/receptions', { product_id: products[1], quantity: '3', notes: null })).status, 201);
    assert.equal((await pay(mixed)).status, 201);
    assert.equal(await quantity(), '8.000');
    assert.equal(await quantity(products[1]), '2.000');
    // Failure after stock, payment and status writes must roll everything back.
    const late = await sale([{ product_id: products[0], quantity: 1 }]);
    await db.query(`CREATE TRIGGER stock_history_failure_${suffix} BEFORE INSERT ON sale_status_history FOR EACH ROW SET NEW.observation = IF(NEW.sale_id = ${late.id}, NULL, NEW.observation)`);
    historyTrigger = true;
    assert.equal((await pay(late)).status, 503);
    assert.equal(await quantity(), '8.000');
    const [[noStock]] = await db.execute('SELECT COUNT(*) AS n FROM inventory_movements WHERE sale_id = ?', [late.id]);
    assert.equal(Number(noStock.n), 0);
    await db.query(`DROP TRIGGER stock_history_failure_${suffix}`); historyTrigger = false;
    assert.equal((await pay(late)).status, 201);
    assert.equal(await quantity(), '7.000');
    await db.query(`CREATE TRIGGER stock_refund_failure_${suffix} BEFORE INSERT ON inventory_movements FOR EACH ROW SET NEW.quantity = IF(NEW.branch_id = ${branch.insertId} AND NEW.movement_type = 'REFUND' AND NEW.product_id = ${products[1]}, 0, NEW.quantity)`);
    refundTrigger = true;
    const rollbackKey = key();
    assert.equal((await refund(mixed, true, rollbackKey)).status, 400);
    assert.equal(await quantity(), '7.000');
    const [[noRefund]] = await db.execute('SELECT COUNT(*) AS n FROM sale_refunds WHERE sale_id = ?', [mixed.id]);
    assert.equal(Number(noRefund.n), 0);
    await db.query(`DROP TRIGGER stock_refund_failure_${suffix}`); refundTrigger = false;
    assert.equal((await refund(mixed, true, rollbackKey)).status, 201);
    assert.equal(await quantity(), '9.000');
    assert.equal(await quantity(products[1]), '3.000');
    const oversellA = await sale([{ product_id: products[0], quantity: 6 }]), oversellB = await sale([{ product_id: products[0], quantity: 6 }]);
    const oversold = await Promise.all([pay(oversellA), pay(oversellB)]);
    assert.deepEqual(oversold.map(r => r.status).sort(), [201, 409]);
    assert.equal(await quantity(), '3.000');
    const winner = oversold[0].status === 201 ? oversellA : oversellB;
    assert.equal((await refund(winner, false)).status, 201);
    assert.equal(await quantity(), '3.000'); // money refunded without restocking
    const fractional = await sale([{ product_id: products[1], quantity: 0.5 }]);
    assert.equal((await pay(fractional)).status, 201);
    assert.equal(await quantity(products[1]), '2.500');
    assert.equal((await refund(fractional)).status, 201);
    assert.equal(await quantity(products[1]), '3.000');
    await assert.rejects(db.execute("INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, sale_id, idempotency_hash, created_by) VALUES (?, ?, 'SALE', -1, ?, ?, ?)", [branch.insertId, products[0], historic.id, randomBytes(32), users[1]]), e => e.code === 'ER_SIGNAL_EXCEPTION');
    await assert.rejects(db.execute("INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, refund_id, idempotency_hash, created_by) VALUES (?, ?, 'REFUND', 1, ?, ?, ?)", [branch.insertId, products[0], returned[0].data.refund.id, randomBytes(32), users[0]]), e => e.code === 'ER_SIGNAL_EXCEPTION');
  } finally {
    if (refundTrigger) await db.query(`DROP TRIGGER stock_refund_failure_${suffix}`);
    if (historyTrigger) await db.query(`DROP TRIGGER stock_history_failure_${suffix}`);
    if (branch) {
      await db.execute('DELETE FROM inventory_movements WHERE branch_id = ?', [branch.insertId]);
      await db.execute('DELETE FROM inventory WHERE branch_id = ?', [branch.insertId]);
      await db.execute('UPDATE branches SET inventory_enabled = 0, inventory_activated_at = NULL, inventory_activated_by = NULL WHERE id = ?', [branch.insertId]);
    }
    for (const id of sales) {
      await db.execute('DELETE FROM sale_refunds WHERE sale_id = ?', [id]);
      await db.execute('DELETE FROM cashier_payments WHERE sale_id = ?', [id]);
      await db.execute('DELETE FROM sale_payment_claims WHERE sale_id = ?', [id]);
      await db.execute('DELETE FROM sale_status_history WHERE sale_id = ?', [id]);
      await db.execute('DELETE FROM sale_items WHERE sale_id = ?', [id]);
      await db.execute('DELETE FROM sales WHERE id = ?', [id]);
    }
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]); await db.execute('DELETE FROM users WHERE id = ?', [id]); }
    for (const id of products) await db.execute('DELETE FROM products WHERE id = ?', [id]);
    if (category) await db.execute('DELETE FROM categories WHERE id = ?', [category.insertId]);
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    await db.end();
  }
});
