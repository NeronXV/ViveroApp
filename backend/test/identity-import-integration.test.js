import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes, createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { importIdentity } from '../scripts/identity-import.js';
import { SOURCE_TABLES } from '../scripts/source-export-tables.js';
if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Only local Compose test profile');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
function fixture() {
  const tables = Object.fromEntries(SOURCE_TABLES.map(name => [name, []]));
  const id = n => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
  tables.branches = [{ id: id(1), code: 'OLD', name: 'Demo Matriz', is_active: true }, { id: id(2), code: 'CENTRO', name: 'Demo Centro', is_active: true }];
  tables.auth_users = [{ id: id(3), email: 'owner@example.invalid' }, { id: id(4), email: 'staff@example.invalid' }];
  tables.profiles = tables.auth_users.map(row => ({ id: row.id, full_name: 'Demo account', branch_id: id(2), is_active: true }));
  tables.roles = [{ id: id(5), name: 'OWNER' }];
  tables.user_roles = tables.auth_users.map((row, index) => ({ id: id(6 + index), user_id: row.id, role_id: id(5) }));
  return Buffer.from(JSON.stringify({ schema_version: 1, authority: 'supabase-export', exported_at: '2026-10-01T12:00:00Z', tables,
    primary_keys: SOURCE_TABLES.filter(name => name !== 'auth_users').map(table => ({ table, columns: ['id'] })), foreign_keys: [] }));
}
test('identity import: private isolated rehearsal, rollback, exact reuse, FKs and idempotency', async () => {
  const database = `identity_test_${randomBytes(8).toString('hex')}`;
  assert.match(database, /^identity_test_[a-f0-9]{16}$/);
  const db = await mysql.createConnection({ host: 'db', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  let created = false;
  try {
    await db.query(`CREATE DATABASE ${database}`); created = true;
    await db.query(`USE ${database}`);
    await db.query(await readFile('/database/schema.sql', 'utf8'));
    await db.query('CREATE TABLE schema_migrations(id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,version VARCHAR(80) NOT NULL UNIQUE)');
    await db.query(await readFile('/database/seed-production.sql', 'utf8'));
    await db.query(await readFile('/database/migrations/027_identity_imports.sql', 'utf8'));
    const real = process.env.IDENTITY_PRIVATE_REHEARSAL === '1';
    const bytes = real ? await readFile('/private/source-export-e4cdebf60fc7c789.json') : fixture();
    const target = real ? JSON.parse(await readFile('/private/target-identities.json', 'utf8'))
      : { branches: [{ id: 1, code: 'MATRIZ', name: 'Demo Matriz', is_active: 1 }], users: [{ id: 1, email: 'owner@example.invalid', full_name: 'Current Owner', branch_id: 1, role_name: 'OWNER', is_active: 1 }] };
    for (const row of target.branches) await db.execute('INSERT INTO branches(id,code,name,is_active) VALUES(?,?,?,?)', [row.id,row.code,row.name,row.is_active]);
    for (const row of target.users) {
      const [[role]] = await db.execute('SELECT id FROM roles WHERE name=?', [row.role_name]);
      await db.execute('INSERT INTO users(id,email,full_name,branch_id,role_id,is_active,password_hash) VALUES(?,?,?,?,?,?,?)', [row.id,row.email,row.full_name,row.branch_id,role.id,row.is_active,'synthetic-preserved-password-marker']);
    }
    const resolutions = real ? JSON.parse(await readFile('/private/identity-import-resolutions.json','utf8'))
      : { input_sha256: digest(bytes), branches: [{ source_index: 0, target_id: 1 }], users: [{ source_index: 0, target_id: 1 }] };
    const options = { sourceKey: 'rehearsal-origin', expectedSha256: digest(bytes), resolutions };
    const before = await db.query('SELECT * FROM users WHERE id=1');
    await assert.rejects(importIdentity(db, bytes, { ...options, expectedSha256: '0'.repeat(64), apply: true }), /INPUT_HASH_MISMATCH/);
    const dry = await importIdentity(db, bytes, options); assert.equal(dry.can_prepare,true); assert.equal(dry.import_applied,false);
    await db.query("CREATE TRIGGER reject_import BEFORE INSERT ON identity_user_sources FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic fault'");
    await assert.rejects(importIdentity(db,bytes,{...options,apply:true}));
    const [[rollback]] = await db.query('SELECT (SELECT COUNT(*) FROM users) AS users,(SELECT COUNT(*) FROM branches) AS branches,(SELECT COUNT(*) FROM identity_branch_sources) AS mappings');
    assert.equal(rollback.users,1); assert.equal(rollback.branches,1); assert.equal(rollback.mappings,0);
    await db.query('DROP TRIGGER reject_import');
    const applied = await importIdentity(db,bytes,{...options,apply:true}); assert.equal(applied.import_applied,true);
    const again = await importIdentity(db,bytes,{...options,apply:true}); assert.ok(again.users.every(row => row.action==='reuse')); assert.ok(again.branches.every(row => row.action==='reuse'));
    assert.deepEqual((await db.query('SELECT * FROM users WHERE id=1'))[0],before[0]);
    const [[counts]] = await db.query('SELECT (SELECT COUNT(*) FROM users) AS users,(SELECT COUNT(*) FROM branches) AS branches,(SELECT COUNT(*) FROM users WHERE password_hash IS NULL) AS reset_accounts');
    const source = JSON.parse(bytes.toString('utf8'));
    assert.equal(counts.users,source.tables.auth_users.length); assert.equal(counts.branches,source.tables.branches.length); assert.equal(counts.reset_accounts,counts.users-1);
    await assert.rejects(db.execute('INSERT INTO identity_branch_sources(source_key,source_id,branch_id,source_hash,target_hash) VALUES(?,?,?,UNHEX(SHA2(?,256)),UNHEX(SHA2(?,256)))', ['fk-test','missing',4294967295,'demo','demo']));
    const changed = structuredClone(source); changed.tables.profiles[0].full_name = 'Changed source name';
    const changedBytes = Buffer.from(JSON.stringify(changed));
    await assert.rejects(importIdentity(db,changedBytes,{...options,expectedSha256:digest(changedBytes),resolutions:{...resolutions,input_sha256:digest(changedBytes)},apply:true}),/SOURCE_CHANGED/);
    await db.query("UPDATE branches SET name='Changed target' WHERE id=1");
    await assert.rejects(importIdentity(db,bytes,{...options,apply:true}),/TARGET_CHANGED/);
  } finally {
    if (created) await db.query(`DROP DATABASE ${database}`);
    await db.end();
  }
});
