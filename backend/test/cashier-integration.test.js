import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Cashier tests require isolated local Compose');

test('cashier SQL/HTTP: exclusive claims, expiry, release, all methods, concurrent payment and rollback', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], sales = [];
  let branch, otherBranch, category, product, failureTrigger = false, historyFailureTrigger = false;
  const key = () => randomBytes(32).toString('hex');
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`CASH-${suffix}`, 'Sucursal sintetica']);
    [otherBranch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`OTHER-${suffix}`, 'Otra sucursal sintetica']);
    [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`CASH-${suffix}`]);
    [product] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 1000)', [`CASH-${suffix}`, 'Planta sintetica', category.insertId]);
    async function user(role, branchId = branch.insertId) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`cash-${suffix}-${users.length}@example.invalid`, 'Persona sintetica', role, branchId]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const cashierA = await user('CASHIER'), cashierB = await user('CASHIER'), seller = await user('SALES'), elsewhere = await user('CASHIER', otherBranch.insertId);
    for (let i = 0; i < 5; i++) {
      const [sale] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, 'SENT_TO_CASHIER', 1000, 1000)", [`TEST-${suffix}-${i}`, branch.insertId, users[2]]);
      sales.push(sale.insertId);
      await db.execute('INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price_cents, line_total_cents) VALUES (?, ?, ?, 1, 1000, 1000)', [sale.insertId, product.insertId, 'Planta sintetica']);
    }
    async function request(id, action, body, token = cashierA, retryKey = key()) {
      const url = process.env.API_URL + '/api/v1/cashier/sales' + (id ? `/${id}` : '') + (action ? `/${action}` : '');
      const response = await fetch(url, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': retryKey }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: response.status, data: await response.json() };
    }
    assert.equal((await request(null, null, undefined, seller)).status, 403);
    assert.equal((await request(sales[0], null, undefined, elsewhere)).status, 404);
    for (const [header, wrong] of [['X-Expected-Actor-Id', users[2]], ['X-Expected-Branch-Id', otherBranch.insertId]]) {
      const mismatch = await fetch(process.env.API_URL + `/api/v1/cashier/sales/${sales[0]}/claim`, {
        method: 'POST', headers: { Authorization: `Bearer ${cashierA}`, 'Content-Type': 'application/json', [header]: String(wrong) },
        body: JSON.stringify({ claim_token: null }),
      });
      assert.equal(mismatch.status, 403);
      assert.equal((await mismatch.json()).error, 'CASHIER_IDENTITY_CHANGED');
    }
    assert.equal((await request(null)).data.items.length, 5);
    const projectedResponse = await fetch(process.env.API_URL + '/api/v1/cashier/sales?view=operations', { headers: { Authorization: `Bearer ${cashierA}` } });
    assert.equal(projectedResponse.status, 200);
    const projected = await projectedResponse.json();
    assert.equal(projected.items[0].operation.item_count, 1);
    assert.equal(projected.items[0].operation.created_by_label, 'Persona sintetica');
    assert.equal(projected.items[0].operation.claim_state, 'AVAILABLE');
    assert.match(projected.items[0].operation.server_time, /\.\d{6}Z$/);
    assert.equal(Object.hasOwn(projected.items[0].operation, 'claim_token'), false);
    const race = await Promise.all([request(sales[0], 'claim', { claim_token: null }, cashierA), request(sales[0], 'claim', { claim_token: null }, cashierB)]);
    assert.deepEqual(race.map(row => row.status).sort(), [200, 409]);
    const winner = race[0].status === 200 ? cashierA : cashierB;
    const loser = winner === cashierA ? cashierB : cashierA;
    const claim = race.find(row => row.status === 200).data.claim_token;
    assert.equal((await request(sales[0], 'claim', { claim_token: claim }, winner)).data.renewed, true);
    assert.equal((await request(sales[0], 'release', { claim_token: claim }, loser)).status, 409);
    const cash = { claim_token: claim, method: 'CASH', amount_received_cents: 1200, reference: null };
    assert.equal((await request(sales[0], 'payments', { ...cash, amount_received_cents: 999 }, winner)).status, 400);
    assert.equal((await request(sales[0], 'payments', cash, loser)).status, 409);
    const paymentKey = key();
    const paid = await Promise.all([request(sales[0], 'payments', cash, winner, paymentKey), request(sales[0], 'payments', cash, winner, paymentKey)]);
    assert.deepEqual(paid.map(row => row.status).sort(), [200, 201]);
    assert.equal(paid[0].data.payment.id, paid[1].data.payment.id);
    assert.equal(paid[0].data.payment.change_cents, 200);
    assert.equal(paid[0].data.sale.status, 'PAID');
    assert.equal((await request(sales[0], 'payment-result', {}, winner, paymentKey)).data.payment.id, paid[0].data.payment.id);
    assert.equal((await request(sales[0], 'payments', { ...cash, amount_received_cents: 1300 }, winner, paymentKey)).status, 409);
    assert.equal((await request(sales[0], 'payments', cash, winner)).status, 409);
    assert.equal((await request(sales[0], 'payment-result', {}, loser, paymentKey)).status, 404);
    async function receiptGet(path, token = winner) {
      const response = await fetch(process.env.API_URL + '/api/v1/cashier/receipts' + path, { headers: { Authorization: `Bearer ${token}` } });
      return { status: response.status, data: await response.json() };
    }
    const paymentId = paid[0].data.payment.id;
    const listing = await receiptGet('?limit=1');
    assert.equal(listing.status, 200); assert.equal(listing.data.items[0].id, paymentId);
    const printed = await receiptGet(`/${paymentId}`);
    assert.equal(printed.status, 200); assert.equal(printed.data.payment.amount_received_cents, 1200);
    assert.equal(printed.data.payment.change_cents, 200); assert.equal(printed.data.items.length, 1);
    assert.equal(printed.data.branch.id, branch.insertId); assert.equal(printed.data.refund, null);
    for (const forbidden of ['claim_token', 'idempotency_key', 'request_hash']) assert.equal(Object.hasOwn(printed.data.payment, forbidden), false);
    assert.equal((await receiptGet(`/${paymentId}`, loser)).status, 404);
    assert.equal((await receiptGet(`/${paymentId}`, elsewhere)).status, 404);
    assert.equal((await receiptGet('', seller)).status, 403);
    assert.equal((await receiptGet('?limit=101')).status, 400);
    assert.equal((await receiptGet('?limit=1&limit=2')).status, 400);
    assert.equal((await receiptGet(`/${paymentId}?extra=1`)).status, 400);
    assert.equal((await receiptGet(`?before_id=${paymentId}`)).data.items.length, 0);
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_payments WHERE sale_id = ?', [sales[0]]);
    assert.equal(Number(count.n), 1);
    const [[history]] = await db.execute("SELECT COUNT(*) AS n FROM sale_status_history WHERE sale_id = ? AND new_status = 'PAID'", [sales[0]]);
    assert.equal(Number(history.n), 1);

    const expiring = (await request(sales[1], 'claim', { claim_token: null })).data.claim_token;
    await db.execute('UPDATE sale_payment_claims SET created_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE), expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE) WHERE sale_id = ?', [sales[1]]);
    assert.equal((await request(sales[1], 'payments', { ...cash, claim_token: expiring })).data.error, 'CLAIM_EXPIRED');
    assert.equal((await request(sales[1], 'claim', { claim_token: expiring })).data.error, 'CLAIM_EXPIRED');
    const replacement = (await request(sales[1], 'claim', { claim_token: null }, cashierB)).data.claim_token;
    assert.notEqual(replacement, expiring);
    const released = await request(sales[1], 'release', { claim_token: replacement }, cashierB);
    assert.equal(released.data.closed_reason, 'RELEASED');
    assert.equal(released.data.claim_token, replacement);
    assert.match(released.data.released_at, /\.\d{6}Z$/);
    const cardClaim = (await request(sales[1], 'claim', { claim_token: null })).data.claim_token;
    const card = await request(sales[1], 'payments', { claim_token: cardClaim, method: 'CARD', amount_received_cents: null, reference: 'AUTH-DEMO' });
    assert.equal(card.status, 201);
    assert.equal(card.data.payment.change_cents, 0);
    const transferClaim = (await request(sales[2], 'claim', { claim_token: null })).data.claim_token;
    assert.equal((await request(sales[2], 'payments', { claim_token: transferClaim, method: 'TRANSFER', amount_received_cents: null, reference: null })).status, 400);
    assert.equal((await request(sales[2], 'payments', { claim_token: transferClaim, method: 'TRANSFER', amount_received_cents: null, reference: 'TRANSFER-DEMO' })).status, 201);

    const firstPage = await receiptGet('?limit=1', cashierA);
    assert.equal(firstPage.data.items.length, 1); assert.ok(firstPage.data.next_before_id);
    const secondPage = await receiptGet(`?limit=1&before_id=${firstPage.data.next_before_id}`, cashierA);
    assert.equal(secondPage.data.items.length, 1); assert.ok(secondPage.data.items[0].id < firstPage.data.items[0].id);
    const oldest = await receiptGet(`?before_id=${secondPage.data.items[0].id}`, cashierA);
    assert.equal(oldest.data.items.length, winner === cashierA ? 1 : 0);
    assert.equal((await receiptGet('', loser)).data.items.every(p => p.id !== paymentId), true);
    const rollbackClaim = (await request(sales[3], 'claim', { claim_token: null })).data.claim_token;
    await db.query(`CREATE TRIGGER cash_test_failure_${suffix} BEFORE INSERT ON cashier_payments FOR EACH ROW SET NEW.amount_received_cents = IF(NEW.sale_id = ${sales[3]}, 0, NEW.amount_received_cents)`);
    failureTrigger = true;
    assert.equal((await request(sales[3], 'payments', { ...cash, claim_token: rollbackClaim })).status, 400);
    assert.equal((await request(sales[3])).data.sale.status, 'SENT_TO_CASHIER');
    const [[rolledBack]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_payments WHERE sale_id = ?', [sales[3]]);
    assert.equal(Number(rolledBack.n), 0);
    const [[stillActive]] = await db.execute('SELECT consumed_at FROM sale_payment_claims WHERE sale_id = ?', [sales[3]]);
    assert.equal(stillActive.consumed_at, null);
    // Fail after payment, status and claim were written: all must roll back.
    const historyClaim = (await request(sales[4], 'claim', { claim_token: null })).data.claim_token;
    await db.query(`CREATE TRIGGER cash_history_failure_${suffix} BEFORE INSERT ON sale_status_history FOR EACH ROW SET NEW.observation = IF(NEW.sale_id = ${sales[4]}, NULL, NEW.observation)`);
    historyFailureTrigger = true;
    assert.equal((await request(sales[4], 'payments', { ...cash, claim_token: historyClaim })).status, 503);
    assert.equal((await request(sales[4])).data.sale.status, 'SENT_TO_CASHIER');
    const [[historyRollback]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_payments WHERE sale_id = ?', [sales[4]]);
    assert.equal(Number(historyRollback.n), 0);
    const [[unconsumed]] = await db.execute('SELECT consumed_at FROM sale_payment_claims WHERE sale_id = ?', [sales[4]]);
    assert.equal(unconsumed.consumed_at, null);
    // A confirmed payment is preserved; closing a missing key fences every late POST.
    const committed = await request(sales[0], 'payment-retire', {}, winner, paymentKey);
    assert.equal(committed.data.status, 'COMMITTED');
    assert.equal(committed.data.receipt.payment.id, paid[0].data.payment.id);
    for (let n = 0; n < 2; n++) {
      const [row] = await db.execute("INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,total_cents) VALUES(?,?,?,'SENT_TO_CASHIER',1000,1000)", [`RET-${suffix}-${n}`, branch.insertId, users[2]]);
      sales.push(row.insertId);
      await db.execute('INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price_cents,line_total_cents) VALUES(?,?,?,1,1000,1000)', [row.insertId, product.insertId, 'Planta sintetica']);
      const token = (await request(row.insertId, 'claim', { claim_token: null })).data.claim_token;
      const originalKey = key(), input = { ...cash, claim_token: token };
      if (n === 0) {
        assert.equal((await request(row.insertId, 'payment-retire', {} , elsewhere, originalKey)).status, 404);
        assert.equal((await request(row.insertId, 'payment-retire', {}, cashierA, originalKey)).data.status, 'RETIRED');
        assert.equal((await request(row.insertId, 'payment-retire', {}, cashierA, originalKey)).data.status, 'RETIRED');
        assert.equal((await request(row.insertId, 'payments', input, cashierA, originalKey)).data.error, 'PAYMENT_ATTEMPT_RETIRED');
        assert.equal((await request(sales[2], 'payment-retire', {}, cashierA, originalKey)).status, 409);
        assert.equal((await request(row.insertId, 'payments', input)).status, 201);
      } else {
        const race = await Promise.all([request(row.insertId, 'payments', input, cashierA, originalKey), request(row.insertId, 'payment-retire', {}, cashierA, originalKey)]);
        const closed = race[1].data;
        assert.ok(['RETIRED','COMMITTED'].includes(closed.status));
        const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM cashier_payments WHERE sale_id=?', [row.insertId]);
        assert.equal(Number(count.n), closed.status === 'COMMITTED' ? 1 : 0);
        assert.equal(race[0].status, closed.status === 'COMMITTED' ? 201 : 409);
        assert.equal((await request(row.insertId, 'payments', input, cashierA, originalKey)).status, closed.status === 'COMMITTED' ? 200 : 409);
      }
    }
  } finally {
    if (historyFailureTrigger) await db.query(`DROP TRIGGER cash_history_failure_${suffix}`);
    if (failureTrigger) await db.query(`DROP TRIGGER cash_test_failure_${suffix}`);
    for (const id of sales) {
      await db.execute('DELETE FROM payment_attempt_retirements WHERE sale_id = ?', [id]);
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
    if (otherBranch) await db.execute('DELETE FROM branches WHERE id = ?', [otherBranch.insertId]);
    await db.end();
  }
});
