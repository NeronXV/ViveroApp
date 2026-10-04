import { inspectSourceExport } from './source-export-preflight.js';
import { branchInput } from '../src/administration.js';
import { normalizeEmail } from '../src/auth/password.js';

const roles = ['SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER'];
// A proposal only: no SQL, passwords, sessions, or writes. Reuse never updates
// an existing account; mismatches require an explicit resolution before import.
export function planIdentityImport(sourceBytes, target, resolutions = { branches: [], users: [] }) {
  const verified = inspectSourceExport(sourceBytes);
  const { tables } = JSON.parse(sourceBytes.toString('utf8'));
  if (!target || !Array.isArray(target.branches) || !Array.isArray(target.users)) throw new Error('TARGET_IDENTITY_INVALID');
  for (const rows of [target.branches, target.users]) {
    if (rows.some(row => !row || !Number.isInteger(row.id) || row.id < 1 || row.id > 4294967295
      || ![0, 1, false, true].includes(row.is_active)) || new Set(rows.map(row => row.id)).size !== rows.length) throw new Error('TARGET_IDENTITY_INVALID');
  }
  if (target.branches.some(row => typeof row.code !== 'string' || typeof row.name !== 'string')
    || target.users.some(row => typeof row.email !== 'string' || typeof row.full_name !== 'string'
      || !roles.includes(row.role_name) || (row.branch_id !== null && !target.branches.some(branch => branch.id === row.branch_id)))) throw new Error('TARGET_IDENTITY_INVALID');
  for (const entity of ['branches', 'users']) {
    const rows = resolutions[entity];
    const sourceRows = entity === 'users' ? tables.auth_users : tables.branches;
    if (!Array.isArray(rows) || rows.some(row => !row || !Number.isInteger(row.source_index)
      || row.source_index < 0 || row.source_index >= sourceRows.length
      || !target[entity].some(item => item.id === row.target_id))
      || new Set(rows.map(row => row.source_index)).size !== rows.length
      || new Set(rows.map(row => row.target_id)).size !== rows.length) throw new Error('IDENTITY_RESOLUTION_INVALID');
  }
  const issues = [], branches = [], users = [];
  const issue = (entity, index, code) => issues.push({ entity, index, code });
  const branchTargets = new Map(), codes = new Set(), emails = new Set();
  for (const [index, row] of tables.branches.entries()) {
    let data;
    try { data = branchInput({ code: row.code, name: row.name }); }
    catch { issue('branches', index, 'SOURCE_BRANCH_INVALID'); continue; }
    if (typeof row.is_active !== 'boolean' || data.code !== row.code || data.name !== row.name || codes.has(data.code)) {
      issue('branches', index, 'SOURCE_BRANCH_NORMALIZATION_OR_DUPLICATE'); continue;
    }
    codes.add(data.code);
    const resolution = resolutions.branches.find(item => item.source_index === index);
    const matches = target.branches.filter(item => resolution ? item.id === resolution.target_id : item.code.toUpperCase() === data.code);
    if (matches.length > 1) { issue('branches', index, 'TARGET_BRANCH_AMBIGUOUS'); continue; }
    const existing = matches[0];
    if (existing && (existing.name !== data.name || Boolean(existing.is_active) !== row.is_active)) {
      issue('branches', index, 'TARGET_BRANCH_CONFLICT'); continue;
    }
    // A matching name with a different code is not silently duplicated.
    if (!existing && target.branches.some(item => item.name.trim().toLowerCase() === data.name.toLowerCase())) {
      issue('branches', index, 'TARGET_BRANCH_CODE_CONFLICT'); continue;
    }
    branchTargets.set(row.id, existing?.id ?? null);
    branches.push({ source_index: index, action: existing ? 'reuse' : 'create', target_id: existing?.id ?? null });
  }
  for (const [index, account] of tables.auth_users.entries()) {
    const profile = tables.profiles.find(row => row.id === account.id);
    const assignments = tables.user_roles.filter(row => row.user_id === account.id);
    let email;
    try { email = normalizeEmail(account.email); }
    catch { issue('users', index, 'SOURCE_EMAIL_INVALID'); continue; }
    if (emails.has(email)) { issue('users', index, 'SOURCE_EMAIL_DUPLICATE'); continue; }
    emails.add(email);
    const role = assignments.length === 1 ? tables.roles.find(row => row.id === assignments[0].role_id)?.name : null;
    if (!profile || !roles.includes(role) || typeof profile.is_active !== 'boolean'
      || typeof profile.full_name !== 'string' || !profile.full_name.isWellFormed()
      || /[\u0000-\u001f\u007f-\u009f]/u.test(profile.full_name)
      || [...profile.full_name.trim()].length < 2 || [...profile.full_name].length > 160) {
      issue('users', index, 'SOURCE_PROFILE_OR_ROLE_INVALID'); continue;
    }
    if (profile.branch_id !== null && !branchTargets.has(profile.branch_id)) {
      issue('users', index, 'BRANCH_MAPPING_UNRESOLVED'); continue;
    }
    const resolution = resolutions.users.find(item => item.source_index === index);
    const matches = target.users.filter(item => resolution ? item.id === resolution.target_id : normalizeEmail(item.email) === email);
    if (matches.length > 1) { issue('users', index, 'TARGET_EMAIL_AMBIGUOUS'); continue; }
    const existing = matches[0];
    const branchId = profile.branch_id === null ? null : branchTargets.get(profile.branch_id);
    if (existing && (normalizeEmail(existing.email) !== email || existing.role_name !== role
      || Boolean(existing.is_active) !== profile.is_active
      || (!resolution && (existing.branch_id !== branchId || existing.full_name !== profile.full_name)))) {
      issue('users', index, 'TARGET_ACCOUNT_CONFLICT'); continue;
    }
    users.push({ source_index: index, action: existing ? 'reuse' : 'create', target_id: existing?.id ?? null,
      password_reset_required: !existing, preserve_target_account: Boolean(resolution) });
  }
  return { input_sha256: verified.input_sha256, import_applied: false,
    can_prepare: issues.length === 0, branches, users, issues };
}
