import { pathToFileURL } from 'node:url';
import { openAdminDb } from './admin-db.js';
import { hashPassword, validateCredentials } from '../src/auth/password.js';
import { branchInput } from '../src/administration.js';

export function bootstrapInput(env) {
  const credentials = validateCredentials({ email: env.BOOTSTRAP_EMAIL, password: env.BOOTSTRAP_PASSWORD }, true);
  if (credentials.password.startsWith('replace-with-')) throw new Error('Replace placeholder');
  const name = env.BOOTSTRAP_FULL_NAME?.trim();
  if (!name || !name.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(name) || [...name].length < 2 || [...name].length > 160) throw new Error('Invalid name');
  const branch = env.BOOTSTRAP_BRANCH_NAME === undefined || env.BOOTSTRAP_BRANCH_NAME === '' ? null
    : branchInput({ code: env.BOOTSTRAP_BRANCH_CODE, name: env.BOOTSTRAP_BRANCH_NAME });
  const code = branch?.code ?? env.BOOTSTRAP_BRANCH_CODE?.trim();
  if (!code) throw new Error('Branch required');
  return { ...credentials, name, code, branch };
}

export async function createInitialOwner(db, input) {
  const hash = await hashPassword(input.password);
  const [[lock]] = await db.execute("SELECT GET_LOCK('vivero_bootstrap_owner', 10) AS acquired");
  if (lock.acquired !== 1) throw new Error('Bootstrap lock unavailable');
  try {
    await db.beginTransaction();
    const [migration] = await db.execute("SELECT id FROM schema_migrations WHERE version = '002_identity'");
    if (!migration.length) throw new Error('Apply migration first');
    const [users] = await db.execute('SELECT id FROM users WHERE password_hash IS NOT NULL OR is_active = 1 FOR UPDATE');
    if (users.length) throw new Error('Bootstrap already completed');
    let [[branch]] = await db.execute('SELECT id,is_active,name FROM branches WHERE code = ? FOR UPDATE', [input.code]);
    if (!branch && input.branch) {
      const [created] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [input.branch.code, input.branch.name]);
      branch = { id: created.insertId, is_active: 1, name: input.branch.name };
    }
    if (!branch?.is_active || (input.branch && branch.name !== input.branch.name)) throw new Error('Branch unavailable or mismatch');
    const [[role]] = await db.execute("SELECT id FROM roles WHERE name = 'OWNER'");
    if (!role) throw new Error('Role unavailable');
    await db.execute('INSERT INTO users (email, full_name, password_hash, branch_id, role_id, is_active) VALUES (?, ?, ?, ?, ?, 1)',
      [input.email, input.name, hash, branch.id, role.id]);
    await db.commit();
  } catch (error) {
    await db.rollback();
    throw error;
  } finally {
    await db.execute("SELECT RELEASE_LOCK('vivero_bootstrap_owner')");
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let db;
  try {
    const input = bootstrapInput(process.env);
    db = await openAdminDb();
    await createInitialOwner(db, input);
    console.log('Initial owner created. No credentials were printed.');
  } catch {
    console.error('Owner not created: check input, migration, branch and whether bootstrap already ran. Existing accounts were not changed.');
    process.exitCode = 1;
  } finally { if (db) await db.end(); }
}
