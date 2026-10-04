import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createApp } from '../src/app.js';
import { createPasswordChanger, passwordChangeInput } from '../src/auth/change-password.js';
import { hashPassword, verifyPassword } from '../src/auth/password.js';

const input = { current_password: 'Synthetic current phrase 2026', new_password: '  Synthetic new phrase 2026  ' };
test('password change accepts only current/new fields and preserves whitespace', () => {
  assert.deepEqual(passwordChangeInput(input), { current: input.current_password, next: input.new_password });
  assert.equal(passwordChangeInput({ ...input, new_password: 'Ab9!xy' }).next, 'Ab9!xy');
  assert.equal(passwordChangeInput({ ...input, new_password: '😀'.repeat(6) }).next, '😀'.repeat(6));
  for (const value of [null, [], {}, { ...input, user_id: 99 }, { ...input, new_password: 'short' },
    { ...input, new_password: input.current_password }, { ...input, new_password: 'replace-with-a-new-password' },
    { ...input, current_password: 'x'.repeat(129) }, { ...input, new_password: 'x'.repeat(14)+'\0' }]) assert.throws(() => passwordChangeInput(value));
});

test('unauthenticated HTTP and forbidden methods cannot reach password work', async t => {
  let reads = 0;
  const server = createApp({ db: { getConnection: async () => { reads++; throw new Error('Unexpected DB access'); } } }).listen(0, '127.0.0.1');
  await once(server, 'listening'); t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/api/v1/auth/change-password`;
  assert.equal((await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })).status, 401);
  assert.equal((await fetch(url)).status, 404);
  assert.equal(reads, 0);
});

test('own password needs the current secret, pins actor/target and never audits secrets', async () => {
  let stored = await hashPassword(input.current_password);
  const writes = [], lockOptions = [];
  const connection = { execute: async (sql, args) => {
    if (sql.startsWith('SELECT password_hash')) return [[{ password_hash: stored }]];
    writes.push({ sql, args }); if (sql.startsWith('UPDATE users')) stored = args[0];
    return [{ affectedRows: 1 }];
  } };
  const budget = { getConnection: async () => ({ beginTransaction: async () => {}, commit: async () => {}, rollback: async () => {}, release() {},
    execute: async sql => sql.startsWith('SELECT attempts') ? [[{ attempts: 1 }]] : [{}] }) };
  const auth = { withAccess: async (_request, required, action, options) => {
    assert.deepEqual(required, []); lockOptions.push(options);
    return action(connection, { user: { id: 7, email: 'synthetic@example.invalid' } });
  } };
  const change = createPasswordChanger(budget, auth);
  await assert.rejects(change({}, { ...input, current_password: 'Incorrect phrase' }, 'test'), { code: 'CURRENT_PASSWORD_INCORRECT' });
  assert.equal(writes.length, 0);
  assert.deepEqual(await change({}, { ...input, new_password: 'Ab9!xy' }, 'test'), { schema_version: 1, password_changed: true, sessions_revoked: true });
  assert.equal(await verifyPassword('Ab9!xy', stored), true);
  assert.equal(writes[0].args[1], 7);
  assert.deepEqual(writes[1].args.slice(0, 3), [7, 7, 'USER_PASSWORD']);
  assert.ok(!JSON.stringify(writes[1]).includes(input.new_password));
  assert.ok(!JSON.stringify(writes[1]).includes(input.current_password));
  assert.deepEqual(lockOptions.at(-1), { administration: true, readCommitted: true });
});
