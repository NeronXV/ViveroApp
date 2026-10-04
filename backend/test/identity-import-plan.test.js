import test from 'node:test';
import assert from 'node:assert/strict';
import { planIdentityImport } from '../scripts/identity-import-plan.js';
import { SOURCE_TABLES } from '../scripts/source-export-tables.js';

function fixture() {
  const tables = Object.fromEntries(SOURCE_TABLES.map(name => [name, []]));
  tables.branches = [{ id: 'branch-source', code: 'MATRIZ', name: 'Matriz', is_active: true }];
  tables.auth_users = [{ id: 'user-source', email: 'owner@example.invalid' }];
  tables.profiles = [{ id: 'user-source', full_name: 'Demo Owner', branch_id: 'branch-source', is_active: true }];
  tables.roles = [{ id: 'owner-role', name: 'OWNER' }];
  tables.user_roles = [{ id: 'assignment', user_id: 'user-source', role_id: 'owner-role' }];
  const source = { schema_version: 1, authority: 'supabase-export', exported_at: '2026-10-01T12:00:00Z', tables,
    primary_keys: SOURCE_TABLES.filter(name => name !== 'auth_users').map(table => ({ table, columns: ['id'] })), foreign_keys: [] };
  const target = { branches: [{ id: 1, code: 'MATRIZ', name: 'Matriz', is_active: 1 }],
    users: [{ id: 1, email: 'owner@example.invalid', full_name: 'Demo Owner', branch_id: 1, role_name: 'OWNER', is_active: 1 }] };
  return { source, target };
}
const plan = (source, target) => planIdentityImport(Buffer.from(JSON.stringify(source)), target);
test('identity proposal reuses an exact owner and branch without disclosing source identities', () => {
  const { source, target } = fixture();
  const before = JSON.stringify(target);
  const result = plan(source, target);
  assert.equal(result.can_prepare, true);
  assert.equal(result.import_applied, false);
  assert.equal(result.users[0].action, 'reuse');
  assert.equal(result.branches[0].target_id, 1);
  assert.equal(result.users[0].password_reset_required, false);
  assert.equal(JSON.stringify(target), before);
  assert.equal(JSON.stringify(result).includes('owner@example.invalid'), false);
  assert.equal(JSON.stringify(result).includes('user-source'), false);
});
test('new identities propose new integer IDs and password reset, never copied credentials', () => {
  const { source } = fixture();
  const result = plan(source, { branches: [], users: [] });
  assert.equal(result.can_prepare, true);
  assert.equal(result.users[0].target_id, null);
  assert.equal(result.users[0].password_reset_required, true);
});
test('conflicts in owner, branch code, role cardinality or duplicate emails stop preparation', () => {
  let { source, target } = fixture();
  target.users[0].role_name = 'ADMIN';
  assert.equal(plan(source, target).issues[0].code, 'TARGET_ACCOUNT_CONFLICT');
  ({ source, target } = fixture()); target.branches[0].code = 'OTHER';
  assert.equal(plan(source, target).issues[0].code, 'TARGET_BRANCH_CODE_CONFLICT');
  ({ source, target } = fixture()); source.tables.user_roles = [];
  assert.equal(plan(source, target).issues[0].code, 'SOURCE_PROFILE_OR_ROLE_INVALID');
  ({ source, target } = fixture()); source.tables.auth_users.push({ id: 'other', email: 'OWNER@example.invalid' });
  assert.ok(plan(source, target).issues.some(row => row.code === 'SOURCE_EMAIL_DUPLICATE'));
});
test('explicit resolutions preserve the existing account but cannot change identity or privilege', () => {
  const { source, target } = fixture();
  source.tables.branches[0].code = 'OLD-CODE';
  source.tables.profiles[0].full_name = 'Old Display Name';
  const resolutions = { branches: [{ source_index: 0, target_id: 1 }], users: [{ source_index: 0, target_id: 1 }] };
  const result = planIdentityImport(Buffer.from(JSON.stringify(source)), target, resolutions);
  assert.equal(result.can_prepare, true);
  assert.equal(result.users[0].preserve_target_account, true);
  target.users[0].email = 'other@example.invalid';
  assert.equal(planIdentityImport(Buffer.from(JSON.stringify(source)), target, resolutions).can_prepare, false);
  target.users[0].email = 'owner@example.invalid'; target.users[0].role_name = 'ADMIN';
  assert.equal(planIdentityImport(Buffer.from(JSON.stringify(source)), target, resolutions).can_prepare, false);
  resolutions.users[0].target_id = 99;
  assert.throws(() => planIdentityImport(Buffer.from(JSON.stringify(source)), target, resolutions), /IDENTITY_RESOLUTION_INVALID/);
});
