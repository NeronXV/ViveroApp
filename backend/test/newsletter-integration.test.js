import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { createApp } from '../src/app.js';
import { sessionDigest } from '../src/auth/service.js';
import { newsletterCipher } from '../src/newsletter.js';

if(process.env.API_URL!=='http://api:3001'||process.env.DB_HOST!=='db'||process.env.DB_NAME!=='vivero') throw new Error('Newsletter tests require isolated local Compose');
test('newsletter HTTP/MariaDB: consent, expiry, snapshots, permissions, durable replay, unsubscribe and concurrency',async()=>{
  const config={host:'db',database:'vivero',user:'root',password:process.env.DB_PASSWORD};
  const root=await mysql.createConnection(config), db=mysql.createPool({...config,user:'catalog_api',password:process.env.CATALOG_DB_PASSWORD,connectionLimit:5});
  const suffix=randomBytes(6).toString('hex'), users=[], subscribers=[], campaigns=[], emails=[], messages=[], accepted=new Map();
  const linkKey='a'.repeat(64);
  let fail=false, hold=null;
  const sender=async(payload,key)=>{
    messages.push({payload,key});
    if(accepted.has(key)) assert.deepEqual(payload,accepted.get(key)); else accepted.set(key,payload);
    if(hold) await hold;
    if(fail) throw new Error('Synthetic uncertain provider response');
  };
  const server=createApp({db,newsletterConfig:{from:'Demo <demo@example.invalid>',origin:'http://localhost',linkKey,send:sender}});
  const disabled=createApp({db});
  const changed=createApp({db,newsletterConfig:{from:'Changed <changed@example.invalid>',origin:'https://changed.example.invalid',linkKey,send:sender}});
  await new Promise(r=>server.listen(0,'127.0.0.1',r)); await new Promise(r=>disabled.listen(0,'127.0.0.1',r));
  await new Promise(r=>changed.listen(0,'127.0.0.1',r));
  const base=`http://127.0.0.1:${server.address().port}`;
  async function actor(role){ const [row]=await root.execute('INSERT INTO users(email,full_name,is_active,role_id) VALUES(?,?,1,(SELECT id FROM roles WHERE name=?))',[`nl-${suffix}-${role}@example.invalid`,'Demo boletín',role]); users.push(row.insertId); const token=randomBytes(32).toString('base64url'); await root.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))',[row.insertId,sessionDigest(token)]); return token; }
  async function call(path,body,token,key,method='POST',target=base){ const r=await fetch(target+'/api/v1/'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`} :{}),...(key?{'Idempotency-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body)}); return {status:r.status,data:await r.json()}; }
  const cp='admin/newsletter/campaigns', input={subject:'Demo boletín',body:'Contenido sintético para probar el boletín.'};
  let release;
  try{
    const owner=await actor('OWNER'), sales=await actor('SALES');
    const create=async(key=randomBytes(32).toString('hex'),body=input)=>{ const result=await call(cp,body,owner,key); if(result.data.id&&!campaigns.includes(result.data.id)) campaigns.push(result.data.id); return {...result,key}; };
    const empty=await create(); assert.equal(empty.status,201); assert.equal(empty.data.recipients,0);
    const unicode=await create(undefined,{...input,body:'🌱'.repeat(5000)}); assert.equal(unicode.status,201);
    const subscribe=async(index)=>{ const email=`nl-${suffix}-${index}@example.invalid`; if(!emails.includes(email))emails.push(email); const result=await call('newsletter/subscribe',{email,consent:true}); const [[row]]=await root.execute('SELECT id FROM newsletter_subscribers WHERE email=?',[email]); if(row&&!subscribers.includes(row.id)) subscribers.push(row.id); return result; };
    assert.equal((await call('newsletter/subscribe',{email:'demo@example.invalid',consent:false})).status,400);
    assert.equal((await subscribe(1)).status,202);
    const confirmation=messages.at(-1).payload.text.match(/#confirm=([A-Za-z0-9_-]{43})/)[1];
    const count=messages.length; await subscribe(1); assert.equal(messages.length,count);
    assert.equal((await call('newsletter/confirm',undefined,undefined,undefined,'GET')).status,404);
    assert.equal((await call('newsletter/confirm',{token:confirmation})).status,200);
    assert.equal((await call('newsletter/confirm',{token:confirmation})).status,400);
    await subscribe(1); assert.equal(messages.length,count);
    const [[sub]]=await root.execute('SELECT * FROM newsletter_subscribers WHERE id=?',[subscribers[0]]);
    const unsubscribe=newsletterCipher(linkKey).open(sub.unsubscribe_cipher);
    assert.ok(sub.unsubscribe_hash.equals(sessionDigest(unsubscribe))); assert.ok(!sub.unsubscribe_cipher.includes(Buffer.from(unsubscribe)));
    const zero=await create(empty.key); assert.equal(zero.status,200); assert.equal(zero.data.recipients,0);
    assert.equal((await create(empty.key,{...input,body:input.body+' changed'})).status,409);
    assert.equal((await call(cp,input,sales,randomBytes(32).toString('hex'))).status,403);
    assert.equal((await call(cp,undefined,sales,undefined,'GET')).status,403);
    await subscribe(2); const second=messages.at(-1).payload.text.match(/#confirm=([A-Za-z0-9_-]{43})/)[1];
    await root.execute('UPDATE newsletter_subscribers SET requested_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 25 HOUR) WHERE id=?',[subscribers[1]]);
    assert.equal((await call('newsletter/confirm',{token:second})).status,400);
    await subscribe(2); const fresh=messages.at(-1).payload.text.match(/#confirm=([A-Za-z0-9_-]{43})/)[1]; assert.notEqual(fresh,second);
    assert.equal((await call('newsletter/confirm',{token:fresh})).status,200);
    const prepared=await create(); assert.equal(prepared.data.recipients,2);
    const raceKey=randomBytes(32).toString('hex'), races=await Promise.all([create(raceKey),create(raceKey)]);
    assert.deepEqual(races.map(row=>row.status).sort(),[200,201]); assert.equal(races[0].data.id,races[1].data.id);
    assert.equal((await call('newsletter/unsubscribe',{token:unsubscribe})).status,200);
    assert.equal((await call('newsletter/unsubscribe',{token:unsubscribe})).status,200);
    const sendPath=`${cp}/${prepared.data.id}/send`;
    assert.equal((await call(sendPath,{},sales)).status,403);
    fail=true;
    assert.equal((await call(sendPath,{},owner)).status,503);
    const uncertain=messages.at(-1); assert.ok(uncertain.payload.text.includes('#unsubscribe='));
    const [[pending]]=await root.execute('SELECT * FROM newsletter_deliveries WHERE campaign_id=? AND skipped_at IS NULL',[prepared.data.id]);
    assert.equal(pending.sent_at,null); assert.ok(pending.first_attempt_at); assert.ok(!pending.payload_cipher.includes(Buffer.from(uncertain.payload.text)));
    // Configuration changes cannot change a committed payload.
    fail=false;
    const sent=await call(sendPath,{},owner,undefined,'POST',`http://127.0.0.1:${changed.address().port}`); assert.equal(sent.status,200); assert.equal(sent.data.sent,1);
    assert.equal(messages.at(-1).key,uncertain.key); assert.deepEqual(messages.at(-1).payload,uncertain.payload);
    assert.equal((await call(sendPath,{},owner)).data.sent,0);
    const list=await call(cp,undefined,owner,undefined,'GET'); const row=list.data.items.find(c=>c.id===prepared.data.id); assert.equal(row.sent,1); assert.equal(row.skipped,1);
    const aged=await create();
    await root.execute('UPDATE newsletter_deliveries SET first_attempt_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 24 HOUR) WHERE campaign_id=?',[aged.data.id]);
    const before=messages.length; assert.equal((await call(`${cp}/${aged.data.id}/send`,{},owner)).status,409); assert.equal(messages.length,before);
    const previous=await create();
    await root.execute('UPDATE newsletter_subscribers SET requested_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 11 MINUTE) WHERE id=?',[subscribers[0]]);
    await subscribe(1); const again=messages.at(-1).payload.text.match(/#confirm=([A-Za-z0-9_-]{43})/)[1]; await call('newsletter/confirm',{token:again});
    await call('newsletter/unsubscribe',{token:unsubscribe});
    const [[active]]=await root.execute('SELECT unsubscribed_at FROM newsletter_subscribers WHERE id=?',[subscribers[0]]); assert.equal(active.unsubscribed_at,null);
    const [[secondSub]]=await root.execute('SELECT unsubscribe_cipher FROM newsletter_subscribers WHERE id=?',[subscribers[1]]);
    const secondUnsubscribe=newsletterCipher(linkKey).open(secondSub.unsubscribe_cipher);
    await call('newsletter/unsubscribe',{token:secondUnsubscribe});
    await root.execute('UPDATE newsletter_subscribers SET requested_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 11 MINUTE) WHERE id=?',[subscribers[1]]);
    await subscribe(2); const reconsent=messages.at(-1).payload.text.match(/#confirm=([A-Za-z0-9_-]{43})/)[1]; await call('newsletter/confirm',{token:reconsent});
    assert.equal((await call(`${cp}/${previous.data.id}/send`,{},owner)).data.sent,0);
    const [[skippedVersion]]=await root.execute('SELECT COUNT(*) n FROM newsletter_deliveries WHERE campaign_id=? AND skipped_at IS NOT NULL',[previous.data.id]); assert.equal(Number(skippedVersion.n),1);
    const current=await create();
    let entered; const entry=new Promise(r=>{entered=r}); hold=new Promise(r=>{release=r});
    // Signal when the next campaign reaches the simulated provider.
    const originalPush=messages.push.bind(messages); messages.push=(...values)=>{entered();return originalPush(...values)};
    const first=call(`${cp}/${current.data.id}/send`,{},owner); await entry;
    assert.equal((await call(`${cp}/${current.data.id}/send`,{},owner)).status,409);
    release(); hold=null; assert.equal((await first).status,200); messages.push=originalPush;
    assert.equal((await call('newsletter/subscribe',{email:'missing@example.invalid',consent:true},undefined,undefined,'POST',`http://127.0.0.1:${disabled.address().port}`)).status,503);
    const [[budget]]=await root.execute('SELECT attempts FROM auth_login_limits WHERE key_hash=?',[sessionDigest('newsletter:global:subscriptions')]); assert.ok(budget.attempts>0);
    await root.execute('UPDATE auth_login_limits SET attempts=100 WHERE key_hash=?',[sessionDigest('newsletter:global:subscriptions')]);
    assert.equal((await subscribe(1)).status,429);
    assert.equal((await call('newsletter/unsubscribe',{token:'x'.repeat(43)})).status,200);
    assert.equal((await call(`${cp}/${previous.data.id}/send`,{role:'OWNER'},owner)).status,400);
    await assert.rejects(db.execute('UPDATE newsletter_campaigns SET body=? WHERE id=?',['Changed payload',prepared.data.id]),error=>['ER_COLUMNACCESS_DENIED_ERROR','ER_TABLEACCESS_DENIED_ERROR'].includes(error.code));
  } finally{
    if(release)release();
    await Promise.all([new Promise(r=>server.close(r)),new Promise(r=>disabled.close(r)),new Promise(r=>changed.close(r))]); await db.end();
    for(const id of campaigns){await root.execute('DELETE FROM newsletter_deliveries WHERE campaign_id=?',[id]);await root.execute('DELETE FROM newsletter_campaigns WHERE id=?',[id]);}
    for(const id of subscribers)await root.execute('DELETE FROM newsletter_subscribers WHERE id=?',[id]);
    for(const id of users){await root.execute('DELETE FROM auth_sessions WHERE user_id=?',[id]);await root.execute('DELETE FROM users WHERE id=?',[id]);}
    for(const email of emails)await root.execute('DELETE FROM auth_login_limits WHERE key_hash=?',[sessionDigest(`email:newsletter:${email}`)]);
    for(const key of ['ip:newsletter:127.0.0.1','newsletter:global:subscriptions'])await root.execute('DELETE FROM auth_login_limits WHERE key_hash=?',[sessionDigest(key)]);
    await root.end();
  }
});
