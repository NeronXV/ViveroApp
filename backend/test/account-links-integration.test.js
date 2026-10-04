import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { createApp } from '../src/app.js';
import { sessionDigest } from '../src/auth/service.js';
import { hashPassword } from '../src/auth/password.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Account link tests require isolated local Compose');
test('account links HTTP/MariaDB: permissions, single use, expiry, revocation, resend and disabled mail', async () => {
  const config = { host: 'db', database: 'vivero', password: process.env.DB_PASSWORD, user: 'root' };
  const root = await mysql.createConnection(config);
  const runtime = mysql.createPool({ ...config, user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD, connectionLimit: 5 });
  const suffix = randomBytes(6).toString('hex'), ids = [], emails = [], messages = [];
  let failMail = false, trigger = false;
  const server = createApp({ db: runtime, webOrigin: 'http://localhost', accountMailer: async message => { messages.push(message); if (failMail) throw new Error('Synthetic mail failure'); } });
  const disabled = createApp({ db: runtime });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  await new Promise(resolve => disabled.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const password = 'Synthetic initial password 2026', next = '  Synthetic new password 2026  ';
  const hash = await hashPassword(password);
  async function actor(role) {
    const email = `link-${suffix}-${role}@example.invalid`; emails.push(email);
    const [row] = await root.execute('INSERT INTO users(email,full_name,password_hash,role_id,is_active) VALUES(?,?,?,(SELECT id FROM roles WHERE name=?),1)', [email, 'Demo enlaces', hash, role]);
    ids.push(row.insertId);
    const token = randomBytes(32).toString('base64url');
    await root.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
    return { id: row.insertId, email, token };
  }
  async function call(path, body, token, method = 'POST', target = base) {
    const response = await fetch(target + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, data: await response.json() };
  }
  try {
    const owner = await actor('OWNER'), sales = await actor('SALES');
    const invitation = { email: `link-${suffix}-invite@example.invalid`, name: 'Demo invitado' }; emails.push(invitation.email);
    const invite = '/api/v1/admin/staff/invitations', recovery = '/api/v1/auth/recovery', complete = '/api/v1/auth/password';
    assert.equal((await call(invite, invitation)).status, 401);
    assert.equal((await call(invite, invitation, sales.token)).status, 403);
    assert.equal((await call(invite, invitation, owner.token)).status, 200);
    const first = messages.at(-1).token;
    const [[invited]] = await root.execute('SELECT * FROM users WHERE email=?', [invitation.email]); ids.push(invited.id);
    assert.equal(invited.role_id, null); assert.equal(invited.branch_id, null); assert.equal(invited.password_hash, null);
    assert.equal((await call(invite, invitation, owner.token)).status, 200);
    assert.equal((await call(complete, { token: first, password: next })).status, 400);
    const token = messages.at(-1).token;
    const [[stored]] = await root.execute('SELECT token_hash FROM account_links WHERE user_id=?', [invited.id]);
    assert.ok(stored.token_hash.equals(sessionDigest(token)));
    assert.equal((await call(complete, { token, password: 'short' })).status, 400);
    assert.equal((await call(complete, { token, password: next })).status, 200);
    assert.equal((await call(complete, { token, password: next })).status, 400);
    assert.equal((await call(invite, invitation, owner.token)).status, 409);
    const login = await call('/api/v1/auth/login', { email: invitation.email, password: next });
    assert.equal(login.status, 200);
    assert.equal((await call('/api/v1/auth/me', undefined, login.data.access_token, 'GET')).data.access_state, 'NO_ROLE');
    const reset = () => call(recovery, { email: invitation.email });
    assert.deepEqual((await reset()).data, { accepted: true });
    const unknown = `link-${suffix}-missing@example.invalid`; emails.push(unknown);
    const count = messages.length;
    assert.deepEqual(await call(recovery, { email: unknown }), { status: 202, data: { accepted: true } });
    assert.equal(messages.length, count);
    const recoveryToken = messages.at(-1).token;
    await root.query(`CREATE TRIGGER link_fail_${suffix} BEFORE UPDATE ON users FOR EACH ROW SET NEW.full_name=IF(NEW.id=${invited.id},NULL,NEW.full_name)`); trigger = true;
    assert.equal((await call(complete, { token: recoveryToken, password })).status, 503);
    assert.equal((await call('/api/v1/auth/me', undefined, login.data.access_token, 'GET')).status, 200);
    await root.query(`DROP TRIGGER link_fail_${suffix}`); trigger = false;
    const results = await Promise.all([call(complete, { token: recoveryToken, password }), call(complete, { token: recoveryToken, password })]);
    assert.deepEqual(results.map(r => r.status).sort(), [200, 400]);
    assert.equal((await call('/api/v1/auth/me', undefined, login.data.access_token, 'GET')).status, 401);
    await reset(); const expired = messages.at(-1).token;
    await root.execute('UPDATE account_links SET expires_at=UTC_TIMESTAMP(6) WHERE user_id=?', [invited.id]);
    assert.equal((await call(complete, { token: expired, password: next })).status, 400);
    await reset(); const invalidated = messages.at(-1).token;
    await root.execute('UPDATE users SET is_active=0 WHERE id=?', [invited.id]);
    await root.execute('UPDATE users SET is_active=1 WHERE id=?', [invited.id]);
    assert.equal((await call(complete, { token: invalidated, password: next })).status, 400);
    await reset(); const adminInvalidated = messages.at(-1).token;
    await root.execute('UPDATE users SET password_hash=? WHERE id=?', [await hashPassword(next), invited.id]);
    assert.equal((await call(complete, { token: adminInvalidated, password })).status, 400);
    failMail = true;
    assert.deepEqual(await reset(), { status: 202, data: { accepted: true } });
    const retry = { email: `link-${suffix}-retry@example.invalid`, name: 'Demo reenvío' }; emails.push(retry.email);
    assert.equal((await call(invite, retry, owner.token)).status, 503);
    const [[pending]] = await root.execute('SELECT id FROM users WHERE email=?', [retry.email]); ids.push(pending.id);
    failMail = false; assert.equal((await call(invite, retry, owner.token)).status, 200);
    const [[duplicates]] = await root.execute('SELECT COUNT(*) n FROM users WHERE email=?', [retry.email]); assert.equal(Number(duplicates.n), 1);
    assert.equal((await call(recovery, { email: unknown }, undefined, 'POST', `http://127.0.0.1:${disabled.address().port}`)).status, 503);
    for (let i = 0; i < 10; i++) await call(recovery, { email: unknown });
    assert.equal((await call(recovery, { email: unknown })).status, 429);
    assert.equal((await call(complete, { token: 'x'.repeat(43), password: next })).status, 400);
    assert.equal((await call(recovery + '?token=hidden', { email: unknown })).status, 400);
  } finally {
    if (trigger) await root.query(`DROP TRIGGER link_fail_${suffix}`);
    await Promise.all([new Promise(resolve => server.close(resolve)), new Promise(resolve => disabled.close(resolve))]);
    await runtime.end();
    for (const id of ids) await root.execute('DELETE FROM administration_changes WHERE actor_id=? OR user_id=?', [id, id]);
    for (const id of ids) { await root.execute('DELETE FROM auth_sessions WHERE user_id=?', [id]); await root.execute('DELETE FROM users WHERE id=?', [id]); }
    for (const email of emails) for (const key of [`email:${email}`, `email:recovery:${email}`]) await root.execute('DELETE FROM auth_login_limits WHERE key_hash=?', [sessionDigest(key)]);
    await root.execute('DELETE FROM auth_login_limits WHERE key_hash=?', [sessionDigest('ip:recovery:127.0.0.1')]);
    await root.end();
  }
});
