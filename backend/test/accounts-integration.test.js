import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Accounts tests require isolated local Compose');

test('staff accounts HTTP/SQL: creation, hierarchy, credentials, revocation and audit rollback', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [], branches = [];
  let trigger = false;
  try {
    for (const active of [true, false]) {
      const [row] = await db.execute('INSERT INTO branches(code,name,is_active) VALUES(?,?,?)', [`ACC-${suffix}-${branches.length}`, 'Demo cuentas', active]);
      branches.push(row.insertId);
    }
    async function session(id) {
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [id, sessionDigest(token)]);
      return token;
    }
    async function actor(role) {
      const [row] = await db.execute('INSERT INTO users(email,full_name,role_id,branch_id,is_active) VALUES(?,?,(SELECT id FROM roles WHERE name=?),?,1)', [`acc-${suffix}-${role}@example.invalid`, 'Demo actor', role, branches[0]]);
      users.push(row.insertId);
      return session(row.insertId);
    }
    const owner = await actor('OWNER'), admin = await actor('ADMIN'), manager = await actor('MANAGER');
    async function call(path, body, token = owner, method = 'POST') {
      const r = await fetch(process.env.API_URL + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const root = '/api/v1/admin/staff', password = 'Demo synthetic password 2026', next = 'Demo replacement password 2026';
    const input = { email: `acc-${suffix}-new@example.invalid`, password, full_name: 'Demo personal', branch_id: branches[0], role_name: 'SALES', is_active: true };
    assert.equal((await call(root, input, manager)).status, 403);
    assert.equal((await call(root, { ...input, role_name: 'OWNER' }, admin)).status, 403);
    assert.equal((await call(root, { ...input, branch_id: branches[1] })).status, 409);
    const races = await Promise.all([call(root, input, admin), call(root, { ...input, email: input.email.toUpperCase() }, admin)]);
    assert.deepEqual(races.map(r => r.status).sort(), [201, 409]);
    const created = races.find(r => r.status === 201).data;
    const id = created.staff.id; users.push(id);
    assert.equal(Object.hasOwn(created.staff, 'email'), false);
    assert.equal(Object.hasOwn(created.staff, 'password_hash'), false);
    const [[stored]] = await db.execute('SELECT password_hash FROM users WHERE id=?', [id]);
    assert.ok(stored.password_hash.startsWith('scrypt$'));
    assert.ok(!stored.password_hash.includes(password));
    const login = pwd => call('/api/v1/auth/login', { email: input.email, password: pwd });
    assert.equal((await login('Incorrect synthetic password')).status, 401);
    const signed = await login(password);
    assert.equal(signed.status, 200);
    const token = signed.data.access_token;
    assert.equal((await call('/api/v1/auth/me', undefined, token, 'GET')).status, 200);
    assert.equal((await call(`${root}/${users[0]}/password`, { password: next }, admin)).status, 403);
    assert.equal((await call(`${root}/${id}/password`, { password: next }, manager)).status, 403);
    await db.query(`CREATE TRIGGER accounts_fail_${suffix} BEFORE INSERT ON administration_changes FOR EACH ROW SET NEW.new_value=IF(NEW.actor_id=${users[1]} AND NEW.action IN ('USER_CREATE','USER_PASSWORD'),NULL,NEW.new_value)`);
    trigger = true;
    assert.equal((await call(`${root}/${id}/password`, { password: next }, admin)).status, 503);
    assert.equal((await call('/api/v1/auth/me', undefined, token, 'GET')).status, 200);
    const failedEmail = `acc-${suffix}-failed@example.invalid`;
    assert.equal((await call(root, { ...input, email: failedEmail }, admin)).status, 503);
    const [[absent]] = await db.execute('SELECT COUNT(*) AS n FROM users WHERE email=?', [failedEmail]);
    assert.equal(Number(absent.n), 0);
    await db.query(`DROP TRIGGER accounts_fail_${suffix}`); trigger = false;
    assert.equal((await call(`${root}/${id}/password`, { password: next }, admin)).status, 200);
    assert.equal((await call('/api/v1/auth/me', undefined, token, 'GET')).status, 401);
    assert.equal((await login(password)).status, 401);
    assert.equal((await login(next)).status, 200);
    const inactive = await call(root, { ...input, email: `acc-${suffix}-inactive@example.invalid`, is_active: false });
    assert.equal(inactive.status, 201); users.push(inactive.data.staff.id);
    assert.equal((await call(`${root}/${inactive.data.staff.id}/password`, { password: next })).status, 200);
    assert.equal((await call('/api/v1/auth/login', { email: `acc-${suffix}-inactive@example.invalid`, password: next })).status, 401);
    const [audits] = await db.execute('SELECT action,previous_value,new_value FROM administration_changes WHERE user_id=? ORDER BY id', [id]);
    assert.deepEqual(audits.map(a => a.action), ['USER_CREATE', 'USER_PASSWORD']);
    assert.ok(!JSON.stringify(audits).includes('password_hash'));
    assert.ok(!JSON.stringify(audits).includes(password));
    assert.ok(!JSON.stringify(audits).includes(input.email));
  } finally {
    if (trigger) await db.query(`DROP TRIGGER accounts_fail_${suffix}`);
    for (const id of users) await db.execute('DELETE FROM administration_changes WHERE actor_id=? OR user_id=?', [id, id]);
    for (const id of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id=?', [id]); await db.execute('DELETE FROM users WHERE id=?', [id]); }
    for (const id of branches) await db.execute('DELETE FROM branches WHERE id=?', [id]);
    await db.end();
  }
});
