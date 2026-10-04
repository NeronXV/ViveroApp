import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomBytes } from 'node:crypto';
import { hashPassword, verifyPassword, validateCredentials } from '../src/auth/password.js';
import { accessContext, bearerToken, requireCapabilities, sessionDigest, transaction } from '../src/auth/service.js';
import { createApp } from '../src/app.js';

const actor = {
  id: 1, email: 'synthetic@example.invalid', full_name: 'Prueba',
  role_id: 3, role_name: 'INVENTORY', role_display_name: 'Inventario',
  branch_id: 1, branch_code: 'DEMO', branch_name: 'Demo', branch_active: 1,
};

test('passwords use salted asynchronous scrypt and reject wrong/missing/malformed hashes', async () => {
  const password = `Synthetic-${randomBytes(16).toString('hex')}`;
  const first = await hashPassword(password);
  const second = await hashPassword(password);
  assert.notEqual(first, second);
  assert.ok(await verifyPassword(password, first));
  assert.equal(await verifyPassword(password + ' ', first), false);
  assert.equal(await verifyPassword(password, null), false);
  assert.equal(await verifyPassword(password, first.replace('131072', '999999999999')), false);
});

test('credentials are bounded and cannot assign a role or normalize a password', () => {
  const value = validateCredentials({ email: ' Demo@Example.invalid ', password: ' password with spaces ' });
  assert.equal(value.email, 'demo@example.invalid');
  assert.equal(value.password, ' password with spaces ');
  for (const input of [null, [], { email: 'x', password: 'x' },
    { email: 'x@example.invalid', password: 'x'.repeat(129) },
    { email: 'x@example.invalid', password: 'valid-password-length', role_id: 6 }]) {
    assert.throws(() => validateCredentials(input));
  }
  assert.throws(() => validateCredentials({ email: 'x@example.invalid', password: 'short' }, true));
});

test('access is capability-based; branch operations have no owner bypass', () => {
  const context = accessContext(actor, ['MANAGE_PRODUCTS']);
  requireCapabilities(context, ['MANAGE_PRODUCTS']);
  assert.throws(() => requireCapabilities(context, ['MANAGE_PRICES']), { code: 'FORBIDDEN' });
  requireCapabilities(context, ['MANAGE_PRODUCTS'], 1);
  assert.throws(() => requireCapabilities(context, ['MANAGE_PRODUCTS'], 2), { code: 'BRANCH_FORBIDDEN' });
  const owner = accessContext({ ...actor, role_name: 'OWNER', branch_active: 0 }, ['MANAGE_PRODUCTS']);
  assert.throws(() => requireCapabilities(owner, ['MANAGE_PRODUCTS'], 1), { code: 'BRANCH_FORBIDDEN' });
  const noRole = accessContext({ ...actor, role_id: null }, ['MANAGE_PRODUCTS']);
  assert.deepEqual(noRole.capabilities, []);
  assert.throws(() => requireCapabilities(noRole, ['MANAGE_PRODUCTS']), { code: 'FORBIDDEN' });
  const noBranch = accessContext({ ...actor, branch_id: null }, ['MANAGE_PRODUCTS']);
  requireCapabilities(noBranch, ['MANAGE_PRODUCTS']); // Catalog is global, as before.
  assert.throws(() => requireCapabilities(noBranch, ['MANAGE_PRODUCTS'], 1), { code: 'BRANCH_FORBIDDEN' });
});

test('opaque sessions have strict syntax and only a digest is stored', () => {
  const token = randomBytes(32).toString('base64url');
  assert.equal(bearerToken({ headers: { authorization: `Bearer ${token}` } }), token);
  assert.equal(sessionDigest(token).length, 32);
  assert.notEqual(sessionDigest(token).toString('hex'), token);
  for (const authorization of ['', 'Bearer old-shared-token', `Bearer ${token}extra`, 'Basic x', 'Bearer a.b.c']) {
    assert.throws(() => bearerToken({ headers: { authorization } }), { status: 401 });
  }
});

test('failed authorization/operation rolls back and releases its connection', async () => {
  const events = [];
  const db = { getConnection: async () => ({
    beginTransaction: async () => events.push('begin'), commit: async () => events.push('commit'),
    rollback: async () => events.push('rollback'), release: () => events.push('release'),
  }) };
  await assert.rejects(transaction(db, async () => { throw new Error('denied'); }));
  assert.deepEqual(events, ['begin', 'rollback', 'release']);
});

test('administration lock surrounds commit or rollback and is released before pooling', async () => {
  for (const failed of [false, true]) {
    const events = [];
    const db = { getConnection: async () => ({
      execute: async sql => { events.push(sql.includes('GET_LOCK') ? 'lock' : 'unlock'); return [[{ acquired: 1 }]]; },
      beginTransaction: async () => events.push('begin'), commit: async () => events.push('commit'),
      rollback: async () => events.push('rollback'), release: () => events.push('release'),
    }) };
    const result = transaction(db, async () => { events.push('action'); if (failed) throw new Error('audit failed'); }, { administration: true });
    if (failed) await assert.rejects(result); else await result;
    assert.deepEqual(events, ['lock', 'begin', 'action', failed ? 'rollback' : 'commit', 'unlock', 'release']);
  }
});

test('HTTP enforces price permissions before writes while allowing unchanged prices', async t => {
  let writes = 0;
  const db = { execute: async sql => {
    if (sql.startsWith('SELECT price_cents')) return [[{ price_cents: '100', wholesale_price_cents: '50' }]];
    writes++;
    return [{ affectedRows: 1, insertId: 2 }];
  } };
  const context = accessContext(actor, ['MANAGE_PRODUCTS']);
  const auth = { withAccess: async (request, required, action) => {
    bearerToken(request);
    requireCapabilities(context, required);
    return action(db, context);
  } };
  const server = createApp({ db, auth }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/api/v1/products`;
  const headers = { Authorization: `Bearer ${'u'.repeat(43)}`, 'Content-Type': 'application/json' };
  const create = await fetch(url, { method: 'POST', headers, body: JSON.stringify({ internal_code: 'TEST', common_name: 'Prueba', category_id: 1, price_cents: 100 }) });
  assert.equal(create.status, 403);
  const change = await fetch(url + '/1', { method: 'PATCH', headers, body: JSON.stringify({ price_cents: 200 }) });
  assert.equal(change.status, 403);
  for (const wholesale_price_cents of [60, null]) {
    const response = await fetch(url + '/1', { method: 'PATCH', headers, body: JSON.stringify({ wholesale_price_cents }) });
    assert.equal(response.status, 403);
  }
  assert.equal(writes, 0);
  const unchanged = await fetch(url + '/1', { method: 'PATCH', headers, body: JSON.stringify({ price_cents: 100, wholesale_price_cents: 50, common_name: 'Nuevo nombre' }) });
  assert.equal(unchanged.status, 200);
  assert.equal(writes, 1);
});
