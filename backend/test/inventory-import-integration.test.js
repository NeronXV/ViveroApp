import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import {randomBytes,createHash} from 'node:crypto';
import {readFile,readdir} from 'node:fs/promises';
import {importIdentity} from '../scripts/identity-import.js';
import {prepareSourceCatalog} from '../scripts/source-catalog.js';
import {importCatalog} from '../scripts/catalog-import.js';
import {importHistory} from '../scripts/history-import.js';
import {importInventory} from '../scripts/inventory-import.js';
import {quantityMilli} from '../scripts/source-inventory.js';
import {sourceInventoryFixture} from './fixtures/source-inventory-demo.js';
if(process.env.API_URL!=='http://api:3001'||process.env.DB_HOST!=='db'||process.env.DB_NAME!=='vivero')throw new Error('Only isolated local Compose test profile');
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
test('complete schema inventory rehearsal: exact ledger, transfers, zero counts, rollback and replay',async()=>{
  const database=`inventory_test_${randomBytes(8).toString('hex')}`;assert.match(database,/^inventory_test_[a-f0-9]{16}$/);
  const db=await mysql.createConnection({host:'db',user:'root',password:process.env.DB_PASSWORD,multipleStatements:true});let created=false;
  try{
    await db.query(`CREATE DATABASE ${database}`);created=true;await db.query(`USE ${database}`);
    await db.query(await readFile('/database/schema.sql','utf8'));
    await db.query(await readFile('/database/seed-production.sql','utf8'));
    await db.query('CREATE TABLE schema_migrations(id INT AUTO_INCREMENT PRIMARY KEY,version VARCHAR(80) UNIQUE)');
    for(const file of (await readdir('/database/migrations')).filter(name=>/^\d{3}_[a-z_]+\.sql$/.test(name)).sort()){
      const sql=(await readFile(`/database/migrations/${file}`,'utf8')).replace(/^(?:GRANT|REVOKE)\b[\s\S]*?;/gm,'');await db.query(sql);
    }
    const [[schema]]=await db.query('SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=DATABASE()');assert.equal(schema.n,55);
    const [versions]=await db.query('SELECT version FROM schema_migrations ORDER BY version');assert.equal(versions.length,28);assert.equal(versions.at(-1).version,'029_inventory_imports');
    const real=process.env.INVENTORY_PRIVATE_REHEARSAL==='1';const bytes=real?await readFile('/private/source.json'):Buffer.from(JSON.stringify(sourceInventoryFixture()));
    const target=real?JSON.parse(await readFile('/private/target.json','utf8')):{branches:[{id:1,code:'MATRIZ',name:'Demo Matriz',is_active:1}],users:[{id:1,email:'owner@example.invalid',full_name:'Demo Owner',branch_id:1,role_name:'OWNER',is_active:1}]};
    for(const row of target.branches)await db.execute('INSERT INTO branches(id,code,name,is_active) VALUES(?,?,?,?)',[row.id,row.code,row.name,row.is_active]);
    for(const row of target.users){const [[role]]=await db.execute('SELECT id FROM roles WHERE name=?',[row.role_name]);await db.execute('INSERT INTO users(id,email,full_name,branch_id,role_id,is_active,password_hash) VALUES(?,?,?,?,?,?,?)',[row.id,row.email,row.full_name,row.branch_id,role.id,row.is_active,'synthetic-preserved-password-marker']);}
    const resolutions=real?JSON.parse(await readFile('/private/resolutions.json','utf8')):{input_sha256:digest(bytes),branches:[{source_index:0,target_id:1}],users:[{source_index:0,target_id:1}]};
    const options={sourceKey:'original-source',expectedSha256:digest(bytes)};
    await importIdentity(db,bytes,{...options,resolutions,apply:true});const {catalog}=prepareSourceCatalog(bytes,options);await importCatalog(db,catalog,{apply:true});await importHistory(db,bytes,{...options,apply:true});
    const [[salesBefore]]=await db.query('SELECT COUNT(*) AS n FROM sales');
    assert.equal((await importInventory(db,bytes,options)).import_applied,false);
    await db.query("CREATE TRIGGER reject_import BEFORE INSERT ON inventory_counts_sources FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
    await assert.rejects(importInventory(db,bytes,{...options,apply:true}));
    const [[rollback]]=await db.query('SELECT (SELECT COUNT(*) FROM inventory) AS balances,(SELECT COUNT(*) FROM inventory_movements) AS movements,(SELECT COUNT(*) FROM inventory_counts) AS counts');assert.deepEqual(rollback,{balances:0,movements:0,counts:0});
    await db.query('DROP TRIGGER reject_import');const result=await importInventory(db,bytes,{...options,apply:true});assert.equal(result.import_applied,true);
    const replay=await importInventory(db,bytes,{...options,apply:true});assert.equal(replay.action,'reuse');
    const source=JSON.parse(bytes.toString('utf8')).tables;
    const [[counts]]=await db.query('SELECT (SELECT COUNT(*) FROM inventory) AS balances,(SELECT COUNT(*) FROM inventory_movements) AS movements,(SELECT COUNT(*) FROM inventory_counts) AS counts');assert.deepEqual(counts,{balances:result.counts.balance_scopes,movements:source.inventory_movements.length,counts:source.inventory_counts.length});
    const [balances]=await db.query('SELECT i.quantity,i.minimum_stock,b.source_branch_id,b.source_product_id FROM inventory i JOIN inventory_balance_sources b ON b.inventory_id=i.id');
    for(const row of balances){const balance=source.inventory_balances.find(b=>b.branch_id===row.source_branch_id&&b.product_id===row.source_product_id),product=source.products.find(p=>p.id===row.source_product_id);assert.equal(quantityMilli(row.quantity),quantityMilli(balance?.total_quantity??0));assert.equal(quantityMilli(row.minimum_stock),quantityMilli(product.minimum_stock));}
    const [[pairs]]=await db.query("SELECT COUNT(*) AS n FROM inventory_movements a JOIN inventory_movements b ON b.id=a.transfer_counterpart_id WHERE b.transfer_counterpart_id=a.id AND a.branch_id<>b.branch_id AND a.product_id=b.product_id AND a.quantity=-b.quantity AND a.movement_type='TRANSFER_OUT' AND b.movement_type='TRANSFER_IN'");assert.equal(pairs.n,result.counts.transfer_pairs);
    const [[zero]]=await db.query('SELECT COUNT(*) AS n FROM inventory_counts WHERE adjustment_quantity=0 AND movement_id IS NULL');assert.equal(zero.n,source.inventory_counts.filter(row=>quantityMilli(row.adjustment_quantity)===0n).length);
    assert.equal((await db.query('SELECT COUNT(*) AS n FROM sales'))[0][0].n,salesBefore.n);
    const [[enabled]]=await db.query('SELECT COUNT(*) AS n FROM branches WHERE inventory_enabled=1');assert.equal(enabled.n,0);
    await db.query('UPDATE inventory SET quantity=quantity+1 WHERE id=(SELECT inventory_id FROM inventory_balance_sources LIMIT 1)');await assert.rejects(importInventory(db,bytes,{...options,apply:true}),/INVENTORY_TARGET_CHANGED/);
  }finally{if(created)await db.query(`DROP DATABASE ${database}`);await db.end();}
});
