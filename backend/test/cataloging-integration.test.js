// Dedicated, fresh local native database only. Never uses .env or a VPS.
import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import mysql from 'mysql2/promise';
import sharp from 'sharp';
import { randomBytes } from 'node:crypto';
import { createApp } from '../src/app.js';
import { createImageStore } from '../src/images.js';
import { hashPassword } from '../src/auth/password.js';
import { importCatalogingDrafts } from '../scripts/cataloging-import.js';

if(process.env.TEST_SCOPE!=='cataloging-native-synthetic' || process.env.DB_HOST!=='127.0.0.1' || !['43387','43388','43389'].includes(process.env.DB_PORT))throw new Error('Dedicated native synthetic database required');
const port=Number(process.env.DB_PORT);
const root=resolve(import.meta.dirname,'../..');
if(port===43389 && !/^plants-contract-[a-f0-9]{32}$/.test(process.env.CATALOGING_TEST_RUN ?? ''))throw new Error('Fresh synthetic run directory required');
const output=port===43389 ? resolve(root,'tmp',process.env.CATALOGING_TEST_RUN) : resolve(root,'tmp/cataloging-mobile-20261010',port===43388 ? 'mariadb114' : '.');
let admin,pool,server,origin,people,category,product;
const password='Synthetic-cataloging-password-034';
const key=()=>randomBytes(24).toString('hex');
async function call(path,method='GET',body,person='manager',k=key(),headers={}) {
  const actor=people[person];
  const response=await fetch(origin+'/api/v1/'+path,{method,headers:{Authorization:`Bearer ${actor.token}`,'Content-Type':'application/json',
    'X-Expected-Actor-Id':String(actor.id),'X-Expected-Branch-Id':'1','Idempotency-Key':k,...headers},...(body===undefined?{}:{body:JSON.stringify(body)})});
  return {status:response.status,data:await response.json()};
}
const fields=()=>({approved_name:'Planta sintética',presentation:'M06',internal_code:'CAT-034-SYNTHETIC',category_id:category});
const create=async()=>call('cataloging','POST',{original_name:'Original literal sintético',fields:{}});
const edit=(d,action,f={},person='manager',k=key(),extra={})=>call(`cataloging/${d.id}`,'PATCH',{revision:d.revision,action,fields:f,...extra},person,k);
before(async()=>{
  const config=JSON.parse((await readFile(resolve(output,'native-db.private.json'),'utf8')).replace(/^\uFEFF/,''));
  admin=await mysql.createConnection({host:'127.0.0.1',port,user:'root',password:config.password,multipleStatements:true,supportBigNumbers:true,bigNumberStrings:true});
  const [[existing]]=await admin.query("SELECT COUNT(*) n FROM information_schema.schemata WHERE schema_name='vivero'");
  assert.equal(Number(existing.n),0,'Refuses an existing database');
  await admin.query("SET GLOBAL time_zone = '+00:00'");
  await admin.query("CREATE DATABASE vivero CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; USE vivero;");
  await admin.query("CREATE USER 'catalog_api'@'%' IDENTIFIED BY 'Synthetic-api-database-034';");
  await admin.query(await readFile(resolve(root,'database/mysql/schema.sql'),'utf8'));
  await admin.query(await readFile(resolve(root,'database/mysql/seed.sql'),'utf8'));
  await admin.query(await readFile(resolve(root,'database/mysql/grants.sql'),'utf8'));
  const migrations=(await readdir(resolve(root,'database/mysql/migrations'))).filter(n=>/^\d{3}_.*\.sql$/.test(n)).sort();
  for(const file of migrations.filter(n=>n<'034_'))await admin.query(await readFile(resolve(root,'database/mysql/migrations',file),'utf8'));
  const [beforeProducts]=await admin.query('SELECT * FROM products ORDER BY id');
  const [beforeInventory]=await admin.query('SELECT * FROM inventory ORDER BY id');
  await admin.query(await readFile(resolve(root,'database/mysql/migrations/034_cataloging_drafts.sql'),'utf8'));
  const [afterProducts]=await admin.query('SELECT * FROM products ORDER BY id');
  assert.deepEqual(afterProducts.map(({catalog_revision,...p})=>p),beforeProducts);
  assert.deepEqual((await admin.query('SELECT * FROM inventory ORDER BY id'))[0],beforeInventory);
  for(const file of migrations.filter(n=>n>'034_zz'))await admin.query(await readFile(resolve(root,'database/mysql/migrations',file),'utf8'));
  people={};
  await admin.query("INSERT INTO branches(id,code,name,is_active) VALUES(2,'SYNTHETIC-SECOND','Sucursal sintética de pruebas',1)");
  const hashed=await hashPassword(password);
  for(const role of ['owner','admin','manager','inventory','cashier']){
    const [u]=await admin.execute('INSERT INTO users(email,full_name,password_hash,role_id,branch_id,is_active) VALUES(?,?,?,(SELECT id FROM roles WHERE name=?),1,1)',
      [`${role}-034@example.invalid`,`${role} sintético`,hashed,role.toUpperCase()]);people[role]={id:u.insertId};
  }
  category=1; product=1;
  await mkdir(resolve(output,'images'),{recursive:true});
  pool=mysql.createPool({host:'127.0.0.1',port,database:'vivero',user:'catalog_api',password:'Synthetic-api-database-034',supportBigNumbers:true,bigNumberStrings:true,timezone:'Z',connectionLimit:5});
  server=createApp({db:pool,imageStore:createImageStore(resolve(output,'images')),webOrigin:'http://127.0.0.1:42551'});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));origin=`http://127.0.0.1:${server.address().port}`;
  for(const name of Object.keys(people)) {
    const response=await fetch(origin+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`${name}-034@example.invalid`,password})});
    assert.equal(response.status,200);people[name].token=(await response.json()).access_token;
  }
  await writeFile(resolve(output,'http-fixture.private.json'),JSON.stringify({origin,people,password}),{flag:'wx'});
});
after(async()=>{if(server)await new Promise(r=>server.close(r));if(pool)await pool.end();if(admin)await admin.end();});

