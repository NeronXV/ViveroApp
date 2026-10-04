import { readFile, readdir } from 'node:fs/promises';
import { openAdminDb } from './admin-db.js';

let db;
try {
  db = await openAdminDb({ multipleStatements: true });
  const [[lock]] = await db.execute("SELECT GET_LOCK('vivero_schema_migration', 10) AS acquired");
  if (lock.acquired !== 1) throw new Error('Migration lock unavailable');
  await db.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(80) NOT NULL UNIQUE,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB`);
  const files = (await readdir('/database/migrations')).filter(name => /^\d{3}_[a-z_]+\.sql$/.test(name)).sort();
  for (const file of files) {
    const version = file.slice(0, -4);
    const [existing] = await db.execute('SELECT id FROM schema_migrations WHERE version = ?', [version]);
    if (existing.length) { console.log(`${version}: already applied`); continue; }
    // Only checked-in SQL from the read-only mount; never HTTP/user-supplied SQL.
    await db.query(await readFile(`/database/migrations/${file}`, 'utf8'));
    console.log(`${version}: applied`);
  }
} catch {
  console.error('Migration failed. Check the local database state before retrying; DDL may be partially applied. No database was reset.');
  process.exitCode = 1;
} finally {
  if (db) await db.end(); // Releases the advisory lock.
}
