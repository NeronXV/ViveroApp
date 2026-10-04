import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { importCatalog } from '../scripts/catalog-import.js';
if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Only isolated local Compose test profile');

test('catalog SQL rehearsal: exact prices, full rollback, integer maps and repeat without duplicates', async () => {
  const database = `catalog_test_${randomBytes(8).toString('hex')}`;
  assert.match(database, /^catalog_test_[a-f0-9]{16}$/);
  const db = await mysql.createConnection({ host: 'db', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  let created = false;
  try {
    await db.query(`CREATE DATABASE ${database}`); created = true;
    await db.query(`USE ${database}`);
    await db.query(await readFile('/database/schema.sql','utf8'));
    await db.query('CREATE TABLE schema_migrations(id INT AUTO_INCREMENT PRIMARY KEY,version VARCHAR(80) UNIQUE)');
    for (const file of ['003_catalog_details.sql','006_catalog_imports.sql']) await db.query(await readFile(`/database/migrations/${file}`,'utf8'));
    const source = JSON.parse(await readFile(process.env.CATALOG_PRIVATE_REHEARSAL === '1' ? '/private/catalog.json' : new URL('./fixtures/catalog-import-demo.json',import.meta.url),'utf8'));
    const dry = await importCatalog(db,source); assert.equal(dry.can_apply,true);
    await db.query("CREATE TRIGGER reject_import BEFORE INSERT ON catalog_product_sources FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic fault'");
    await assert.rejects(importCatalog(db,source,{apply:true}));
    const [[rollback]] = await db.query('SELECT (SELECT COUNT(*) FROM categories) AS categories,(SELECT COUNT(*) FROM products) AS products');
    assert.equal(rollback.categories,0); assert.equal(rollback.products,0);
    await db.query('DROP TRIGGER reject_import');
    const applied = await importCatalog(db,source,{apply:true}); assert.equal(applied.committed,true);
    const replay = await importCatalog(db,source,{apply:true}); assert.equal(replay.committed,true); assert.ok(replay.items.every(row => row.action==='reuse'));
    const [[counts]] = await db.query('SELECT (SELECT COUNT(*) FROM categories) AS categories,(SELECT COUNT(*) FROM products) AS products,(SELECT COUNT(*) FROM catalog_product_sources) AS mapped');
    assert.equal(counts.categories,source.categories.length); assert.equal(counts.products,source.products.length); assert.equal(counts.mapped,source.products.length);
    const [rows] = await db.query('SELECT p.price_cents,p.wholesale_price_cents,m.source_id FROM products p JOIN catalog_product_sources m ON m.product_id=p.id');
    for (const row of rows) {
      const original = source.products.find(item => item.id === row.source_id);
      assert.equal(BigInt(row.price_cents),BigInt(original.price_cents));
      assert.equal(row.wholesale_price_cents === null ? null : BigInt(row.wholesale_price_cents), original.wholesale_price_cents === null ? null : BigInt(original.wholesale_price_cents));
    }
    const changed = structuredClone(source); changed.products[0].price_cents++;
    const rejected = await importCatalog(db,changed,{apply:true}); assert.equal(rejected.can_apply,false);
    assert.ok(rejected.items.some(row => row.code==='SOURCE_CHANGED'));
  } finally { if (created) await db.query(`DROP DATABASE ${database}`); await db.end(); }
});