test('actual permissions, login and identity guards isolate cataloging',async()=>{
  assert.equal((await create()).status,201);
  assert.equal((await call('cataloging','POST',{original_name:'Synthetic',fields:{}},'cashier')).status,403);
  assert.equal((await call('cataloging','POST',{original_name:'Synthetic',fields:{}},'manager',key(),{'X-Expected-Actor-Id':'999'})).status,409);
  const [[n]]=await admin.query('SELECT COUNT(*) n FROM inventory');assert.equal(Number(n.n),2);
});

test('health requires both cataloging migrations, not just one matching row',async()=>{
  assert.equal((await fetch(origin+'/health')).status,200);
  await admin.query("UPDATE schema_migrations SET version='synthetic_hidden_035' WHERE version='035_cataloging_publish'");
  try { assert.equal((await fetch(origin+'/health')).status,503); }
  finally { await admin.query("UPDATE schema_migrations SET version='035_cataloging_publish' WHERE version='synthetic_hidden_035'"); }
  assert.equal((await fetch(origin+'/health')).status,200);
});
test('same attempt recovers create/update; two users cannot silently overwrite',async()=>{
  const k=key(),body={original_name:'Replay original',fields:{}};
  const first=await call('cataloging','POST',body,'manager',k),again=await call('cataloging','POST',body,'manager',k);
  assert.equal(first.status,201);assert.deepEqual(again.data,first.data);
  assert.equal((await call('cataloging','POST',body,'owner',k)).status,409);
  const saved=await edit(first.data,'SAVE',{presentation:'B02'});assert.equal(saved.status,200);
  assert.equal((await edit(first.data,'SAVE',{presentation:'M06'},'inventory')).status,409);
  assert.equal((await call(`cataloging/${first.data.id}`)).data.fields.presentation,'B02');
});
test('incomplete review cannot create product; only owner prepares an inactive pending product',async()=>{
  const d=(await create()).data,submitted=await edit(d,'SUBMIT');assert.equal(submitted.status,200);
  assert.equal((await edit(submitted.data,'PREPARE',fields(),'manager')).status,403);
  assert.equal((await edit(submitted.data,'PREPARE',{},'owner')).status,400);
  const result=await edit(submitted.data,'PREPARE',fields(),'owner');assert.equal(result.status,200);
  const [[p]]=await admin.execute('SELECT * FROM products WHERE id=?',[result.data.product_id]);
  assert.equal(p.is_active,0);assert.equal(p.preparation_mode,'PENDING');assert.equal(Number(p.price_cents),0);assert.equal(p.commercial_disabled_at,null);
  const [[stock]]=await admin.execute('SELECT COUNT(*) n FROM inventory WHERE product_id=?',[p.id]);assert.equal(Number(stock.n),0);
  const active=await fetch(origin+'/api/v1/products');assert.ok(!(await active.json()).items.some(item=>item.id===p.id));
});
test('explicit owner link keeps existing product and inventory exactly unchanged',async()=>{
  const d=(await create()).data,s=(await edit(d,'SUBMIT')).data;
  const [[before]]=await admin.execute('SELECT * FROM products WHERE id=?',[product]);
  const r=await edit(s,'LINK',{},'owner',key(),{product_id:product});assert.equal(r.status,200);
  assert.deepEqual((await admin.execute('SELECT * FROM products WHERE id=?',[product]))[0][0],before);
});
test('draft photo persists privately, repeats safely and transfers to prepared product',async()=>{
  const d=(await create()).data,k=key(),png=await sharp({create:{width:4,height:4,channels:3,background:'#376b45'}}).png().toBuffer();
  const upload=()=>fetch(`${origin}/api/v1/cataloging/${d.id}/photo`,{method:'PUT',headers:{Authorization:`Bearer ${people.manager.token}`,'Content-Type':'image/png',
    'X-Expected-Actor-Id':String(people.manager.id),'X-Expected-Branch-Id':'1','Idempotency-Key':k,'X-Cataloging-Revision':String(d.revision)},body:png});
  const one=await upload();assert.equal(one.status,200);const saved=await one.json();
  assert.deepEqual(await (await upload()).json(),saved);
  assert.equal((await fetch(`${origin}/api/v1/cataloging/${d.id}/photo`)).status,401);
  const submitted=(await edit(saved,'SUBMIT',{...fields(),internal_code:'PHOTO-034'})).data;
  const prepared=await edit(submitted,'PREPARE',submitted.fields,'owner');assert.equal(prepared.status,200);
  const [photos]=await admin.execute('SELECT * FROM product_images WHERE product_id=?',[prepared.data.product_id]);assert.equal(photos.length,1);
});
test('product concurrency also catches edits made by existing API clients',async()=>{
  const v=(await call(`products/${product}/edit-version`)).data.revision;
  assert.equal((await call(`products/${product}`,'PATCH',{description:'Concurrent existing-client edit'})).status,200);
  assert.equal((await call(`products/${product}`,'PATCH',{description:'Outdated overwrite'},'manager',key(),{'If-Match':`"${v}"`})).status,409);
});
test('local manifest import reuses source IDs and preserves later human changes',async()=>{
  const manifest={schema_version:1,source_key:'synthetic-native',items:[{source_id:'COT-SYNTHETIC-1',original_name:'Literal original',fields:{},source_evidence:{presentation:'B02',sku_propuesto:'NOT-APPROVED'}}]};
  const preview=await importCatalogingDrafts(admin,manifest,people.owner.id);assert.equal(preview.items[0].action,'create');
  const applied=await importCatalogingDrafts(admin,manifest,people.owner.id,{apply:true});
  const id=applied.items[0].id;
  await admin.execute('UPDATE cataloging_drafts SET fields=?,revision=revision+1 WHERE id=?',[JSON.stringify({presentation:'B02'}),id]);
  const reused=await importCatalogingDrafts(admin,manifest,people.owner.id,{apply:true});assert.equal(reused.items[0].action,'reuse');
  assert.equal(JSON.parse((await admin.execute('SELECT fields FROM cataloging_drafts WHERE id=?',[id]))[0][0].fields).presentation,'B02');
});
test('Caja still quotes and charges legacy products once; pending products cannot be sold',async()=>{
  const pending=(await admin.query("SELECT id FROM products WHERE preparation_mode='PENDING' ORDER BY id LIMIT 1"))[0][0].id;
  assert.equal((await call('sales/quote','POST',{items:[{product_id:pending,quantity:1}]})).status,409);
  const items=[{product_id:product,quantity:1}],quote=await call('sales/quote','POST',{items});assert.equal(quote.status,200);
  const saleKey=randomBytes(32).toString('hex'),sale=await call('sales','POST',{items,expected_total_cents:quote.data.total_cents},'manager',saleKey);
  assert.equal(sale.status,201);
  const id=sale.data.id,claim=await call(`cashier/sales/${id}/claim`,'POST',{claim_token:null},'cashier');assert.equal(claim.status,200);
  const paymentKey=randomBytes(32).toString('hex'),body={claim_token:claim.data.claim_token,method:'CASH',amount_received_cents:quote.data.total_cents,reference:null};
  const paid=await call(`cashier/sales/${id}/payments`,'POST',body,'cashier',paymentKey);assert.equal(paid.status,201);
  const replay=await call(`cashier/sales/${id}/payments`,'POST',body,'cashier',paymentKey);assert.equal(replay.status,200);assert.equal(replay.data.payment.id,paid.data.payment.id);
  const [[n]]=await admin.execute('SELECT COUNT(*) n FROM cashier_payments WHERE sale_id=?',[id]);assert.equal(Number(n.n),1);
  const [[balance]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);assert.equal(balance.quantity,'10.000');
});

