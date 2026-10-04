import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Closing tests require isolated local Compose');

test('closings SQL/HTTP: exact memberships, scope, replay, rollback and concurrent payment/refund', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], sales = [], payments = [];
  const key = () => randomBytes(32).toString('hex');
  let branch, other, category, product, trigger = false;
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`CLOSE-${suffix}`, 'Demo corte']);
    [other] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`CLOSE2-${suffix}`, 'Otra demo']);
    [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`CLOSE-${suffix}`]);
    [product] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 900)', [`CLOSE-${suffix}`, 'Demo planta', category.insertId]);
    async function user(role, branchId = branch.insertId) {
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`close-${suffix}-${users.length}@example.invalid`, 'Demo', role, branchId]);
      users.push(row.insertId);
      return session(row.insertId);
    }
    async function session(id) {
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [id, sessionDigest(token)]);
      return token;
    }
    const manager = await user('MANAGER'), manager2 = await session(users[0]), cashier = await user('CASHIER'), seller = await user('SALES'), elsewhere = await user('MANAGER', other.insertId);
    async function sale(method, actor = users[0]) {
      const [row] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, ?, 900, 900)", [`CLOSE-${suffix}-${sales.length}`, branch.insertId, actor, method ? 'PAID' : 'SENT_TO_CASHIER']);
      sales.push(row.insertId);
      await db.execute('INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price_cents, line_total_cents) VALUES (?, ?, ?, 1, 900, 900)', [row.insertId, product.insertId, 'Demo planta']);
      if (method) {
        const [p] = await db.execute('INSERT INTO cashier_payments (sale_id, branch_id, cashier_id, idempotency_key, method, amount_due_cents, amount_received_cents, change_cents, reference) VALUES (?, ?, ?, ?, ?, 900, ?, ?, ?)', [row.insertId, branch.insertId, actor, key(), method, method === 'CASH' ? 1000 : 900, method === 'CASH' ? 100 : 0, method === 'TRANSFER' ? 'DEMO' : null]);
        payments.push(p.insertId);
      }
      return row.insertId;
    }
    async function call(path, body, token = manager, retry = key()) {
      const r = await fetch(process.env.API_URL + path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': retry }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const base = '/api/v1/cashier/closings', input = { opening_cash_cents: 100, counted_cash_cents: 990 };
    const refund = (id, method = 'CARD') => call(`/api/v1/cashier/sales/${id}/refunds`, { reason: 'Devolucion demo para corte', method, restock: false, money_returned: true });
    assert.equal((await call(base + '/preview', undefined, seller)).status, 403);
    assert.equal((await call(base, input)).data.error, 'CLOSING_EMPTY');
    const cashSale = await sale('CASH'), cardSale = await sale('CARD');
    await sale('TRANSFER');
    await sale('CASH', users[1]); // another cashier must remain outside this closing
    assert.equal((await refund(cashSale)).status, 201);
    const preview = (await call(base + '/preview')).data;
    assert.equal(preview.payment_count, 3);
    assert.equal(preview.refund_count, 1);
    assert.equal(preview.cash_sales_cents, 900);
    assert.equal(preview.card_sales_cents, 900);
    assert.equal(preview.transfer_sales_cents, 900);
    assert.equal(preview.other_refunds_cents, 900);
    const retry = key();
    const race = await Promise.all([call(base, input, manager, retry), call(base, input, manager2, retry)]);
    assert.deepEqual(race.map(r => r.status).sort(), [200, 201]);
    const first = race[0].data;
    assert.equal(first.closing.id, race[1].data.closing.id);
    assert.equal(first.closing.expected_cash_cents, 1000);
    assert.equal(first.closing.difference_cents, -10);
    assert.deepEqual(first.payment_ids, payments.slice(0, 3));
    assert.equal(first.refund_ids.length, 1);
    assert.equal((await call(base + '/recover', {}, manager, retry)).data.closing.id, first.closing.id);
    assert.equal((await call(base, { ...input, counted_cash_cents: 1000 }, manager, retry)).data.error, 'CLOSING_IDEMPOTENCY_CONFLICT');
    assert.equal((await call(base + '/recover', {}, cashier, retry)).status, 404);
    assert.equal((await call(base + '/' + first.closing.id, undefined, cashier)).status, 404);
    assert.equal((await call(base + '/' + first.closing.id, undefined, elsewhere)).status, 404);
    assert.equal((await call(base + '/preview')).data.payment_count, 0);
    assert.equal((await call(base + '/preview', undefined, cashier)).data.payment_count, 1);
    assert.equal((await call(base, input)).data.error, 'CLOSING_EMPTY');
    // A late link failure must roll back the header, membership and retry key.
    const rollbackSale = await sale('CASH'), rollbackPayment = payments.at(-1);
    await db.query(`CREATE TRIGGER closing_test_failure_${suffix} BEFORE INSERT ON cashier_closing_payments FOR EACH ROW SET NEW.closing_id = IF(NEW.payment_id = ${rollbackPayment}, 4294967295, NEW.closing_id)`);
    trigger = true;
    const rollbackKey = key();
    const failed = await call(base, input, manager, rollbackKey);
    assert.equal(failed.status, 409);
    assert.equal(failed.data.error, 'REFERENCE_CONFLICT');
    assert.equal((await call(base + '/recover', {}, manager, rollbackKey)).status, 404);
    assert.equal((await call(base + '/preview')).data.payment_count, 1);
    await db.query(`DROP TRIGGER closing_test_failure_${suffix}`); trigger = false;
    assert.equal((await call(base, input, manager, rollbackKey)).status, 201);
    // Refunds of already closed payments belong to the next cut, never rewrite it.
    assert.equal((await refund(cardSale, 'CASH')).status, 201);
    const negative = await call(base, { opening_cash_cents: 0, counted_cash_cents: 0 });
    assert.equal(negative.status, 201);
    assert.equal(negative.data.closing.expected_cash_cents, -900);
    assert.equal(negative.data.closing.difference_cents, 900);
    assert.equal(negative.data.payment_ids.length, 0);
    // Simultaneous real HTTP payment and cut: operation appears here or next.
    const pendingSale = await sale(null);
    const claim = (await call(`/api/v1/cashier/sales/${pendingSale}/claim`, { claim_token: null })).data.claim_token;
    const concurrent = await Promise.all([
      call(base, { opening_cash_cents: 0, counted_cash_cents: 900 }, manager2),
      call(`/api/v1/cashier/sales/${pendingSale}/payments`, { claim_token: claim, method: 'CASH', amount_received_cents: 1000, reference: null }),
    ]);
    assert.equal(concurrent[1].status, 201);
    assert.ok([201, 409].includes(concurrent[0].status));
    if (concurrent[0].status === 409) assert.equal(concurrent[0].data.error, 'CLOSING_EMPTY');
    if ((await call(base + '/preview')).data.payment_count) assert.equal((await call(base, { opening_cash_cents: 0, counted_cash_cents: 900 })).status, 201);
    const [[assigned]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_closing_payments WHERE payment_id = ?', [concurrent[1].data.payment.id]);
    assert.equal(Number(assigned.n), 1);
    // Simultaneous refund and cut also remains in one exact membership.
    const refundsRace = await Promise.all([call(base, { opening_cash_cents: 0, counted_cash_cents: 0 }, manager2), refund(rollbackSale, 'CASH')]);
    assert.equal(refundsRace[1].status, 201);
    assert.ok([201, 409].includes(refundsRace[0].status));
    if ((await call(base + '/preview')).data.refund_count) assert.equal((await call(base, { opening_cash_cents: 0, counted_cash_cents: 0 })).status, 201);
    const [[refundAssigned]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_closing_refunds WHERE refund_id = ?', [refundsRace[1].data.refund.id]);
    assert.equal(Number(refundAssigned.n), 1);
    const [[unchanged]] = await db.execute('SELECT expected_cash_cents FROM cashier_closings WHERE id = ?', [first.closing.id]);
    assert.equal(Number(unchanged.expected_cash_cents), 1000);
    await assert.rejects(db.execute('INSERT INTO cashier_closing_payments (payment_id, closing_id) VALUES (?, ?)', [payments[3], first.closing.id]), e => e.code === 'ER_SIGNAL_EXCEPTION');
    await assert.rejects(db.execute('INSERT INTO cashier_closing_payments (payment_id, closing_id) VALUES (?, ?)', [payments[0], first.closing.id]), e => e.code === 'ER_DUP_ENTRY');
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try {
      for (const table of ['cashier_closings', 'cashier_closing_payments', 'cashier_closing_refunds']) await assert.rejects(runtime.query(`DELETE FROM ${table} WHERE 1 = 0`), e => e.code === 'ER_TABLEACCESS_DENIED_ERROR');
      await assert.rejects(runtime.execute('UPDATE cashier_closings SET counted_cash_cents = 1 WHERE id = ?', [first.closing.id]), e => e.code === 'ER_TABLEACCESS_DENIED_ERROR');
    } finally { await runtime.end(); }
  } finally {
    if (trigger) await db.query(`DROP TRIGGER closing_test_failure_${suffix}`);
    if (branch) {
      await db.execute('DELETE l FROM cashier_closing_payments l JOIN cashier_closings c ON c.id = l.closing_id WHERE c.branch_id = ?', [branch.insertId]);
      await db.execute('DELETE l FROM cashier_closing_refunds l JOIN cashier_closings c ON c.id = l.closing_id WHERE c.branch_id = ?', [branch.insertId]);
      await db.execute('DELETE FROM cashier_closings WHERE branch_id = ?', [branch.insertId]);
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
    if (product) await db.execute('DELETE FROM products WHERE id = ?', [product.insertId]);
    if (category) await db.execute('DELETE FROM categories WHERE id = ?', [category.insertId]);
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    if (other) await db.execute('DELETE FROM branches WHERE id = ?', [other.insertId]);
    await db.end();
  }
});
