import test from 'node:test';
import assert from 'node:assert/strict';
import { accountInput, accountPassword, branchInput, staffInput, adminQuery, ownerProtection, createAdministration } from '../src/administration.js';

test('staff accounts validate strict credentials, activation and role without altering password', () => {
  const input = { email: ' DEMO@example.invalid ', password: ' demo-password-long ', full_name: ' Demo  personal ', role_name: 'sales', branch_id: 1, is_active: false };
  const normalized = accountInput(input);
  assert.equal(normalized.email, 'demo@example.invalid');
  assert.equal(normalized.password, input.password);
  assert.equal(normalized.full_name, 'Demo personal');
  assert.equal(normalized.role_name, 'SALES');
  for (const change of [{ password: 'short' }, { password: 'replace-with-password' }, { role_name: 'ROOT' }, { branch_id: '1' }, { is_active: 1 }, { full_name: 'A' }, { extra: true }]) assert.throws(() => accountInput({ ...input, ...change }));
  assert.equal(accountPassword({ password: input.password }), input.password);
  assert.throws(() => accountPassword({ password: input.password, email: input.email }));
});

test('administration validates normalized branch identities, strict state and role bodies', () => {
  assert.deepEqual(branchInput({ code: ' demo-01 ', name: ' Demo   sucursal ' }), { code: 'DEMO-01', name: 'Demo sucursal' });
  for (const value of [{ code: 'bad code', name: 'Demo' }, { code: 'AA', name: 'Demo', is_active: true }]) assert.throws(() => branchInput(value));
  assert.equal(staffInput('role', { role_name: 'sales' }), 'SALES');
  assert.equal(staffInput('branch', { branch_id: 2 }), 2);
  for (const [action, input] of [['role', { role_name: 'ROOT' }], ['active', { is_active: 1 }], ['branch', { branch_id: null }]]) assert.throws(() => staffInput(action, input));
  assert.equal(adminQuery(new URLSearchParams('limit=10&include_inactive=true')).include_inactive, true);
  for (const query of ['limit=101', 'include_inactive=1', 'limit=1&limit=2', 'branch_id=1']) assert.throws(() => adminQuery(new URLSearchParams(query)));
});
test('ADMIN cannot grant or change OWNER while OWNER retains authorized management', () => {
  const admin = { role: { name: 'ADMIN' } };
  assert.throws(() => ownerProtection(admin, 'OWNER'));
  assert.throws(() => ownerProtection(admin, 'SALES', 'OWNER'));
  ownerProtection(admin, 'SALES', 'CASHIER');
  ownerProtection({ role: { name: 'OWNER' } }, 'OWNER', 'SALES');
});

test('last active OWNER cannot lose role or activation before any write', async () => {
  const db = { execute: async sql => {
    if (sql.startsWith('SELECT COUNT')) return [[{ n: 1 }]];
    if (sql.startsWith('SELECT u.id')) return [[{ id: 1, role_name: 'OWNER', is_active: 1, branch_active: 1 }]];
    assert.fail('Last OWNER protection must precede writes');
  } };
  const service = createAdministration(db, { user: { id: 1 }, role: { name: 'OWNER' }, access_state: 'ACTIVE', capabilities: ['ASSIGN_ROLES', 'MANAGE_USERS'] });
  for (const [action, value] of [['role', 'SALES'], ['active', false]]) {
    await assert.rejects(service.changeStaff(1, action, value), { code: 'ROLE_LAST_OWNER_REQUIRED' });
  }
});
