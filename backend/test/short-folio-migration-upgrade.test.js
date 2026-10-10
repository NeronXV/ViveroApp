import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Isolated Compose required');
test('031 upgrade preserves historical rows and skips collisions with another original folio', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  try {
    const [[applied]] = await db.execute("SELECT COUNT(*) AS n FROM schema_migrations WHERE version='031_short_folios'");
    assert.equal(Number(applied.n), 0, 'Use the preserved local 030 rehearsal, never rerun on an upgraded DB');
    const [user] = await db.execute("INSERT INTO users(email,full_name,branch_id,role_id) VALUES(?, 'Migracion ficticia',1,(SELECT id FROM roles WHERE name='OWNER'))", [`migration-${randomBytes(8).toString('hex')}@example.invalid`]);
    await db.execute("INSERT INTO sales(folio,branch_id,created_by,subtotal_cents,total_cents) VALUES('HISTORICAL-SHORT-FOLIO-TEST',1,?,0,0),('VD-0001',1,?,0,0)", [user.insertId, user.insertId]);
    const snapshot = async () => {
      const result = {};
      for (const table of ['sales','web_orders','cashier_payments','inventory','inventory_movements']) result[table] = (await db.execute(`SELECT * FROM ${table} ORDER BY id`))[0];
      return result;
    };
    const before = await snapshot();
    await db.query(await readFile('/tmp/031_short_folios.sql', 'utf8'));
    assert.deepEqual(await snapshot(), before);
    const [[collisions]] = await db.execute('SELECT COUNT(*) AS n FROM sale_folio_aliases a JOIN sales s ON BINARY s.folio=BINARY a.short_folio AND s.id<>a.sale_id');
    assert.equal(Number(collisions.n), 0);
    const [[skipped]] = await db.execute("SELECT COUNT(*) AS n FROM sale_folio_aliases WHERE short_folio='VD-0001'");
    assert.equal(Number(skipped.n), 0, 'The first candidate belongs to a different historical sale and must be skipped');
    const [[missing]] = await db.execute('SELECT COUNT(*) AS n FROM sales s LEFT JOIN sale_folio_aliases a ON a.sale_id=s.id WHERE a.sale_id IS NULL');
    assert.equal(Number(missing.n), 0);
    const [[original]] = await db.execute("SELECT COUNT(*) AS n FROM sales WHERE folio='VD-0001'");
    assert.equal(Number(original.n), 1);
  } finally { await db.end(); }
});