const publish=(d,body={},person='manager',k=key())=>call(`cataloging/${d.id}/publish`,'POST',{
  revision:d.revision,fields:{...fields(),internal_code:''},price_cents:12500,initial_quantity:7,...body,
},person,k);
test('unified confirmation publishes a ficha once without creating inventory or movements',async()=>{
  const draft=(await create()).data,k=key();
  const body={target:'draft',id:draft.id,revision:draft.revision,fields:{...fields(),internal_code:''},price_cents:12900};
  const first=await call('plants/confirm','POST',body,'manager',k);
  assert.equal(first.status,200,JSON.stringify(first.data));
  assert.equal(first.data.publication.ready,false);
  assert.equal(first.data.publication.needs_initial_count,true);
  const replay=await call('plants/confirm','POST',body,'manager',k);
  assert.deepEqual(replay.data,first.data);
  assert.equal((await call('plants/confirm','POST',body)).status,409);
  const id=first.data.product_id;
  for(const table of ['inventory','inventory_movements','inventory_counts']){
    const [[n]]=await admin.execute(`SELECT COUNT(*) n FROM ${table} WHERE product_id=?`,[id]);
    assert.equal(Number(n.n),0,table);
  }
  const [[p]]=await admin.execute('SELECT is_active,price_confirmed_at FROM products WHERE id=?',[id]);
  assert.equal(p.is_active,0);assert.ok(p.price_confirmed_at);
});
test('unified confirmation activates only after a previously confirmed initial count',async()=>{
  const draft=(await create()).data;
  const created=await call('plants/confirm','POST',{target:'draft',id:draft.id,revision:draft.revision,
    fields:{...fields(),internal_code:''},price_cents:12500});
  assert.equal(created.status,200,JSON.stringify(created.data));
  const id=created.data.product_id;
  await admin.execute('INSERT INTO product_branch_preparation(product_id,branch_id,initial_count_confirmed_at,initial_count_confirmed_by) VALUES(?,1,UTC_TIMESTAMP(6),?)',[id,people.manager.id]);
  const revision=(await call(`products/${id}/edit-version`)).data.revision;
  const body={target:'product',id,revision,fields:{common_name:'Planta sintética · M06',category_id:category,
    description:'Ficha completa',light_type:'Indirecta',watering_advice:'Cada semana'},price_cents:12500};
  const k=key(),first=await call('plants/confirm','POST',body,'manager',k);
  assert.equal(first.status,200,JSON.stringify(first.data));assert.equal(first.data.ready,true);
  assert.deepEqual((await call('plants/confirm','POST',body,'manager',k)).data,first.data);
  const [[p]]=await admin.execute('SELECT is_active FROM products WHERE id=?',[id]);assert.equal(p.is_active,1);
  for(const table of ['inventory_movements','inventory_counts']){
    const [[n]]=await admin.execute(`SELECT COUNT(*) n FROM ${table} WHERE product_id=?`,[id]);assert.equal(Number(n.n),0,table);
  }
});
test('unified link reuses a catalog product and its price without adding inventory',async()=>{
  const draft=(await create()).data,k=key();
  const [[beforeProduct]]=await admin.execute('SELECT price_cents FROM products WHERE id=?',[product]);
  const [[beforeStock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  const [[beforeMovements]]=await admin.execute('SELECT COUNT(*) n FROM inventory_movements WHERE branch_id=1 AND product_id=?',[product]);
  const body={target:'draft',id:draft.id,revision:draft.revision,fields:{...fields(),internal_code:''},price_cents:0,product_id:product};
  const first=await call('plants/confirm','POST',body,'manager',k);
  assert.equal(first.status,200,JSON.stringify(first.data));assert.equal(first.data.product_id,product);
  assert.equal(first.data.status,'LINKED');
  assert.equal(first.data.publication.price_cents,Number(beforeProduct.price_cents));
  assert.deepEqual((await call('plants/confirm','POST',body,'manager',k)).data,first.data);
  const [[afterStock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  const [[afterMovements]]=await admin.execute('SELECT COUNT(*) n FROM inventory_movements WHERE branch_id=1 AND product_id=?',[product]);
  assert.equal(afterStock.quantity,beforeStock.quantity);assert.equal(afterMovements.n,beforeMovements.n);
});
async function assertSinglePublication(result,quantity=7) {
  const id=result.product_id;
  assert.ok(id);
  const [[p]]=await admin.execute('SELECT is_active,price_cents FROM products WHERE id=?',[id]);
  assert.equal(p.is_active,1);assert.equal(Number(p.price_cents),12500);
  const [[stock]]=await admin.execute('SELECT quantity FROM inventory WHERE product_id=? AND branch_id=1',[id]);
  assert.equal(stock.quantity,`${quantity}.000`);
  for(const table of ['inventory_movements','inventory_counts','product_branch_preparation']) {
    const [[n]]=await admin.execute(`SELECT COUNT(*) n FROM ${table} WHERE product_id=?`,[id]);assert.equal(Number(n.n),1,table);
  }
}
test('publish response loss, concurrent same-key retries and different-key retries create one product and one stock operation',async()=>{
  const d=(await create()).data,k=key();
  const results=await Promise.all([publish(d,{},'manager',k),publish(d,{},'manager',k)]);
  for(const r of results)assert.equal(r.status,200,JSON.stringify(r.data));
  assert.deepEqual(results[0].data,results[1].data);
  const replay=await publish(d,{},'manager',k);assert.deepEqual(replay.data,results[0].data);
  assert.equal((await publish(d)).status,409);
  assert.equal((await publish(d,{initial_quantity:8},'manager',k)).data.error,'CATALOGING_KEY_CONFLICT');
  assert.deepEqual(replay.data.publication,{branch_id:1,price_cents:12500,initial_quantity:7,ready:true});
  await assertSinglePublication(replay.data);
  const current=(await call(`cataloging/${d.id}`)).data;
  assert.equal(current.status,'PREPARED');assert.equal(current.revision,2);
  assert.equal((await publish(current)).data.error,'CATALOGING_CLOSED');
});
test('two authorized actors publishing the same revision cannot create duplicate products',async()=>{
  const d=(await create()).data;
  const results=await Promise.all([publish(d),publish(d,{},'owner')]);
  assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
  await assertSinglePublication(results.find(r=>r.status===200).data);
});
test('existing publisher capabilities complete REVIEW, preserve omitted fields and publish; workers cannot publish or link',async()=>{
  for(const actor of ['manager','admin','owner']) {
    const d=(await create()).data;
    const submitted=await edit(d,'SUBMIT',{...fields(),internal_code:'',suggested_price_cents:12500,initial_quantity:7},'inventory');
    assert.equal(submitted.status,200);
    assert.equal((await edit(submitted.data,'SAVE',{description:'Denied'},'inventory')).data.error,'CATALOGING_IN_REVIEW');
    assert.equal((await edit(submitted.data,'SUBMIT',{description:'Cannot bypass review with submit'},'inventory')).data.error,'CATALOGING_IN_REVIEW');
    const saved=await edit(submitted.data,'SAVE',{description:'Conserva precio y existencia'},actor);
    assert.equal(saved.status,200);assert.equal(saved.data.status,'REVIEW');
    assert.equal(saved.data.fields.suggested_price_cents,12500);assert.equal(saved.data.fields.initial_quantity,7);
    assert.equal((await publish(saved.data,{},'inventory')).status,403);
    assert.equal((await edit(saved.data,'LINK',{},'inventory',key(),{product_id:product})).status,403);
    const published=await publish(saved.data,{},actor);assert.equal(published.status,200,JSON.stringify(published.data));
    await assertSinglePublication(published.data);
  }
});
test('publisher links DRAFT and REVIEW without changing product, movements or stock',async()=>{
  const [productsBefore]=await admin.query('SELECT * FROM products ORDER BY id');
  const [stockBefore]=await admin.query('SELECT * FROM inventory ORDER BY id');
  const [[movementsBefore]]=await admin.query('SELECT COUNT(*) n FROM inventory_movements');
  for(const status of ['DRAFT','REVIEW']) {
    let d=(await create()).data;if(status==='REVIEW')d=(await edit(d,'SUBMIT')).data;
    const k=key(),r=await edit(d,'LINK',{},'manager',k,{product_id:product});assert.equal(r.status,200);
    assert.equal(r.data.status,'LINKED');
    assert.deepEqual((await edit(d,'LINK',{},'manager',k,{product_id:product})).data,r.data);
    assert.equal((await publish(r.data)).data.error,'CATALOGING_CLOSED');
  }
  assert.deepEqual((await admin.query('SELECT * FROM products ORDER BY id'))[0],productsBefore);
  assert.deepEqual((await admin.query('SELECT * FROM inventory ORDER BY id'))[0],stockBefore);
  assert.deepEqual((await admin.query('SELECT COUNT(*) n FROM inventory_movements'))[0][0],movementsBefore);
});
test('autogenerated SKU still checks barcode collision and failed publication leaves no partial effects',async()=>{
  const d=(await create()).data;
  const [[existing]]=await admin.execute('SELECT internal_code FROM products WHERE id=?',[product]);
  const [[before]]=await admin.query('SELECT COUNT(*) n FROM products');
  const failed=await publish(d,{fields:{...fields(),internal_code:'',barcode:existing.internal_code}});
  assert.equal(failed.status,409);assert.equal(failed.data.error,'CATALOGING_PRODUCT_EXISTS');
  assert.deepEqual((await admin.query('SELECT COUNT(*) n FROM products'))[0][0],before);
  assert.deepEqual((await call(`cataloging/${d.id}`)).data,d);
});
test('transaction rollback after product and stock writes permits a clean retry with the same key',async()=>{
  const d=(await create()).data,k=key();
  const [[before]]=await admin.query('SELECT COUNT(*) n FROM products');
  await admin.query("CREATE TRIGGER synthetic_reject_cataloging_event BEFORE INSERT ON cataloging_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic transaction failure'");
  try { assert.equal((await publish(d,{},'manager',k)).status,503); }
  finally { await admin.query('DROP TRIGGER synthetic_reject_cataloging_event'); }
  assert.deepEqual((await admin.query('SELECT COUNT(*) n FROM products'))[0][0],before);
  assert.deepEqual((await call(`cataloging/${d.id}`)).data,d);
  const result=await publish(d,{},'manager',k);assert.equal(result.status,200);await assertSinglePublication(result.data);
});
test('publication without price or positive count creates an inactive product without invented stock',async()=>{
  for(const body of [{price_cents:null,initial_quantity:null},{price_cents:12500,initial_quantity:0},{price_cents:null,initial_quantity:7}]) {
    const d=(await create()).data,r=await publish(d,body);assert.equal(r.status,200);
    assert.equal(r.data.publication.ready,false);
    const [[p]]=await admin.execute('SELECT is_active FROM products WHERE id=?',[r.data.product_id]);assert.equal(p.is_active,0);
    const [[n]]=await admin.execute('SELECT COUNT(*) n FROM inventory_movements WHERE product_id=?',[r.data.product_id]);
    assert.equal(Number(n.n),body.initial_quantity>0?1:0);
  }
});
test('photo on REVIEW is allowed to publisher and replays after publication without duplicate images',async()=>{
  const d=(await edit((await create()).data,'SUBMIT')).data,k=key();
  const png=await sharp({create:{width:4,height:4,channels:3,background:'#376b45'}}).png().toBuffer();
  const upload=(person='manager')=>fetch(`${origin}/api/v1/cataloging/${d.id}/photo`,{method:'PUT',headers:{Authorization:`Bearer ${people[person].token}`,'Content-Type':'image/png',
    'X-Expected-Actor-Id':String(people[person].id),'X-Expected-Branch-Id':'1','Idempotency-Key':k,'X-Cataloging-Revision':String(d.revision)},body:png});
  assert.equal((await upload('inventory')).status,409);
  const response=await upload();assert.equal(response.status,200);const saved=await response.json();
  const published=await publish(saved);assert.equal(published.status,200);
  assert.deepEqual(await (await upload()).json(),saved);
  const [[n]]=await admin.execute('SELECT COUNT(*) n FROM product_images WHERE product_id=?',[published.data.product_id]);assert.equal(Number(n.n),1);
});

test('publication recovery is tied to the original branch, including legacy receipts',async()=>{
  const d=(await create()).data,k=key(),body={revision:d.revision,fields:{...fields(),internal_code:''},price_cents:12500,initial_quantity:7};
  const first=await call(`cataloging/${d.id}/publish`,'POST',body,'manager',k);assert.equal(first.status,200);
  for(const legacy of [false,true]) {
    if(legacy) await admin.execute("UPDATE cataloging_events SET result=JSON_REMOVE(result,'$.publication') WHERE draft_id=? AND JSON_UNQUOTE(JSON_EXTRACT(result,'$.status'))='PREPARED'",[d.id]);
    await admin.execute('UPDATE users SET branch_id=2 WHERE id=?',[people.manager.id]);
    try {
      const result=await call(`cataloging/${d.id}/publish`,'POST',body,'manager',k,{'X-Expected-Branch-Id':'2'});
      assert.equal(result.status,409);assert.equal(result.data.error,'CATALOGING_SESSION_CHANGED');
    } finally { await admin.execute('UPDATE users SET branch_id=1 WHERE id=?',[people.manager.id]); }
    const recovered=await call(`cataloging/${d.id}/publish`,'POST',body,'manager',k);assert.equal(recovered.status,200);
    assert.equal(recovered.data.product_id,first.data.product_id);
  }
  await assertSinglePublication(first.data);
});

test('retry after capability revocation never bypasses current authorization',async()=>{
  const d=(await create()).data,k=key();const first=await publish(d,{},'manager',k);assert.equal(first.status,200);
  await admin.execute("UPDATE users SET role_id=(SELECT id FROM roles WHERE name='INVENTORY') WHERE id=?",[people.manager.id]);
  try { assert.equal((await publish(d,{},'manager',k)).status,403); }
  finally { await admin.execute("UPDATE users SET role_id=(SELECT id FROM roles WHERE name='MANAGER') WHERE id=?",[people.manager.id]); }
  assert.deepEqual((await publish(d,{},'manager',k)).data,first.data);
  await assertSinglePublication(first.data);
});

async function arrival(reference=key(),quantities=[10,10,10]) {
  const supplier=await call('suppliers','POST',{code:`S-${key().slice(0,12)}`,name:'Proveedor sintético',is_active:true});
  assert.equal(supplier.status,201,JSON.stringify(supplier.data));
  const input={supplier_id:supplier.data.supplier.id,document_date:'2026-10-15',external_reference:reference,payment_terms:'OTHER',expected_total_cents:quantities.reduce((sum,quantity)=>sum+quantity*100,0),source_file_name:'synthetic.pdf',items:[1,2,3].map(n=>({line_number:n,raw_description:`Sintética ${n}`,container_code:'M6',suggested_common_name:null,suggested_presentation:null,quantity:quantities[n-1],unit_cost_cents:100}))};
  const d=await call('supplier-purchases','POST',input);assert.equal(d.status,201,JSON.stringify(d.data));
  return {d:d.data.purchase,input};
}
test('arrival review learns supplier equivalences atomically and reuses them without changing stock or prices',async()=>{
  const {d,input}=await arrival();
  const [[before]]=await admin.execute('SELECT quantity FROM inventory WHERE product_id=? AND branch_id=1',[product]);
  const [[price]]=await admin.execute('SELECT price_cents FROM products WHERE id=?',[product]);
  const edit={revision:d.revision,items:d.items.map((i,n)=>({id:i.id,quantity:n===2?0:2,product_id:n===0?product:null,
    new_product:n===1?{name:'Nueva sintética',presentation:null,category_id:null}:null}))};
  // A later unavailable product must roll back the earlier alias as well as the review.
  const invalid={...edit,items:edit.items.map((i,n)=>n===1?{...i,product_id:4294967000,new_product:null}:i)};
  assert.equal((await call(`supplier-purchases/${d.id}`,'PATCH',invalid)).status,409);
  const aliases=async()=> (await admin.execute('SELECT product_id FROM supplier_product_aliases WHERE supplier_id=?',[d.supplier.id]))[0];
  assert.deepEqual(await aliases(),[]);
  const reviewed=await call(`supplier-purchases/${d.id}`,'PATCH',edit);
  assert.equal(reviewed.status,200,JSON.stringify(reviewed.data));
  assert.deepEqual((await call(`supplier-purchases/${d.id}`,'PATCH',edit)).data,reviewed.data);
  assert.deepEqual(await aliases(),[{product_id:product}]);
  const next=await call('supplier-purchases','POST',{...input,external_reference:key()});
  assert.equal(next.status,201,JSON.stringify(next.data));
  assert.equal(next.data.purchase.items[0].resolution_status,'AUTO_MATCHED');
  assert.equal(next.data.purchase.items[0].product_id,product);
  assert.ok(next.data.purchase.items.slice(1).every(i=>i.resolution_status==='UNMATCHED'));
  const other=await arrival();assert.ok(other.d.items.every(i=>i.resolution_status==='UNMATCHED'));
  const zero={revision:reviewed.data.purchase.revision,items:edit.items.map(i=>({...i,quantity:0,product_id:null,new_product:null}))};
  assert.equal((await call(`supplier-purchases/${d.id}`,'PATCH',zero)).status,200);
  assert.deepEqual(await aliases(),[{product_id:product}]);
  assert.deepEqual((await admin.execute('SELECT quantity FROM inventory WHERE product_id=? AND branch_id=1',[product]))[0][0],before);
  assert.deepEqual((await admin.execute('SELECT price_cents FROM products WHERE id=?',[product]))[0][0],price);
});

test('arrival binds new plants and adds actual quantities once; completing the same product does not add stock',async()=>{
  const {d,input}=await arrival();
  const [[before]]=await admin.execute('SELECT quantity FROM inventory WHERE product_id=? AND branch_id=1',[product]);
  const newPlant={name:'Planta nueva del camión',presentation:'Maceta 6',category_id:category};
  const edit={revision:d.revision,items:d.items.map((i,n)=>({id:i.id,quantity:n===0?4:3,product_id:n===0?product:null,new_product:n===0?null:newPlant}))};
  const revised=await call(`supplier-purchases/${d.id}`,'PATCH',edit);assert.equal(revised.status,200,JSON.stringify(revised.data));
  const again=await call(`supplier-purchases/${d.id}`,'PATCH',edit);assert.deepEqual(again.data,revised.data);
  assert.equal((await call(`supplier-purchases/${d.id}`,'PATCH',{...edit,items:edit.items.map(i=>({...i,quantity:5}))})).status,409);
  const body={revision:revised.data.purchase.revision},k=key();
  const results=await Promise.all([call(`supplier-purchases/${d.id}/confirm`,'POST',body,'manager',k),call(`supplier-purchases/${d.id}/confirm`,'POST',body,'manager',k)]);
  for(const r of results)assert.ok([200,201].includes(r.status),JSON.stringify(r.data));
  const receipt=results[0].data.purchase;
  assert.equal(receipt.items[1].product_id,receipt.items[2].product_id);
  const pid=receipt.items[1].product_id;
  const [[stock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[pid]);assert.equal(stock.quantity,'6.000');
  const [[existing]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);assert.equal(Number(existing.quantity),Number(before.quantity)+4);
  const [[p]]=await admin.execute('SELECT is_active,price_cents FROM products WHERE id=?',[pid]);assert.equal(p.is_active,0);assert.equal(Number(p.price_cents),0);
  const rev=(await call(`products/${pid}/edit-version`)).data.revision,editKey=key(),patch={price_cents:2500,description:'Información completada desde el celular'};
  const saved=await call(`products/${pid}`,'PATCH',patch,'manager',editKey,{'If-Match':`"${rev}"`});assert.equal(saved.status,200,JSON.stringify(saved.data));
  assert.deepEqual((await call(`products/${pid}`,'PATCH',patch,'manager',editKey,{'If-Match':`"${rev}"`})).data,saved.data);
  assert.equal((await call(`products/${pid}/preparation/activate`,'POST',{})).status,200);
  assert.deepEqual((await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[pid]))[0][0],stock);
  assert.equal((await call('supplier-purchases','POST',input)).status,409);
  assert.equal((await call(`supplier-purchases/${d.id}/confirm`,'POST',{revision:1},'manager',k)).status,409);
});

test('arrival keeps zero lines and stages unclassified stock until a manager confirms its draft once',async()=>{
  const {d}=await arrival();
  const [[before]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  const edit={revision:d.revision,items:d.items.map((i,n)=>({id:i.id,quantity:n===0?0:n===1?4:3,
    product_id:n===1?product:null,new_product:n===2?{name:'Nueva sin clasificar',presentation:null,category_id:null}:null}))};
  const revised=await call(`supplier-purchases/${d.id}`,'PATCH',edit);assert.equal(revised.status,200,JSON.stringify(revised.data));
  const confirmBody={revision:revised.data.purchase.revision},confirmKey=key();
  await admin.query("CREATE TRIGGER synthetic_pending_arrival_failure BEFORE UPDATE ON supplier_purchase_documents FOR EACH ROW BEGIN IF NEW.status='RECEIVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic pending failure'; END IF; END");
  try {assert.equal((await call(`supplier-purchases/${d.id}/confirm`,'POST',confirmBody,'manager',confirmKey)).status,503)}
  finally {await admin.query('DROP TRIGGER synthetic_pending_arrival_failure')}
  const [[rolledBack]]=await admin.execute("SELECT COUNT(*) AS n FROM cataloging_drafts WHERE source_key='supplier_purchase_item' AND source_id=?",[String(d.items[2].id)]);
  assert.equal(Number(rolledBack.n),0);
  const first=await call(`supplier-purchases/${d.id}/confirm`,'POST',confirmBody,'manager',confirmKey);
  assert.equal(first.status,201,JSON.stringify(first.data));
  const replay=await call(`supplier-purchases/${d.id}/confirm`,'POST',confirmBody,'manager',confirmKey);
  assert.deepEqual(replay.data.purchase,first.data.purchase);
  const [[ignored]]=await admin.execute('SELECT quantity,product_id,movement_id,resolution_status FROM supplier_purchase_items WHERE id=?',[d.items[0].id]);
  assert.equal(ignored.quantity,0);assert.equal(ignored.product_id,null);assert.equal(ignored.movement_id,null);assert.equal(ignored.resolution_status,'IGNORED');
  const [[pending]]=await admin.execute("SELECT id,fields FROM cataloging_drafts WHERE source_key='supplier_purchase_item' AND source_id=?",[String(d.items[2].id)]);
  assert.ok(pending);assert.equal(JSON.parse(pending.fields).initial_quantity,3);
  assert.equal((await call(`cataloging/${pending.id}`,'PATCH',{revision:1,action:'SAVE',fields:{initial_quantity:999}})).status,409);
  const [[unclassified]]=await admin.execute('SELECT product_id,movement_id FROM supplier_purchase_items WHERE id=?',[d.items[2].id]);
  assert.equal(unclassified.product_id,null);assert.equal(unclassified.movement_id,null);
  const [[known]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  assert.equal(Number(known.quantity),Number(before.quantity)+4);
  const publication={target:'draft',id:pending.id,revision:1,fields:{presentation:'Maceta 6',category_id:category},price_cents:2400};
  const publicationKey=key(),published=await call('plants/confirm','POST',publication,'manager',publicationKey);
  assert.equal(published.status,200,JSON.stringify(published.data));
  assert.deepEqual((await call('plants/confirm','POST',publication,'manager',publicationKey)).data,published.data);
  const [[stock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[published.data.product_id]);
  assert.equal(stock.quantity,'3.000');
  const [[movements]]=await admin.execute('SELECT COUNT(*) AS n FROM inventory_movements WHERE branch_id=1 AND product_id=?',[published.data.product_id]);
  assert.equal(Number(movements.n),1);
  const [[linked]]=await admin.execute('SELECT product_id,movement_id,resolution_status FROM supplier_purchase_items WHERE id=?',[d.items[2].id]);
  assert.equal(linked.product_id,published.data.product_id);assert.ok(linked.movement_id);assert.equal(linked.resolution_status,'MATCHED');
});

test('a document with only zero quantities stays intact and confirms without stock or plant drafts',async()=>{
  const {d}=await arrival(key(),[0,0,0]);
  assert.ok(d.items.every(i=>i.quantity===0&&i.resolution_status==='IGNORED'));
  const [[beforeMovements]]=await admin.execute('SELECT COUNT(*) AS n FROM inventory_movements');
  const [[beforeDrafts]]=await admin.execute("SELECT COUNT(*) AS n FROM cataloging_drafts WHERE source_key='supplier_purchase_item'");
  const first=await call(`supplier-purchases/${d.id}/confirm`,'POST',{revision:d.revision},'manager',key());
  assert.equal(first.status,201,JSON.stringify(first.data));
  assert.equal(first.data.purchase.status,'RECEIVED');
  assert.deepEqual((await admin.execute('SELECT COUNT(*) AS n FROM inventory_movements'))[0][0],beforeMovements);
  assert.deepEqual((await admin.execute("SELECT COUNT(*) AS n FROM cataloging_drafts WHERE source_key='supplier_purchase_item'"))[0][0],beforeDrafts);
});

test('a received draft can link an existing plant and transfer its quantity once',async()=>{
  const {d}=await arrival();
  const edit={revision:d.revision,items:d.items.map((i,n)=>({id:i.id,quantity:n===0?2:0,product_id:null,
    new_product:n===0?{name:'Planta por vincular',presentation:null,category_id:null}:null}))};
  const revised=await call(`supplier-purchases/${d.id}`,'PATCH',edit);assert.equal(revised.status,200,JSON.stringify(revised.data));
  const confirmed=await call(`supplier-purchases/${d.id}/confirm`,'POST',{revision:revised.data.purchase.revision});
  assert.equal(confirmed.status,201,JSON.stringify(confirmed.data));
  const [[pending]]=await admin.execute("SELECT id FROM cataloging_drafts WHERE source_key='supplier_purchase_item' AND source_id=?",[String(d.items[0].id)]);
  const [[before]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  const body={target:'draft',id:pending.id,revision:1,fields:{presentation:'Maceta 6',category_id:category},price_cents:0,product_id:product};
  const k=key(),first=await call('plants/confirm','POST',body,'manager',k);
  assert.equal(first.status,200,JSON.stringify(first.data));
  assert.deepEqual((await call('plants/confirm','POST',body,'manager',k)).data,first.data);
  const [[afterStock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[product]);
  assert.equal(Number(afterStock.quantity),Number(before.quantity)+2);
  const [[item]]=await admin.execute('SELECT product_id,movement_id FROM supplier_purchase_items WHERE id=?',[d.items[0].id]);
  assert.equal(item.product_id,product);assert.ok(item.movement_id);
});

test('arrival failure rolls back new products and all movements, then original key recovers',async()=>{
  const {d}=await arrival();
  const revised=await call(`supplier-purchases/${d.id}`,'PATCH',{revision:d.revision,items:d.items.map(i=>({id:i.id,quantity:2,product_id:null,new_product:{name:'Fallida',presentation:'M6',category_id:category}}))});
  assert.equal(revised.status,200,JSON.stringify(revised.data));
  const k=key(),body={revision:revised.data.purchase.revision};
  const [[before]]=await admin.query('SELECT COUNT(*) n FROM products');
  await admin.query("CREATE TRIGGER synthetic_receipt_failure BEFORE UPDATE ON supplier_purchase_documents FOR EACH ROW BEGIN IF NEW.status='RECEIVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic failure'; END IF; END");
  try {assert.equal((await call(`supplier-purchases/${d.id}/confirm`,'POST',body,'manager',k)).status,503)}finally{await admin.query('DROP TRIGGER synthetic_receipt_failure')}
  assert.deepEqual((await admin.query('SELECT COUNT(*) n FROM products'))[0][0],before);
  assert.equal((await call(`supplier-purchases/${d.id}/confirm`,'POST',body,'manager',k)).status,201);
});

test('product photo replays the exact receipt after response loss',async()=>{
  const rev=(await call(`products/${product}/edit-version`)).data.revision,k=key();
  const png=await sharp({create:{width:4,height:4,channels:3,background:'#376b45'}}).png().toBuffer();
  const upload=()=>fetch(`${origin}/api/v1/products/${product}/catalog-photo`,{method:'PUT',headers:{Authorization:`Bearer ${people.manager.token}`,'Content-Type':'image/png','X-Expected-Actor-Id':String(people.manager.id),'X-Expected-Branch-Id':'1','Idempotency-Key':k,'If-Match':`"${rev}"`},body:png});
  const first=await upload();assert.equal(first.status,200);const receipt=await first.json();
  const replay=await upload();assert.equal(replay.status,200);assert.deepEqual(await replay.json(),receipt);
});

test('direct registration and counts for trusted roles are atomic, audited and replay safe',async()=>{
  for(const person of ['owner','admin','manager']){
    const d=(await create()).data;
    const initial=person==='owner'?0:5;
    const body={target:'draft',id:d.id,revision:d.revision,fields:{approved_name:'Registro directo sintético',presentation:'Maceta',category_id:category},price_cents:15000,initial_quantity:initial};
    const k=key(),first=await call('plants/confirm','POST',body,person,k);
    assert.equal(first.status,200,JSON.stringify(first.data));
    assert.equal(first.data.publication.ready,true);
    assert.deepEqual((await call('plants/confirm','POST',body,person,k)).data,first.data);
    const pid=first.data.product_id;
    const [[stock]]=await admin.execute('SELECT quantity FROM inventory WHERE branch_id=1 AND product_id=?',[pid]);
    assert.equal(Number(stock.quantity),initial);
    const baseline=(await call(`inventory/count-baseline?product_id=${pid}`,'GET',undefined,person)).data;
    const input={product_id:pid,counted_quantity:'4',reason:'Conteo físico sintético',baseline_quantity:baseline.quantity,baseline_movement_id:baseline.movement_id};
    const countKey=key(),count=await call('inventory/direct-counts','POST',input,person,countKey);
    assert.equal(count.status,200,JSON.stringify(count.data));assert.equal(count.data.status,'APPLIED');
    const again=await call('inventory/direct-counts','POST',input,person,countKey);
    assert.equal(again.data.observation_id,count.data.observation_id);
    assert.equal((await call('inventory/direct-counts/result','POST',input,person,countKey)).data.observation_id,count.data.observation_id);
    assert.equal((await call('inventory/direct-counts','POST',input,person)).status,409);
    const history=await call(`plants/history?product_id=${pid}`,'GET',undefined,person);
    assert.equal(history.status,200,JSON.stringify(history.data));assert.equal(history.data.items.length,2);
    assert.equal(history.data.items[0].action,'DIRECT_COUNT');
    assert.equal(Number(history.data.items[0].before_values.quantity),initial);
    assert.equal(Number(history.data.items[0].after_values.quantity),4);
    assert.equal((await call('inventory/direct-counts','POST',input,'inventory')).status,403);
    assert.equal((await call(`plants/history?product_id=${pid}`,'GET',undefined,'cashier')).status,403);
  }
});

test('failed history write rolls back initial stock and allows same registration attempt',async()=>{
  const d=(await create()).data,k=key();
  const body={target:'draft',id:d.id,revision:d.revision,fields:{approved_name:'Atomicidad sintética',presentation:'Maceta',category_id:category},price_cents:12000,initial_quantity:5};
  const [[before]]=await admin.query('SELECT COUNT(*) n FROM inventory_movements');
  await admin.query("CREATE TRIGGER synthetic_history_failure BEFORE INSERT ON plant_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic history failure'");
  try {assert.equal((await call('plants/confirm','POST',body,'manager',k)).status,503)}
  finally {await admin.query('DROP TRIGGER synthetic_history_failure')}
  assert.deepEqual((await admin.query('SELECT COUNT(*) n FROM inventory_movements'))[0][0],before);
  assert.equal((await call(`cataloging/${d.id}`)).data.status,'DRAFT');
  assert.equal((await call('plants/confirm','POST',body,'manager',k)).status,200);
  await assert.rejects(pool.query("UPDATE plant_history SET reason='forbidden'"),e=>e.code==='ER_TABLEACCESS_DENIED_ERROR');
  await assert.rejects(pool.query('DELETE FROM plant_history'),e=>e.code==='ER_TABLEACCESS_DENIED_ERROR');
});
