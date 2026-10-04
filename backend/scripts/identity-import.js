import { createHash } from 'node:crypto';
import { inspectSourceExport } from './source-export-preflight.js';
import { planIdentityImport } from './identity-import-plan.js';
import { normalizeEmail } from '../src/auth/password.js';

export class IdentityImportError extends Error {}
const fail = code => { throw new IdentityImportError(code); };
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  return value;
}
const hash = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest();
const definitions = {
  branches: { map: 'identity_branch_sources', id: 'branch_id' },
  users: { map: 'identity_user_sources', id: 'user_id' },
};
export async function importIdentity(db, bytes, { sourceKey, expectedSha256, resolutions, apply = false }) {
  const verified = inspectSourceExport(bytes);
  if (verified.input_sha256 !== expectedSha256) fail('INPUT_HASH_MISMATCH');
  if (typeof sourceKey !== 'string' || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(sourceKey)) fail('SOURCE_KEY_INVALID');
  if (!resolutions || resolutions.input_sha256 !== expectedSha256) fail('RESOLUTION_HASH_MISMATCH');
  const { tables } = JSON.parse(bytes.toString('utf8'));
  const sourceRows = { branches: tables.branches, users: tables.auth_users.map(account => ({
    account, profile: tables.profiles.find(row => row.id === account.id),
    roles: tables.user_roles.filter(row => row.user_id === account.id).map(row => ({ assignment: row, role: tables.roles.find(role => role.id === row.role_id) })),
  })) };
  const ids = { branches: tables.branches.map(row => row.id), users: tables.auth_users.map(row => row.id) };
  if (Object.values(ids).flat().some(id => typeof id !== 'string' || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(id))) fail('SOURCE_ID_INVALID');
  const [[lock]] = await db.execute("SELECT GET_LOCK('vivero_identity_import',10) AS acquired");
  if (lock.acquired !== 1) fail('IMPORT_LOCK_UNAVAILABLE');
  try {
    await db.beginTransaction();
    const [migrations] = await db.execute("SELECT id FROM schema_migrations WHERE version='027_identity_imports'");
    if (!migrations.length) fail('MIGRATION_REQUIRED');
    const readTarget = async () => {
      const [branches] = await db.query('SELECT id,code,name,is_active FROM branches FOR UPDATE');
      const [users] = await db.query('SELECT u.id,u.email,u.full_name,u.branch_id,u.is_active,r.name AS role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id FOR UPDATE');
      return { branches, users };
    };
    let target = await readTarget();
    const resolved = { branches: [...resolutions.branches], users: [...resolutions.users] };
    const mappings = {};
    for (const entity of ['branches', 'users']) {
      const definition = definitions[entity];
      const [stored] = await db.execute(`SELECT source_id,${definition.id} AS target_id,source_hash,target_hash FROM ${definition.map} WHERE source_key=? FOR UPDATE`, [sourceKey]);
      mappings[entity] = new Map(stored.map(row => [row.source_id, row]));
      for (const [index, id] of ids[entity].entries()) {
        const previous = mappings[entity].get(id);
        if (!previous) continue;
        if (!previous.source_hash.equals(hash(sourceRows[entity][index]))) fail('SOURCE_CHANGED');
        const existing = target[entity].find(row => row.id === previous.target_id);
        if (!existing || !previous.target_hash.equals(hash(existing))) fail('TARGET_CHANGED');
        const explicit = resolved[entity].find(row => row.source_index === index);
        if (explicit && explicit.target_id !== previous.target_id) fail('MAPPING_CONFLICT');
        if (!explicit) resolved[entity].push({ source_index: index, target_id: previous.target_id });
      }
    }
    const plan = planIdentityImport(bytes, target, resolved);
    if (!plan.can_prepare) { await db.rollback(); return { ...plan, import_applied: false }; }
    if (!apply) { await db.rollback(); return plan; }
    const branchIds = new Map();
    for (const item of plan.branches) {
      const row = tables.branches[item.source_index];
      if (item.action === 'create') {
        const [inserted] = await db.execute('INSERT INTO branches(code,name,is_active) VALUES(?,?,?)', [row.code, row.name, row.is_active]);
        item.target_id = inserted.insertId;
      }
      branchIds.set(row.id, item.target_id);
    }
    const [roles] = await db.query('SELECT id,name FROM roles');
    for (const item of plan.users) {
      if (item.action !== 'create') continue;
      const row = sourceRows.users[item.source_index];
      const roleId = roles.find(role => role.name === row.roles[0].role.name)?.id;
      if (!roleId) fail('TARGET_ROLE_MISSING');
      const profile = row.profile;
      const [inserted] = await db.execute('INSERT INTO users(email,full_name,branch_id,role_id,is_active,password_hash) VALUES(?,?,?,?,?,NULL)',
        [normalizeEmail(row.account.email), profile.full_name, profile.branch_id === null ? null : branchIds.get(profile.branch_id), roleId, profile.is_active]);
      item.target_id = inserted.insertId;
    }
    target = await readTarget();
    for (const entity of ['branches', 'users']) {
      const definition = definitions[entity];
      for (const item of plan[entity]) {
        const index = item.source_index;
        if (mappings[entity].has(ids[entity][index])) continue;
        await db.execute(`INSERT INTO ${definition.map}(source_key,source_id,${definition.id},source_hash,target_hash) VALUES(?,?,?,?,?)`,
          [sourceKey, ids[entity][index], item.target_id, hash(sourceRows[entity][index]), hash(target[entity].find(row => row.id === item.target_id))]);
      }
    }
    await db.commit();
    return { ...plan, import_applied: true };
  } catch (error) {
    await db.rollback();
    if (error instanceof IdentityImportError) throw error;
    fail('IDENTITY_IMPORT_FAILED');
  } finally { await db.execute("SELECT RELEASE_LOCK('vivero_identity_import')"); }
}
