import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes,createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { importIdentity } from '../scripts/identity-import.js';
import { prepareSourceCatalog } from '../scripts/source-catalog.js';
import { importCatalog } from '../scripts/catalog-import.js';
import { importHistory } from '../scripts/history-import.js';
import { sourceHistoryFixture } from './fixtures/source-history-demo.js';
if (process.env.API_URL!=='http://api:3001'||process.env.DB_HOST!=='db'||process.env.DB_NAME!=='vivero') throw new Error('Only local Compose test profile');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
test('joint history rehearsal preserves money, dates, identity and inventory without replay',async () => {
  const database=`history_test_${randomBytes(8).toString('hex')}`;
  assert.match(database,/^history_test_[a-f0-9]{16}$/);
  const db=await mysql.createConnection({host:'db',user:'root',password:process.env.DB_PASSWORD,multipleStatements:true});
  let created=false;
  try {
    await db.query(`CREATE DATABASE ${database}`);created=true;
    await db.query(`USE ${database}`);
    await db.query(await readFile('/database/schema.sql','utf8'));
    await db.query('CREATE TABLE schema_migrations(id INT AUTO_INCREMENT PRIMARY KEY,version VARCHAR(80) UNIQUE)');
    await db.query(await readFile('/database/seed-production.sql','utf8'));
    for (const file of ['003_catalog_details.sql','005_catalog_promotions.sql','006_catalog_imports.sql','012_sales_submission.sql','013_cashier_payments.sql','027_identity_imports.sql','028_history_imports.sql']) {
      // Do not alter grants in the shared server. DDL targets only this disposable database.
      const sql=(await readFile(`/database/migrations/${file}`,'utf8')).replace(/^GRANT\b[\s\S]*?;/gm,'');
      await db.query(sql);
    }
    const real=process.env.HISTORY_PRIVATE_REHEARSAL==='1';
    const bytes=real?await readFile('/private/source.json'):Buffer.from(JSON.stringify(sourceHistoryFixture()));
    const target=real?JSON.parse(await readFile('/private/target.json','utf8')):
      {branches:[{id:1,code:'MATRIZ',name:'Demo Matriz',is_active:1}],users:[{id:1,email:'owner@example.invalid',full_name:'Demo Owner',branch_id:1,role_name:'OWNER',is_active:1}]};
    for(const row of target.branches) await db.execute('INSERT INTO branches(id,code,name,is_active) VALUES(?,?,?,?)',[row.id,row.code,row.name,row.is_active]);
    for(const row of target.users){const [[role]]=await db.execute('SELECT id FROM roles WHERE name=?',[row.role_name]);await db.execute('INSERT INTO users(id,email,full_name,branch_id,role_id,is_active,password_hash) VALUES(?,?,?,?,?,?,?)',[row.id,row.email,row.full_name,row.branch_id,role.id,row.is_active,'synthetic-preserved-password-marker']);}
    const beforeOwner=(await db.query('SELECT * FROM users WHERE id=1'))[0];
    const resolutions=real?JSON.parse(await readFile('/private/resolutions.json','utf8')):{input_sha256:digest(bytes),branches:[{source_index:0,target_id:1}],users:[{source_index:0,target_id:1}]};
    const options={sourceKey:'original-source',expectedSha256:digest(bytes)};
    await importIdentity(db,bytes,{...options,resolutions,apply:true});
    const {catalog}=prepareSourceCatalog(bytes,options);
    await importCatalog(db,catalog,{apply:true});
    const dry=await importHistory(db,bytes,options);assert.equal(dry.import_applied,false);
    await db.query("CREATE TRIGGER reject_history BEFORE INSERT ON history_payments_sources FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
    await assert.rejects(importHistory(db,bytes,{...options,apply:true}));
    const [[rollback]]=await db.query('SELECT (SELECT COUNT(*) FROM sales) AS sales,(SELECT COUNT(*) FROM sale_items) AS items,(SELECT COUNT(*) FROM sale_payment_claims) AS claims,(SELECT COUNT(*) FROM cashier_payments) AS payments');
    assert.deepEqual(rollback,{sales:0,items:0,claims:0,payments:0});
    await db.query('DROP TRIGGER reject_history');
    const result=await importHistory(db,bytes,{...options,apply:true});assert.equal(result.import_applied,true);
    const replay=await importHistory(db,bytes,{...options,apply:true});assert.ok(replay.items.every(row => row.action==='reuse'));
    const source=JSON.parse(bytes.toString('utf8'));
    const [[counts]]=await db.query('SELECT (SELECT COUNT(*) FROM sales) AS sales,(SELECT COUNT(*) FROM sale_items) AS items,(SELECT COUNT(*) FROM cashier_payments) AS payments,(SELECT COUNT(*) FROM sale_status_history) AS history,(SELECT COUNT(*) FROM inventory) AS inventory');
    assert.deepEqual(counts,{sales:source.tables.sales.length,items:source.tables.sale_items.length,payments:source.tables.sale_payments.length,history:source.tables.sale_status_history.length,inventory:0});
    assert.equal(digest(Buffer.from(JSON.stringify((await db.query('SELECT * FROM users WHERE id=1'))[0]))),digest(Buffer.from(JSON.stringify(beforeOwner))));
    const [[sums]]=await db.query('SELECT CAST(SUM(amount_due_cents) AS CHAR) AS total,CAST(SUM(amount_received_cents) AS CHAR) AS received,CAST(SUM(change_cents) AS CHAR) AS change_amount FROM cashier_payments');
    assert.deepEqual(sums,{total:result.totals.total_cents,received:result.totals.received_cents,change_amount:result.totals.change_cents});
    const [dates]=await db.query("SELECT m.source_id,s.folio,DATE_FORMAT(s.created_at,'%Y-%m-%d %H:%i:%s.%f') AS created_at FROM sales s JOIN history_sales_sources m ON m.sale_id=s.id");
    const {historyDate}=await import('../scripts/history-import.js');
    for(const row of dates){const original=source.tables.sales.find(sale => sale.id===row.source_id);assert.equal(digest(Buffer.from(row.folio)),digest(Buffer.from(original.folio)));assert.equal(row.created_at,historyDate(original.created_at));}
    const [[claims]]=await db.query('SELECT COUNT(*) AS active FROM sale_payment_claims WHERE released_at IS NULL AND consumed_at IS NULL');assert.equal(claims.active,0);
    await db.query("UPDATE sales SET folio='Changed historical target' WHERE id=(SELECT sale_id FROM history_sales_sources LIMIT 1)");
    await assert.rejects(importHistory(db,bytes,{...options,apply:true}),/HISTORY_TARGET_CHANGED/);
  } finally {if(created)await db.query(`DROP DATABASE ${database}`);await db.end();}
});
