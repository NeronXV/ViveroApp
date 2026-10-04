import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { bootstrapInput, createInitialOwner } from '../scripts/bootstrap-owner.js';
import { verifyPassword } from '../src/auth/password.js';
if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Only isolated local Compose test profile');
test('MariaDB first owner: atomic branch/account, FK, rollback and repeat protection', async () => {
  const database = `bootstrap_test_${randomBytes(8).toString('hex')}`;
  assert.match(database, /^bootstrap_test_[a-f0-9]{16}$/);
  const db = await mysql.createConnection({ host: 'db', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  let created = false;
  try {
    await db.query(`CREATE DATABASE ${database}`); created = true;
    await db.query(`USE ${database}`);
    await db.query(await readFile('/database/schema.sql', 'utf8'));
    await db.query("CREATE TABLE schema_migrations(id INT PRIMARY KEY AUTO_INCREMENT,version VARCHAR(80)); INSERT INTO schema_migrations(version) VALUES('002_identity'); INSERT INTO roles(name,display_name) VALUES('OWNER','Demo owner')");
    const input = bootstrapInput({ BOOTSTRAP_EMAIL: 'demo@example.invalid', BOOTSTRAP_PASSWORD: 'Synthetic initial password 2026', BOOTSTRAP_FULL_NAME: 'Demo owner', BOOTSTRAP_BRANCH_CODE: 'CENTRO', BOOTSTRAP_BRANCH_NAME: 'Demo branch' });
    await db.query("CREATE TRIGGER reject_owner BEFORE INSERT ON users FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic rollback'");
    await assert.rejects(createInitialOwner(db, input));
    const [[empty]] = await db.query('SELECT COUNT(*) AS n FROM branches'); assert.equal(empty.n, 0);
    await db.query('DROP TRIGGER reject_owner');
    await createInitialOwner(db, input);
    const [[owner]] = await db.query('SELECT u.password_hash,u.is_active,b.name,r.name AS role FROM users u JOIN branches b ON b.id=u.branch_id JOIN roles r ON r.id=u.role_id');
    assert.equal(owner.name, 'Demo branch'); assert.equal(owner.role, 'OWNER'); assert.equal(owner.is_active, 1); assert.ok(await verifyPassword(input.password, owner.password_hash));
    await assert.rejects(createInitialOwner(db, { ...input, code: 'ANOTHER', branch: { code: 'ANOTHER', name: 'Another demo' } }));
    const [[once]] = await db.query('SELECT COUNT(*) AS n FROM branches'); assert.equal(once.n, 1);
    const [[users]] = await db.query('SELECT COUNT(*) AS n FROM users'); assert.equal(users.n, 1);
  } finally {
    if (created) await db.query(`DROP DATABASE ${database}`);
    await db.end();
  }
});
