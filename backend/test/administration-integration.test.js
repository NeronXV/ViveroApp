import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Administration tests require isolated local Compose');
test('administration HTTP/SQL: hierarchy, branch safety, session revocation, audit rollback and concurrent owners', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], branches = [];
  let saleId, orderId, trigger = false;
  try {
    const [[baseline]] = await db.execute("SELECT COUNT(*) AS n FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'OWNER' AND u.is_active = 1");
    for (let i = 0; i < 3; i++) {
      const [row] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`ADM-${suffix}-${i}`, 'Demo sucursal']);
      branches.push(row.insertId);
    }
    async function session(id) {
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [id, sessionDigest(token)]);
      return token;
    }
    async function user(role, active = true) {
      const [row] = await db.execute('INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, ?, (SELECT id FROM roles WHERE name = ?), ?, ?)', [`adm-${suffix}-${users.length}@example.invalid`, `Demo ${suffix} ${users.length}`, role, branches[0], active]);
      users.push(row.insertId);
      return session(row.insertId);
    }
    let owner = await user('OWNER'), owner2 = await user('OWNER');
    const admin = await user('ADMIN'), manager = await user('MANAGER'), staff = await user('SALES'), inactive = await user('SALES', false);
    async function call(path, method = 'GET', body, token = owner) {
      const r = await fetch(process.env.API_URL + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const root = '/api/v1/admin', staffPath = id => `${root}/staff/${id}`;
    assert.equal((await call(root + '/staff', 'GET', undefined, manager)).status, 403);
    assert.equal((await call(root + '/branches', 'POST', { code: 'DENIED', name: 'Demo' }, manager)).status, 403);
    assert.equal((await call(root + '/roles', 'GET', undefined, admin)).data.items.some(r => r.name === 'OWNER'), false);
    assert.equal((await call(root + '/roles')).data.items.some(r => r.name === 'OWNER'), true);
    const list = (await call(`${root}/staff?branch_id=${branches[0]}&include_inactive=true&limit=2`)).data;
    assert.equal(list.items.length, 2);
    assert.ok(list.next_after_id);
    assert.equal(Object.hasOwn(list.items[0], 'email'), false);
    assert.equal(Object.hasOwn(list.items[0], 'password_hash'), false);
    assert.equal((await call(`${root}/staff?branch_id=${branches[0]}&after_id=${list.next_after_id}&include_inactive=true&limit=2`)).data.items[0].id, users[2]);
    for (const action of ['role', 'active', 'branch']) {
      const body = action === 'role' ? { role_name: 'SALES' } : action === 'active' ? { is_active: false } : { branch_id: branches[1] };
      assert.equal((await call(staffPath(users[0]) + '/' + action, 'PATCH', body, admin)).status, 403);
    }
    assert.equal((await call(staffPath(users[4]) + '/role', 'PATCH', { role_name: 'OWNER' }, admin)).status, 403);
    assert.equal((await call(staffPath(users[5]) + '/role', 'PATCH', { role_name: 'CASHIER' })).status, 409);
    const created = await call(root + '/branches', 'POST', { code: ` new-${suffix} `, name: ' Nueva   demo ' });
    assert.equal(created.status, 201); branches.push(created.data.branch.id);
    assert.equal(created.data.branch.code, `NEW-${suffix.toUpperCase()}`);
    assert.equal(created.data.branch.name, 'Nueva demo');
    assert.equal((await call(root + '/branches', 'POST', { code: `NEW-${suffix}`, name: 'Duplicada' })).status, 409);
    assert.equal((await call(`${root}/branches/${branches[1]}`, 'PATCH', { code: `EDIT-${suffix}`, name: 'Editada demo' })).status, 200);
    assert.equal((await call(`${root}/branches/${branches[0]}/active`, 'PATCH', { is_active: false })).data.error, 'BRANCH_HAS_OPERATIONS');
    assert.equal((await call(`${root}/branches/${branches[1]}/active`, 'PATCH', { is_active: false })).status, 200);
    assert.equal((await call(staffPath(users[4]) + '/branch', 'PATCH', { branch_id: branches[1] })).data.error, 'BRANCH_INACTIVE');
    assert.equal((await call(`${root}/branches/${branches[1]}/active`, 'PATCH', { is_active: true })).status, 200);
    const changedBranch = await call(staffPath(users[4]) + '/branch', 'PATCH', { branch_id: branches[1] });
    assert.equal(changedBranch.status, 200);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, staff)).status, 401);
    let fresh = await session(users[4]);
    assert.equal((await call(staffPath(users[4]) + '/role', 'PATCH', { role_name: 'CASHIER' }, admin)).status, 200);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, fresh)).status, 401);
    fresh = await session(users[4]);
    assert.equal((await call(staffPath(users[4]) + '/role', 'PATCH', { role_name: 'CASHIER' }, admin)).data.idempotent_replay, true);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, fresh)).status, 200);
    assert.equal((await call(staffPath(users[4]) + '/active', 'PATCH', { is_active: false }, admin)).status, 200);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, fresh)).status, 401);
    assert.equal((await call(staffPath(users[4]) + '/active', 'PATCH', { is_active: true }, admin)).status, 200);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, fresh)).status, 401);
    const [pending] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, 'SENT_TO_CASHIER', 100, 100)", [`ADM-${suffix}`, branches[2], users[0]]);
    saleId = pending.insertId;
    assert.equal((await call(`${root}/branches/${branches[2]}/active`, 'PATCH', { is_active: false })).data.error, 'BRANCH_HAS_OPERATIONS');
    await db.execute('DELETE FROM sales WHERE id = ?', [saleId]); saleId = null;
    const [order] = await db.execute("INSERT INTO web_orders (branch_id, idempotency_hash, request_hash, customer_name, customer_email, subtotal_cents, discount_cents, total_cents) VALUES (?, ?, ?, 'Demo pedido', 'demo@example.invalid', 100, 0, 100)", [branches[2], randomBytes(32), randomBytes(32)]);
    orderId = order.insertId;
    assert.equal((await call(`${root}/branches/${branches[2]}/active`, 'PATCH', { is_active: false })).data.error, 'BRANCH_HAS_OPERATIONS');
    await db.execute('DELETE FROM web_orders WHERE id = ?', [orderId]); orderId = null;
    // Late audit failure rolls back assignment and token revocation together.
    const rollbackToken = await session(users[4]);
    await db.query(`CREATE TRIGGER admin_audit_failure_${suffix} BEFORE INSERT ON administration_changes FOR EACH ROW SET NEW.new_value = IF(NEW.user_id = ${users[4]}, NULL, NEW.new_value)`);
    trigger = true;
    assert.equal((await call(staffPath(users[4]) + '/role', 'PATCH', { role_name: 'SALES' }, admin)).status, 503);
    assert.equal((await call('/api/v1/auth/me', 'GET', undefined, rollbackToken)).data.role.name, 'CASHIER');
    await db.query(`DROP TRIGGER admin_audit_failure_${suffix}`); trigger = false;
    assert.equal((await call(staffPath(users[4]) + '/role', 'PATCH', { role_name: 'SALES' }, admin)).status, 200);
    const [[audit]] = await db.execute("SELECT COUNT(*) AS n FROM administration_changes WHERE actor_id = ? AND action = 'USER_ROLE' AND user_id = ?", [users[2], users[4]]);
    assert.equal(Number(audit.n), 2); // failed attempt did not persist
    const races = await Promise.all([
      call(staffPath(users[0]) + '/role', 'PATCH', { role_name: 'SALES' }, owner),
      call(staffPath(users[1]) + '/role', 'PATCH', { role_name: 'SALES' }, owner2),
    ]);
    if (Number(baseline.n) === 0) {
      assert.deepEqual(races.map(r => r.status).sort(), [200, 409]);
      const survivor = races[0].status === 409 ? 0 : 1;
      const token = survivor === 0 ? owner : owner2;
      assert.equal((await call(staffPath(users[survivor]) + '/active', 'PATCH', { is_active: false }, token)).data.error, 'ROLE_LAST_OWNER_REQUIRED');
    } else assert.ok(races.every(r => [200, 409].includes(r.status)));
    const [[owners]] = await db.execute("SELECT COUNT(*) AS n FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'OWNER' AND u.is_active = 1");
    assert.ok(Number(owners.n) >= 1);
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try {
      await assert.rejects(runtime.execute('UPDATE users SET full_name = full_name WHERE id = ?', [users[4]]), e => e.code === 'ER_COLUMNACCESS_DENIED_ERROR');
      await assert.rejects(runtime.execute('UPDATE users SET email = ? WHERE id = ?', ['changed@example.invalid', users[4]]), e => e.code === 'ER_COLUMNACCESS_DENIED_ERROR');
      await assert.rejects(runtime.query('DELETE FROM administration_changes WHERE 1 = 0'), e => e.code === 'ER_TABLEACCESS_DENIED_ERROR');
    } finally { await runtime.end(); }
  } finally {
    if (trigger) await db.query(`DROP TRIGGER admin_audit_failure_${suffix}`);
    if (saleId) await db.execute('DELETE FROM sales WHERE id = ?', [saleId]);
    if (orderId) await db.execute('DELETE FROM web_orders WHERE id = ?', [orderId]);
    for (const id of users) await db.execute('DELETE FROM administration_changes WHERE actor_id = ? OR user_id = ?', [id, id]);
    for (const id of branches) await db.execute('DELETE FROM administration_changes WHERE branch_id = ?', [id]);
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]); await db.execute('DELETE FROM users WHERE id = ?', [id]); }
    for (const id of branches) await db.execute('DELETE FROM branches WHERE id = ?', [id]);
    await db.end();
  }
});
