// Synthetic fixtures only, using the same isolated local Compose test profile.
import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { createInventory, inventoryBody } from '../src/inventory.js';
import { sessionDigest, transaction } from '../src/auth/service.js';

if (process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero' || process.env.API_URL !== 'http://api:3001') {
  throw new Error('Inventory integration requires the isolated local Compose test profile');
}

test('MariaDB inventory: runtime grants, signed trigger, replay, zero count and parallel receptions', async () => {
  const admin = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const runtime = mysql.createPool({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD, connectionLimit: 4 });
  const suffix = randomBytes(8).toString('hex');
  let branch, category, product, actor;
  try {
    assert.equal((await fetch(process.env.API_URL + '/health')).status, 200);
    [branch] = await admin.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`INV-${suffix}`, 'Sucursal sintetica']);
    [category] = await admin.execute('INSERT INTO categories (name) VALUES (?)', [`INV-${suffix}`]);
    [product] = await admin.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 100)', [`INV-${suffix}`, 'Planta sintetica', category.insertId]);
    [actor] = await admin.execute('INSERT INTO users (email, full_name, branch_id, role_id, is_active) VALUES (?, ?, ?, (SELECT id FROM roles WHERE name = ?), 1)', [`inv-${suffix}@example.invalid`, 'Persona sintetica', branch.insertId, 'INVENTORY']);
    const context = { access_state: 'ACTIVE', user: { id: actor.insertId }, branch: { id: branch.insertId, is_active: true }, capabilities: ['MANAGE_INVENTORY'] };
    const reception = inventoryBody('reception', { product_id: product.insertId, quantity: '5', notes: null }, `reception-${suffix}`);
    const receive = input => transaction(runtime, db => createInventory(db, context).reception(input));
    const count = (quantity, key) => transaction(runtime, db => createInventory(db, context).reconcile(inventoryBody('count', { product_id: product.insertId, counted_quantity: quantity, reason: 'Conteo sintetico' }, key)));
    const first = await receive(reception);
    assert.equal(first.total_quantity, '5.000');
    assert.equal((await receive(reception)).idempotent_replay, true);
    const adjusted = await count('2', `count-${suffix}`);
    assert.equal(adjusted.adjustment_quantity, '-3.000');
    assert.equal((await count('2', `count-${suffix}`)).idempotent_replay, true);
    assert.equal((await count('2', `zero-${suffix}`)).adjustment_quantity, '0.000');
    const parallel = await Promise.all([receive(reception), receive(reception)]);
    assert.ok(parallel.every(value => value.idempotent_replay));
    await assert.rejects(receive({ ...reception, quantity: '6.000', quantityMilli: 6000n }), { code: 'INVENTORY_IDEMPOTENCY_CONFLICT' });
    const [[balance]] = await admin.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branch.insertId, product.insertId]);
    assert.equal(balance.quantity, '2.000');
    const [[ledger]] = await admin.execute('SELECT COUNT(*) AS n, SUM(quantity) AS quantity FROM inventory_movements WHERE branch_id = ?', [branch.insertId]);
    assert.equal(Number(ledger.n), 2);
    assert.equal(ledger.quantity, '2.000');
    await assert.rejects(runtime.execute('UPDATE inventory SET quantity = 99 WHERE branch_id = ?', [branch.insertId]));
    await assert.rejects(admin.execute('INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity) VALUES (?, ?, ?, ?)', [branch.insertId, product.insertId, 'OPENING', '-1.000']));
    const dashboard = await transaction(runtime, db => createInventory(db, context).dashboard({ limit: 100, afterProductId: 0 }));
    assert.equal(dashboard.items.find(row => row.product_id === product.insertId).total_quantity, '2.000');
    const token = randomBytes(32).toString('base64url');
    await admin.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [actor.insertId, sessionDigest(token)]);
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
    const response = await fetch(process.env.API_URL + '/api/v1/inventory/dashboard', { headers });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).branch_id, branch.insertId);
    const history = await fetch(process.env.API_URL + `/api/v1/inventory/history?product_id=${product.insertId}`, { headers });
    assert.equal(history.status, 200);
    assert.equal((await history.json()).items[0].quantity, '-3.000');
    const httpCount = await fetch(process.env.API_URL + '/api/v1/inventory/counts', { method: 'POST', headers: { ...headers, 'Idempotency-Key': `count-${suffix}` }, body: JSON.stringify({ product_id: product.insertId, counted_quantity: '2', reason: 'Conteo sintetico' }) });
    assert.equal(httpCount.status, 200);
    assert.equal((await httpCount.json()).idempotent_replay, true);
    // Stress the duplicate-key lock path that previously deadlocked before
    // SELECT FOR UPDATE. Each concurrent pair creates exactly one reception.
    for (let i = 0; i < 12; i++) {
      const input = inventoryBody('reception', { product_id: product.insertId, quantity: '1', notes: null }, `stress-${suffix}-${i}`);
      const pair = await Promise.all([receive(input), receive(input)]);
      assert.deepEqual(pair.map(row => row.idempotent_replay).sort(), [false, true]);
    }
    const [[stressed]] = await admin.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branch.insertId, product.insertId]);
    assert.equal(stressed.quantity, '14.000');
    // Read recovery must survive product deactivation and never create stock.
    await admin.execute('UPDATE products SET is_active = 0 WHERE id = ?', [product.insertId]);
    const recover = (operation, body, key, extra = {}) => fetch(process.env.API_URL + `/api/v1/inventory/${operation}/result`, {
      method: 'POST', headers: { ...headers, 'Idempotency-Key': key, ...extra }, body: JSON.stringify(body),
    });
    const receptionBody = { product_id: product.insertId, quantity: '5', notes: null };
    const recovered = await recover('receptions', receptionBody, `reception-${suffix}`);
    assert.equal(recovered.status, 200);
    assert.equal((await recovered.json()).movement_id, first.movement_id);
    const recoveredCount = await recover('counts', { product_id: product.insertId, counted_quantity: '2', reason: 'Conteo sintetico' }, `count-${suffix}`);
    assert.equal(recoveredCount.status, 200);
    assert.equal((await recoveredCount.json()).total_quantity, '2.000');
    const missing = await recover('receptions', receptionBody, `missing-${suffix}`);
    assert.equal(missing.status, 404);
    assert.deepEqual(await missing.json(), { error: 'INVENTORY_OPERATION_NOT_FOUND' });
    await assert.rejects(transaction(runtime, db => createInventory(db, { ...context, user: { id: actor.insertId + 1 } }).result(reception, 'receptions')), { code: 'INVENTORY_IDEMPOTENCY_CONFLICT' });
    await assert.rejects(transaction(runtime, db => createInventory(db, { ...context, capabilities: ['VIEW_INVENTORY_ALERTS'] }).result(reception, 'receptions')), { code: 'INVENTORY_UNAUTHORIZED' });
    assert.equal((await recover('receptions', { ...receptionBody, quantity: '6' }, `reception-${suffix}`)).status, 409);
    assert.equal((await recover('receptions', receptionBody, `reception-${suffix}`, { 'X-Expected-Branch-Id': String(branch.insertId + 1) })).status, 403);
    const [[unchanged]] = await admin.execute('SELECT quantity FROM inventory WHERE branch_id = ? AND product_id = ?', [branch.insertId, product.insertId]);
    assert.equal(unchanged.quantity, '14.000');
    await admin.execute('UPDATE users SET branch_id = NULL WHERE id = ?', [actor.insertId]);
    assert.equal((await fetch(process.env.API_URL + '/api/v1/inventory/dashboard', { headers })).status, 403);
  } finally {
    await runtime.end();
    // Delete only this test's branch fixtures, in dependency order.
    if (branch) {
      await admin.execute('DELETE FROM inventory_counts WHERE branch_id = ?', [branch.insertId]);
      await admin.execute('DELETE FROM inventory_movements WHERE branch_id = ?', [branch.insertId]);
      await admin.execute('DELETE FROM inventory WHERE branch_id = ?', [branch.insertId]);
    }
    if (actor) {
      await admin.execute('DELETE FROM auth_sessions WHERE user_id = ?', [actor.insertId]);
      await admin.execute('DELETE FROM users WHERE id = ?', [actor.insertId]);
    }
    if (product) await admin.execute('DELETE FROM products WHERE id = ?', [product.insertId]);
    if (category) await admin.execute('DELETE FROM categories WHERE id = ?', [category.insertId]);
    if (branch) await admin.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    await admin.end();
  }
});
