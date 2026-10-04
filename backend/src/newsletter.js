import { randomBytes, createCipheriv, createDecipheriv } from 'node:crypto';
import { ApiError } from './catalog.js';
import { normalizeEmail } from './auth/password.js';
import { sessionDigest, transaction, requireCapabilities, consumeLoginBudget } from './auth/service.js';

const invalid = () => { throw new ApiError(400, 'NEWSLETTER_INPUT_INVALID'); };
function shape(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== keys.length || keys.some(k => !Object.hasOwn(value,k))) invalid();
}
export function newsletterInput(value, action) {
  if (action === 'subscribe') { shape(value,['email','consent']); if (value.consent !== true) invalid(); return { email: normalizeEmail(value.email), consent: true }; }
  if (action === 'confirm' || action === 'unsubscribe') { shape(value,['token']); if (typeof value.token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(value.token)) invalid(); return value; }
  shape(value,['subject','body']);
  const result = {};
  for (const [key,min,max] of [['subject',3,150],['body',10,10000]]) {
    if (typeof value[key] !== 'string' || [...value[key].trim()].length < min || [...value[key].trim()].length > max || (key === 'subject' ? /[\x00-\x1f\x7f]/ : /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/).test(value[key])) invalid();
    result[key] = value[key].trim();
  }
  return result;
}
export function newsletterKey(value) { if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) invalid(); return Buffer.from(value,'hex'); }
export function newsletterCipher(hex) {
  if (!/^[a-f0-9]{64}$/.test(hex ?? '') || /^0+$/.test(hex)) throw new Error('Invalid NEWSLETTER_LINK_KEY');
  const key = Buffer.from(hex,'hex');
  return {
    seal(value) { const iv=randomBytes(12), cipher=createCipheriv('aes-256-gcm',key,iv); cipher.setAAD(Buffer.from('vivero-newsletter-v1')); const data=Buffer.concat([cipher.update(value,'utf8'),cipher.final()]); return Buffer.concat([iv,cipher.getAuthTag(),data]); },
    open(value) { const cipher=createDecipheriv('aes-256-gcm',key,value.subarray(0,12)); cipher.setAAD(Buffer.from('vivero-newsletter-v1')); cipher.setAuthTag(value.subarray(12,28)); return Buffer.concat([cipher.update(value.subarray(28)),cipher.final()]).toString('utf8'); },
  };
}
export function createNewsletter(db, config = {}) {
  const cipher = config.linkKey ? newsletterCipher(config.linkKey) : null;
  let pendingMail = 0;
  function configured() { if (!cipher || !config.send || !config.from || !config.origin) throw new ApiError(503,'MAIL_UNAVAILABLE'); }
  if (config.from && /[\r\n]/.test(config.from)) throw new Error('Invalid NEWSLETTER_FROM');
  const secret = () => randomBytes(32).toString('base64url');
  return {
    async subscribe(input,address) {
      configured();
      await consumeLoginBudget(db,`newsletter:${input.email}`,`newsletter:${address}`);
      const overBudget = await transaction(db, async connection => {
        const hash=sessionDigest('newsletter:global:subscriptions');
        await connection.execute(`INSERT INTO auth_login_limits(key_hash,attempts,reset_at) VALUES(?,1,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))
          ON DUPLICATE KEY UPDATE attempts=IF(reset_at<=UTC_TIMESTAMP(6),1,LEAST(attempts+1,65535)),reset_at=IF(reset_at<=UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR),reset_at)`,[hash]);
        const [[row]]=await connection.execute('SELECT attempts FROM auth_login_limits WHERE key_hash=?',[hash]); return row.attempts>100;
      });
      if (overBudget) throw new ApiError(429,'NEWSLETTER_RATE_LIMITED');
      if (pendingMail >= 32) throw new ApiError(503,'MAIL_UNAVAILABLE');
      pendingMail++;
      let message;
      try { message = await transaction(db,async connection => {
        // Unique email insert serializes concurrent first subscriptions.
        let created=true;
        try { await connection.execute('INSERT INTO newsletter_subscribers(email) VALUES(?)',[input.email]); }
        catch(error) { if (error.code!=='ER_DUP_ENTRY') throw error; created=false; }
        const [[row]]=await connection.execute('SELECT *,requested_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE) AS recent FROM newsletter_subscribers WHERE email=? FOR UPDATE',[input.email]);
        if (row.confirmed_at && !row.unsubscribed_at || !created && row.recent) return null;
        const token=secret();
        await connection.execute('UPDATE newsletter_subscribers SET confirmation_hash=?,requested_at=UTC_TIMESTAMP(6) WHERE id=?',[sessionDigest(token),row.id]);
        return { payload:{from:config.from,to:[input.email],subject:'Confirma tu suscripción a Boletín Verde',text:`Confirma tu suscripción abriendo ${config.origin}/boletin#confirm=${token} y pulsando Confirmar. El enlace vence en 24 horas. Si no lo solicitaste, ignóralo.`}, key:`newsletter-confirm-${sessionDigest(token).toString('hex')}` };
      }); } catch(error) { pendingMail--; throw error; }
      // Public response neither waits for nor exposes provider/account status.
      void (message ? config.send(message.payload,message.key) : Promise.resolve()).catch(()=>{}).finally(()=>{ pendingMail--; });
      return {accepted:true};
    },
    async confirm(token) {
      if (!cipher) throw new ApiError(503,'MAIL_UNAVAILABLE');
      return transaction(db,async connection=>{
        const [[row]]=await connection.execute('SELECT id FROM newsletter_subscribers WHERE confirmation_hash=? AND requested_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 24 HOUR) FOR UPDATE',[sessionDigest(token)]);
        if (!row) throw new ApiError(400,'NEWSLETTER_LINK_INVALID');
        const unsubscribe=secret();
        await connection.execute('UPDATE newsletter_subscribers SET confirmation_hash=NULL,confirmed_at=UTC_TIMESTAMP(6),unsubscribed_at=NULL,consent_version=consent_version+1,unsubscribe_hash=?,unsubscribe_cipher=? WHERE id=?',[sessionDigest(unsubscribe),cipher.seal(unsubscribe),row.id]);
        return {confirmed:true};
      });
    },
    async unsubscribe(token) {
      await transaction(db,connection=>connection.execute('UPDATE newsletter_subscribers SET unsubscribed_at=COALESCE(unsubscribed_at,UTC_TIMESTAMP(6)),confirmation_hash=NULL WHERE unsubscribe_hash=?',[sessionDigest(token)]));
      return {unsubscribed:true};
    },
    async campaigns(connection,context) {
      requireCapabilities(context,['MANAGE_SETTINGS']);
      const [rows]=await connection.execute(`SELECT c.id,c.subject,c.body,COUNT(d.id) recipients,
        COALESCE(SUM(d.sent_at IS NOT NULL),0) sent,COALESCE(SUM(d.skipped_at IS NOT NULL),0) skipped,
        COALESCE(SUM(d.sent_at IS NULL AND d.skipped_at IS NULL AND d.first_attempt_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 23 HOUR)),0) review
        FROM (SELECT * FROM newsletter_campaigns ORDER BY id DESC LIMIT 50)c LEFT JOIN newsletter_deliveries d ON d.campaign_id=c.id GROUP BY c.id,c.subject,c.body ORDER BY c.id DESC`);
      return {items:rows.map(row=>({...row,recipients:Number(row.recipients),sent:Number(row.sent),skipped:Number(row.skipped),review:Number(row.review)}))};
    },
    async create(connection,context,input,key) {
      requireCapabilities(context,['MANAGE_SETTINGS']); configured();
      // withAccess locks the actor before this transaction, serializing its keys.
      const [[campaign]]=await connection.execute('SELECT * FROM newsletter_campaigns WHERE created_by=? AND request_key=? FOR UPDATE',[context.user.id,key]);
      if (campaign) {
        if (campaign.subject!==input.subject || campaign.body!==input.body) throw new ApiError(409,'IDEMPOTENCY_CONFLICT');
        const [[existing]]=await connection.execute('SELECT COUNT(*) n FROM newsletter_deliveries WHERE campaign_id=?',[campaign.id]);
        return {id:campaign.id,recipients:Number(existing.n),idempotent_replay:true};
      }
      const [created]=await connection.execute('INSERT INTO newsletter_campaigns(created_by,request_key,subject,body) VALUES(?,?,?,?)',[context.user.id,key,input.subject,input.body]);
      return {id:created.insertId,recipients:await this.snapshot(connection,created.insertId,input),idempotent_replay:false};
    },
    async snapshot(connection,campaignId,input) {
      const [subscribers]=await connection.execute('SELECT id,email,consent_version,unsubscribe_cipher FROM newsletter_subscribers WHERE confirmed_at IS NOT NULL AND unsubscribed_at IS NULL LOCK IN SHARE MODE');
      for (const subscriber of subscribers) {
        const token=cipher.open(subscriber.unsubscribe_cipher);
        const payload={from:config.from,to:[subscriber.email],subject:input.subject,text:input.body+`\n\nCancelar suscripción: ${config.origin}/boletin#unsubscribe=${token}`};
        await connection.execute('INSERT INTO newsletter_deliveries(campaign_id,subscriber_id,consent_version,mail_key,payload_cipher) VALUES(?,?,?,?,?)',[campaignId,subscriber.id,subscriber.consent_version,secret(),cipher.seal(JSON.stringify(payload))]);
      }
      return subscribers.length;
    },
    async send(request,id,auth) {
      configured();
      const lock=await db.getConnection(), name=`vivero_newsletter_${id}`;
      let acquired=false, sent=0, processed=0;
      try {
        const [[result]]=await lock.execute('SELECT GET_LOCK(?,0) acquired',[name]); acquired=result.acquired===1;
        if (!acquired) throw new ApiError(409,'NEWSLETTER_BUSY');
        const started=Date.now();
        while (processed<20 && Date.now()-started<4000) {
          const item=await auth.withAccess(request,['MANAGE_SETTINGS'],async connection=>{
            const [[campaign]]=await connection.execute('SELECT id FROM newsletter_campaigns WHERE id=?',[id]);
            if (!campaign) throw new ApiError(404,'NOT_FOUND');
            const [[delivery]]=await connection.execute(`SELECT d.*,d.first_attempt_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 23 HOUR) AS aged FROM newsletter_deliveries d WHERE campaign_id=? AND sent_at IS NULL AND skipped_at IS NULL ORDER BY id LIMIT 1 FOR UPDATE`,[id]);
            if (!delivery) return null;
            const [[subscriber]]=await connection.execute('SELECT confirmed_at,unsubscribed_at,consent_version FROM newsletter_subscribers WHERE id=? FOR UPDATE',[delivery.subscriber_id]);
            if (!subscriber.confirmed_at || subscriber.unsubscribed_at || subscriber.consent_version!==delivery.consent_version) {
              await connection.execute('UPDATE newsletter_deliveries SET skipped_at=UTC_TIMESTAMP(6) WHERE id=?',[delivery.id]); return {skip:true};
            }
            if (delivery.aged) throw new ApiError(409,'NEWSLETTER_REVIEW_REQUIRED');
            await connection.execute('UPDATE newsletter_deliveries SET first_attempt_at=COALESCE(first_attempt_at,UTC_TIMESTAMP(6)) WHERE id=?',[delivery.id]);
            return delivery;
          });
          if (!item) break;
          processed++;
          if (item.skip) continue;
          await config.send(JSON.parse(cipher.open(item.payload_cipher)),`newsletter-${item.mail_key}`);
          await db.execute('UPDATE newsletter_deliveries SET sent_at=UTC_TIMESTAMP(6) WHERE id=?',[item.id]);
          sent++;
        }
        return {sent,batch_size:processed};
      } finally {
        if (acquired) { try { await lock.execute('SELECT RELEASE_LOCK(?)',[name]); } catch { lock.destroy(); } }
        lock.release();
      }
    },
  };
}
