import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Checkout tests require isolated local Compose');

test('web order checkout preserves accepted prices, serializes retries, guards completion and rolls back', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], orders = [];
  let branch, otherBranch, category, product, promotion, failureTrigger = false;
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`WEB-${suffix}`, 'Sucursal sintetica']);
    [otherBranch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`WRONG-${suffix}`, 'Otra sucursal sintetica']);
    [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`WEB-${suffix}`]);
    [product] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 1000)', [`WEB-${suffix}`, 'Planta sintetica', category.insertId]);
    [promotion] = await db.execute("INSERT INTO promotions (name, promo_type, scope, fixed_amount_cents) VALUES (?, 'FIXED_AMOUNT', 'SELECTED_PRODUCTS', 100)", [`WEB-${suffix}`]);
    await db.execute('INSERT INTO promotion_products (promotion_id, product_id) VALUES (?, ?)', [promotion.insertId, product.insertId]);
    async function user(role, scope = branch.insertId) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`checkout-${suffix}-${users.length}@example.invalid`, 'Persona sintetica', role, scope]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const manager = await user('MANAGER'), cashier = await user('CASHIER'), denied = await user('SALES'), other = await user('MANAGER', otherBranch.insertId);
    async function request(path, method = 'GET', body, token = manager, key = randomBytes(32).toString('hex')) {
      const response = await fetch(process.env.API_URL + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': key }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: response.status, data: await response.json() };
    }
    for (let i = 0; i < 2; i++) {
      const result = await request('/api/v1/web-orders', 'POST', { branch_id: branch.insertId, items: [{ product_id: product.insertId, quantity: 2 }], customer_name: 'Cliente sintetico', customer_phone: null, customer_email: `customer-${suffix}-${i}@example.invalid`, notes: null, expected_total_cents: 1800 });
      assert.equal(result.status, 201);
      orders.push(result.data.id);
    }
    const path = id => `/api/v1/admin/web-orders/${id}`;
    const send = (id, token = manager) => request(path(id) + '/send-to-cashier', 'POST', {}, token);
    assert.equal((await send(orders[0])).status, 409); // PENDING cannot go to cashier.
    assert.equal((await send(orders[0], denied)).status, 403);
    assert.equal((await send(orders[0], other)).status, 404);
    assert.equal((await request(path(orders[0]), 'PATCH', { status: 'CONFIRMED', expected_revision: 0, observation: null })).status, 200);
    assert.equal((await request(path(orders[0]), 'PATCH', { status: 'READY', expected_revision: 1, observation: null })).status, 200);
    await db.execute('UPDATE products SET price_cents = 1500 WHERE id = ?', [product.insertId]);
    const sent = await Promise.all([send(orders[0]), send(orders[0], cashier)]);
    assert.deepEqual(sent.map(row => row.status).sort(), [200, 201]);
    const saleId = sent[0].data.sale_id;
    assert.equal(sent[1].data.sale_id, saleId);
    assert.equal(sent[0].data.total_cents, 1800);
    const detail = await request(`/api/v1/cashier/sales/${saleId}`, 'GET', undefined, cashier);
    assert.equal(detail.data.sale.subtotal_cents, 2000);
    assert.equal(detail.data.sale.discount_cents, 200);
    assert.equal(detail.data.items[0].list_price_cents, 1000);
    assert.equal(detail.data.items[0].unit_price_cents, 900);
    assert.equal((await request(path(orders[0]))).data.order.cashier_sale_id, saleId);
    const projected = await request(path(orders[0]) + '?view=operations');
    assert.equal(projected.status, 200);
    assert.equal(projected.data.operation.branch.id, branch.insertId);
    assert.equal(projected.data.operation.checkout.id, saleId);
    assert.equal(projected.data.operation.checkout.total_cents, 1800);
    assert.equal(projected.data.operation.order_number, `VW-${orders[0]}`);
    assert.match(projected.data.operation.server_time, /\.\d{6}Z$/);
    assert.equal((await request(path(orders[0]) + '?view=operations', 'GET', undefined, other)).status, 404);
    assert.equal((await request(path(orders[0]) + '?view=operations&view=operations')).status, 400);

    assert.equal((await request(path(orders[0]), 'PATCH', { status: 'CANCELLED', expected_revision: 2, observation: null })).data.error, 'WEB_ORDER_ALREADY_IN_CASHIER');
    assert.equal((await request(path(orders[0]), 'PATCH', { status: 'COMPLETED', expected_revision: 2, observation: null })).data.error, 'WEB_ORDER_PAYMENT_REQUIRED');
    await assert.rejects(db.execute("UPDATE web_orders SET status = 'CANCELLED' WHERE id = ?", [orders[0]]), { code: 'ER_SIGNAL_EXCEPTION' });
    await assert.rejects(db.execute("UPDATE web_orders SET status = 'COMPLETED' WHERE id = ?", [orders[0]]), { code: 'ER_SIGNAL_EXCEPTION' });
    const claim = await request(`/api/v1/cashier/sales/${saleId}/claim`, 'POST', { claim_token: null }, cashier);
    const payment = await request(`/api/v1/cashier/sales/${saleId}/payments`, 'POST', { claim_token: claim.data.claim_token, method: 'CASH', amount_received_cents: 2000, reference: null }, cashier);
    assert.equal(payment.status, 201);
    assert.equal(payment.data.payment.amount_due_cents, 1800);
    assert.equal(payment.data.payment.change_cents, 200);
    const completion = { status: 'COMPLETED', expected_revision: 2, observation: 'Entregado' };
    assert.equal((await request(path(orders[0]), 'PATCH', completion)).status, 200);
    assert.equal((await request(path(orders[0]), 'PATCH', completion)).data.idempotent_replay, true);
    assert.equal((await send(orders[0])).data.status, 'PAID');
    const [[salesCount]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE web_order_id = ?', [orders[0]]);
    assert.equal(Number(salesCount.n), 1);

    assert.equal((await request(path(orders[1]), 'PATCH', { status: 'CONFIRMED', expected_revision: 0, observation: null })).status, 200);
    await db.execute('UPDATE products SET is_active = 0 WHERE id = ?', [product.insertId]);
    assert.equal((await send(orders[1])).data.error, 'WEB_ORDER_ITEMS_UNAVAILABLE');
    await db.execute('UPDATE products SET is_active = 1 WHERE id = ?', [product.insertId]);
    await db.query(`CREATE TRIGGER checkout_failure_${suffix} BEFORE INSERT ON sale_status_history FOR EACH ROW SET NEW.observation = IF((SELECT web_order_id FROM sales WHERE id = NEW.sale_id) = ${orders[1]}, NULL, NEW.observation)`);
    failureTrigger = true;
    assert.equal((await send(orders[1])).status, 503);
    const [[rollback]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE web_order_id = ?', [orders[1]]);
    assert.equal(Number(rollback.n), 0);
    await db.query(`DROP TRIGGER checkout_failure_${suffix}`);
    failureTrigger = false;
    assert.equal((await send(orders[1])).status, 201);
  } finally {
    if (failureTrigger) await db.query(`DROP TRIGGER checkout_failure_${suffix}`);
    for (const orderId of orders) {
      const [rows] = await db.execute('SELECT id FROM sales WHERE web_order_id = ?', [orderId]);
      for (const { id } of rows) {
        await db.execute('DELETE FROM cashier_payments WHERE sale_id = ?', [id]);
        await db.execute('DELETE FROM sale_payment_claims WHERE sale_id = ?', [id]);
        await db.execute('DELETE FROM sale_status_history WHERE sale_id = ?', [id]);
        await db.execute('DELETE FROM sale_items WHERE sale_id = ?', [id]);
        await db.execute('DELETE FROM sales WHERE id = ?', [id]);
      }
      await db.execute('DELETE FROM web_order_status_history WHERE order_id = ?', [orderId]);
      await db.execute('DELETE FROM web_order_items WHERE order_id = ?', [orderId]);
      await db.execute('DELETE FROM web_orders WHERE id = ?', [orderId]);
    }
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]); await db.execute('DELETE FROM users WHERE id = ?', [id]); }
    if (promotion) { await db.execute('DELETE FROM promotion_products WHERE promotion_id = ?', [promotion.insertId]); await db.execute('DELETE FROM promotions WHERE id = ?', [promotion.insertId]); }
    if (product) await db.execute('DELETE FROM products WHERE id = ?', [product.insertId]);
    if (category) await db.execute('DELETE FROM categories WHERE id = ?', [category.insertId]);
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    if (otherBranch) await db.execute('DELETE FROM branches WHERE id = ?', [otherBranch.insertId]);
    await db.end();
  }
});
