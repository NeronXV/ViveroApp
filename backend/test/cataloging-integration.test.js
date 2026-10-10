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

if(process.env.TEST_SCOPE!=='cataloging-native-synthetic' || process.env.DB_HOST!=='127.0.0.1' || !['43387','43388'].includes(process.env.DB_PORT))throw new Error('Dedicated native synthetic database required');
const port=Number(process.env.DB_PORT);
const root=resolve(import.meta.dirname,'../..'), output=resolve(root,'tmp/cataloging-mobile-20261010',port===43388 ? 'mariadb114' : '.');
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
  const config=JSON.parse(await readFile(resolve(output,'native-db.private.json'),'utf8'));
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
  for(const file of migrations.filter(n=>!n.startsWith('034_')))await admin.query(await readFile(resolve(root,'database/mysql/migrations',file),'utf8'));
  const [beforeProducts]=await admin.query('SELECT * FROM products ORDER BY id');
  const [beforeInventory]=await admin.query('SELECT * FROM inventory ORDER BY id');
  await admin.query(await readFile(resolve(root,'database/mysql/migrations/034_cataloging_drafts.sql'),'utf8'));
  const [afterProducts]=await admin.query('SELECT * FROM products ORDER BY id');
  assert.deepEqual(afterProducts.map(({catalog_revision,...p})=>p),beforeProducts);
  assert.deepEqual((await admin.query('SELECT * FROM inventory ORDER BY id'))[0],beforeInventory);
  people={};
  const hashed=await hashPassword(password);
  for(const role of ['owner','manager','inventory','cashier']){
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
