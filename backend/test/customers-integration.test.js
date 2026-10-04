import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Customer tests require isolated local Compose');
test('customers HTTP/SQL: global search, permissions, email uniqueness, updates and soft deletion', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], customerIds = [];
  let branch;
  try {
    [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`CUST-${suffix}`, 'Demo clientes']);
    async function user(role, branchId = branch.insertId) {
      const token = randomBytes(32).toString('base64url');
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, 1)', [`cust-${suffix}-${users.length}@example.invalid`, 'Demo', role, branchId]);
      users.push(row.insertId);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const seller = await user('SALES'), admin = await user('ADMIN'), cashier = await user('CASHIER'), elsewhere = await user('SALES', 1);
    async function call(path = '', method = 'GET', body, token = seller) {
      const r = await fetch(process.env.API_URL + '/api/v1/customers' + path, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
      const data = await r.json();
      if (method === 'POST' && r.status === 201) customerIds.push(data.customer.id);
      return { status: r.status, data };
    }
    const input = { full_name: `${suffix} Alpha_%`, email: `${suffix}@example.invalid`, phone: '12345678', is_active: true };
    assert.equal((await call('?search=Demo', 'GET', undefined, null)).status, 401);
    assert.equal((await call('?search=Demo', 'GET', undefined, cashier)).status, 403);
    assert.equal((await call('', 'POST', input, cashier)).status, 403);
    const race = await Promise.all([call('', 'POST', { ...input, email: input.email.toUpperCase() }), call('', 'POST', input)]);
    assert.deepEqual(race.map(r => r.status).sort(), [201, 409]);
    const customer = race.find(r => r.status === 201).data.customer;
    assert.equal(customer.email, input.email);
    assert.equal((await call('?search=' + encodeURIComponent(input.email.toUpperCase()))).data.items[0].id, customer.id);
    assert.ok(Number.isInteger(customer.id));
    assert.equal((await call(`?search=${suffix}`, 'GET', undefined, elsewhere)).data.items[0].id, customer.id);
    assert.equal((await call('?search=' + encodeURIComponent('_%'))).data.items.filter(r => r.id === customer.id).length, 1);
    assert.deepEqual((await call('?search=' + encodeURIComponent("' OR 1=1 --"))).data.items, []);
    assert.equal((await call('?search=x')).status, 400);
    assert.equal((await call(`?search=${suffix}&limit=51`)).status, 400);
    assert.equal((await call(`/${customer.id}`, 'PATCH', input)).status, 403);
    assert.equal((await call(`/${customer.id}`, 'DELETE')).status, 403);
    const second = await call('', 'POST', { ...input, full_name: `${suffix} Beta`, email: null, phone: null });
    assert.equal(second.status, 201);
    assert.equal((await call(`?search=${suffix}&limit=1`)).data.items.length, 1);
    const changed = { ...input, full_name: `${suffix} Zeta`, phone: '87654321' };
    assert.equal((await call(`/${customer.id}`, 'PATCH', changed, admin)).status, 200);
    assert.equal((await call(`/${customer.id}`)).data.customer.phone, changed.phone);
    // Unique conflict must roll back the whole edit, including its other fields.
    assert.equal((await call(`/${second.data.customer.id}`, 'PATCH', { ...changed, full_name: 'Should roll back' }, admin)).status, 409);
    assert.equal((await call(`/${second.data.customer.id}`)).data.customer.full_name, `${suffix} Beta`);
    assert.equal((await call(`/${customer.id}`, 'DELETE', undefined, admin)).status, 200);
    assert.equal((await call(`/${customer.id}`)).status, 404);
    assert.equal((await call(`/${customer.id}`, 'GET', undefined, admin)).data.customer.is_active, false);
    assert.equal((await call(`?search=${suffix}`)).data.items.length, 1);
    assert.equal((await call(`/${customer.id}`, 'PATCH', changed, admin)).status, 200);
    assert.equal((await call(`?search=${suffix}`)).data.items.length, 2);
    assert.equal((await call('/4294967295', 'PATCH', input, admin)).status, 404);
    const [[audit]] = await db.execute('SELECT created_by, updated_by FROM customers WHERE id = ?', [customer.id]);
    assert.equal(audit.created_by, users[0]);
    assert.equal(audit.updated_by, users[1]);
    await assert.rejects(db.execute('UPDATE customers SET created_by = 4294967295 WHERE id = ?', [customer.id]), e => e.code === 'ER_NO_REFERENCED_ROW_2');
    await assert.rejects(db.execute('UPDATE customers SET email = ? WHERE id = ?', ['UPPER@EXAMPLE.INVALID', customer.id]), e => e.errno === 4025);
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try {
      await assert.rejects(runtime.execute('DELETE FROM customers WHERE id = ?', [customer.id]), e => e.code === 'ER_TABLEACCESS_DENIED_ERROR');
      await assert.rejects(runtime.execute('UPDATE customers SET created_by = ? WHERE id = ?', [users[1], customer.id]), e => e.code === 'ER_COLUMNACCESS_DENIED_ERROR');
    } finally { await runtime.end(); }
  } finally {
    for (const id of customerIds) await db.execute('DELETE FROM customers WHERE id = ?', [id]);
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]); await db.execute('DELETE FROM users WHERE id = ?', [id]); }
    if (branch) await db.execute('DELETE FROM branches WHERE id = ?', [branch.insertId]);
    await db.end();
  }
});
