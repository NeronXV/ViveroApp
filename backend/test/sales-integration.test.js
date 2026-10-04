import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Sales tests require isolated local Compose');

test('sales HTTP/SQL: server prices, atomic snapshots, concurrent replay, recovery and own branch access', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
  const suffix = randomBytes(8).toString('hex');
  let branch, category, product, promotion, customer;
  const users = [];
  let failureTrigger = false;
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`SALE-${suffix}`, 'Sucursal sintetica']);
    [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`SALE-${suffix}`]);
    [product] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 1000)', [`SALE-${suffix}`, 'Planta sintetica', category.insertId]);
    [promotion] = await db.execute("INSERT INTO promotions (name, promo_type, scope, fixed_amount_cents) VALUES (?, 'FIXED_AMOUNT', 'SELECTED_PRODUCTS', 100)", [`SALE-${suffix}`]);
    await db.execute('INSERT INTO promotion_products (promotion_id, product_id) VALUES (?, ?)', [promotion.insertId, product.insertId]);
    await db.execute('INSERT INTO inventory (branch_id, product_id, quantity) VALUES (?, ?, 10)', [branch.insertId, product.insertId]);
    async function user(role) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`sale-${suffix}-${users.length}@example.invalid`, 'Persona sintetica', role, branch.insertId]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const token = await user('SALES'), other = await user('SALES'), denied = await user('INVENTORY');
    [customer] = await db.execute('INSERT INTO customers (full_name, created_by, updated_by) VALUES (?, ?, ?)', ['Cliente sintetico', users[0], users[0]]);
    const key = randomBytes(32).toString('hex');
    const input = { items: [{ product_id: product.insertId, quantity: 2 }], expected_total_cents: 1800, customer_id: customer.insertId };
    async function request(path, method = 'GET', body, useToken = token, useKey = key) {
      const response = await fetch(process.env.API_URL + path, { method, headers: { Authorization: `Bearer ${useToken}`, 'Content-Type': 'application/json', 'Idempotency-Key': useKey }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: response.status, data: await response.json() };
    }
    assert.equal((await request('/api/v1/sales', 'POST', input, denied)).status, 403);
    assert.equal((await request('/api/v1/sales/quote', 'POST', { items: input.items }, denied)).status, 403);
    const quote = await request('/api/v1/sales/quote', 'POST', { items: input.items });
    assert.equal(quote.status, 200);
    assert.equal(quote.data.branch_id, branch.insertId);
    assert.equal(quote.data.total_cents, 1800);
    assert.equal(quote.data.subtotal_cents, 2000);
    assert.equal((await request('/api/v1/sales/quote', 'POST', { items: [{ product_id: product.insertId, quantity: 101 }] })).data.total_cents, 90900);
    assert.equal((await request('/api/v1/sales/quote', 'POST', { items: input.items, branch_id: branch.insertId })).status, 400);
    assert.equal((await request('/api/v1/sales/quote', 'GET')).status, 404);
    const wrongIdentity = await fetch(process.env.API_URL + '/api/v1/sales', { method: 'POST', headers: {
      Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': randomBytes(32).toString('hex'),
      'X-Expected-Actor-Id': String(users[1]), 'X-Expected-Branch-Id': String(branch.insertId),
    }, body: JSON.stringify(input) });
    assert.equal(wrongIdentity.status, 403);
    assert.equal((await request('/api/v1/sales', 'POST', { ...input, expected_total_cents: 100 }, token, randomBytes(32).toString('hex'))).status, 409);
    const replies = await Promise.all([request('/api/v1/sales', 'POST', input), request('/api/v1/sales', 'POST', input)]);
    assert.deepEqual(replies.map(row => row.status).sort(), [200, 201]);
    const id = replies[0].data.id;
    assert.equal(replies[1].data.id, id);
    assert.equal(replies[0].data.status, 'SENT_TO_CASHIER');
    const [[linked]] = await db.execute('SELECT customer_id FROM sales WHERE id = ?', [id]);
    assert.equal(linked.customer_id, customer.insertId);
    const committed = await request('/api/v1/sales/retire', 'POST', {});
    assert.equal(committed.data.status, 'COMMITTED'); assert.equal(committed.data.sale.id, id);
    const retiredKey = randomBytes(32).toString('hex');
    assert.equal((await request('/api/v1/sales/retire', 'POST', {}, token, retiredKey)).data.status, 'RETIRED');
    assert.equal((await request('/api/v1/sales', 'POST', input, token, retiredKey)).data.error, 'SALE_ATTEMPT_RETIRED');
    assert.equal((await request('/api/v1/sales/retire', 'POST', {}, token, retiredKey)).data.status, 'RETIRED');
    const raceKey = randomBytes(32).toString('hex');
    const race = await Promise.all([request('/api/v1/sales/retire', 'POST', {}, token, raceKey), request('/api/v1/sales', 'POST', input, token, raceKey)]);
    if (race[0].data.status === 'RETIRED') assert.equal(race[1].data.error, 'SALE_ATTEMPT_RETIRED');
    else {
      assert.equal(race[0].data.status, 'COMMITTED'); assert.equal(race[0].data.sale.id, race[1].data.id);
      await db.execute('DELETE FROM sale_status_history WHERE sale_id = ?', [race[1].data.id]);
      await db.execute('DELETE FROM sale_items WHERE sale_id = ?', [race[1].data.id]);
      await db.execute('DELETE FROM sales WHERE id = ?', [race[1].data.id]);
    }
    assert.equal((await request('/api/v1/sales', 'POST', { ...input, customer_id: 4294967295 }, token, randomBytes(32).toString('hex'))).data.error, 'SALE_CUSTOMER_UNAVAILABLE');

    const detail = await request(`/api/v1/sales/${id}`);
    assert.equal(detail.status, 200);
    assert.equal(detail.data.items[0].list_price_cents, 1000);
    assert.equal(detail.data.items[0].unit_price_cents, 900);
    assert.equal(detail.data.items[0].line_total_cents, 1800);
    assert.equal(detail.data.items[0].promotion_id, promotion.insertId);
    assert.equal(detail.data.history.length, 2);
    assert.equal((await request(`/api/v1/sales/${id}`, 'GET', undefined, other)).status, 404);
    assert.equal((await request('/api/v1/sales')).data.items.length, 1);
    assert.equal((await request('/api/v1/sales/recover', 'POST', {})).data.id, id);
    assert.equal((await request('/api/v1/sales', 'POST', { ...input, items: [{ product_id: product.insertId, quantity: 3 }] })).status, 409);
    await db.execute('UPDATE products SET price_cents = 1200 WHERE id = ?', [product.insertId]);
    assert.equal((await request('/api/v1/sales', 'POST', input)).data.total_cents, 1800);
    // Force the second-stage insert to fail: the entire new sale must roll back.
    await db.query(`CREATE TRIGGER sales_test_failure_${suffix} BEFORE INSERT ON sale_items FOR EACH ROW SET NEW.line_total_cents = IF(NEW.product_id = ${product.insertId}, -1, NEW.line_total_cents)`);
    failureTrigger = true;
    assert.equal((await request('/api/v1/sales', 'POST', { ...input, expected_total_cents: 2200 }, token, randomBytes(32).toString('hex'))).status, 400);
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE created_by = ?', [users[0]]);
    assert.equal(Number(count.n), 1);
    const [[balance]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branch.insertId, product.insertId]);
    assert.equal(balance.quantity, '10.000');
    const [[movements]] = await db.execute('SELECT COUNT(*) AS n FROM inventory_movements WHERE branch_id = ?', [branch.insertId]);
    assert.equal(Number(movements.n), 0);
    await assert.rejects(runtime.execute('UPDATE sales SET total_cents = 1 WHERE id = ?', [id]));
    await assert.rejects(runtime.execute('DELETE FROM cashier_payments WHERE sale_id = ?', [id]));
    await db.execute('UPDATE users SET branch_id = NULL WHERE id = ?', [users[0]]);
    assert.equal((await request('/api/v1/sales/recover', 'POST', {})).status, 403);
  } finally {
    if (failureTrigger) await db.query(`DROP TRIGGER sales_test_failure_${suffix}`);
    if (branch) {
      await db.execute('DELETE h FROM sale_status_history h JOIN sales s ON s.id = h.sale_id WHERE s.branch_id = ?', [branch.insertId]);
      await db.execute('DELETE i FROM sale_items i JOIN sales s ON s.id = i.sale_id WHERE s.branch_id = ?', [branch.insertId]);
      await db.execute('DELETE FROM sales WHERE branch_id = ?', [branch.insertId]);
      await db.execute('DELETE FROM inventory WHERE branch_id = ?', [branch.insertId]);
    }
    if (customer) await db.execute('DELETE FROM customers WHERE id = ?', [customer.insertId]);
    if (branch) await db.execute('DELETE FROM sale_attempt_retirements WHERE branch_id = ?', [branch.insertId]);
    for (const id of users) {
      await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]);
      await db.execute('DELETE FROM users WHERE id = ?', [id]);
    }
    if (promotion) { await db.execute('DELETE FROM promotion_products WHERE promotion_id = ?', [promotion.insertId]); await db.execute('DELETE FROM promotions WHERE id = ?', [promotion.insertId]); }
    if (product) await db.execute('DELETE FROM products WHERE id = ?', [product.insertId]);
    if (category) await db.execute('DELETE FROM categories WHERE id = ?', [category.insertId]);
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    await runtime.end(); await db.end();
  }
});
