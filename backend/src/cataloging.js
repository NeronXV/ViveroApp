import { createHash } from 'node:crypto';
import { ApiError, positiveId, validateProduct } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const hash = v => createHash('sha256').update(JSON.stringify(v)).digest();
const json = v => typeof v === 'string' ? JSON.parse(v) : v;
const object = v => { if (!v || typeof v !== 'object' || Array.isArray(v)) fail(400, 'CATALOGING_INPUT_INVALID'); return v; };
const text = (v, max) => {
  if (typeof v !== 'string' || !v.isWellFormed() || [...v].length > max || /[\u0000-\u001f\u007f]/u.test(v)) fail(400, 'CATALOGING_INPUT_INVALID');
  return v.trim();
};
export function catalogingFields(value) {
  object(value);
  const limits = { approved_name: 160, presentation: 120, internal_code: 40, scientific_name: 160, description: 2000, barcode: 128 };
  const result = {};
  for (const [k,v] of Object.entries(value)) {
    if (k === 'category_id') result[k] = v === null ? null : positiveId(v);
    else if (Object.hasOwn(limits, k)) result[k] = text(v, limits[k]);
    else fail(400, 'CATALOGING_INPUT_INVALID');
  }
  return result;
}
export function catalogingInput(value, creating = false) {
  object(value);
  const allowed = creating ? ['original_name','fields'] : ['revision','action','fields','product_id'];
  if (Object.keys(value).some(k => !allowed.includes(k))) fail(400, 'CATALOGING_INPUT_INVALID');
  if (creating) {
    const original = text(value.original_name,240);
    if (!original) fail(400, 'CATALOGING_INPUT_INVALID');
    return { original_name: original, fields: catalogingFields(value.fields) };
  }
  if (!Number.isInteger(value.revision) || value.revision < 1 || !['SAVE','SUBMIT','RETURN','PREPARE','LINK'].includes(value.action)) fail(400, 'CATALOGING_INPUT_INVALID');
  const fields = catalogingFields(value.fields);
  if ((value.action === 'LINK') !== Object.hasOwn(value,'product_id')) fail(400, 'CATALOGING_INPUT_INVALID');
  return { revision: value.revision, action: value.action, fields, ...(value.action === 'LINK' ? { product_id:positiveId(value.product_id) } : {}) };
}
export function catalogingKey(key) {
  if (typeof key !== 'string' || !/^[A-Za-z0-9._:-]{16,128}$/.test(key)) fail(400,'CATALOGING_KEY_REQUIRED');
  return hash(key);
}
export function catalogingScope(context, request) {
  requireCapabilities(context,['MANAGE_PRODUCTS']);
  if (!context.branch?.is_active) fail(403,'BRANCH_FORBIDDEN');
  if (String(context.user.id) !== request.headers['x-expected-actor-id'] || String(context.branch.id) !== request.headers['x-expected-branch-id']) fail(409,'CATALOGING_SESSION_CHANGED');
}
export function catalogingQuery(params) {
  const allowed = ['after_id','limit','search','status','category_id'];
  if ([...params.keys()].some(k => !allowed.includes(k) || params.getAll(k).length !== 1)) fail(400,'CATALOGING_INPUT_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  const status = params.get('status') ?? 'ALL';
  if (limit > 100 || !['ALL','DRAFT','REVIEW','LINKED','PREPARED'].includes(status)) fail(400,'CATALOGING_INPUT_INVALID');
  return { limit, after: params.has('after_id') ? positiveId(params.get('after_id')) : 0, search:text(params.get('search') ?? '',80), status,
    category: params.has('category_id') ? positiveId(params.get('category_id')) : null };
}
export function createCataloging(db, context) {
  requireCapabilities(context,['MANAGE_PRODUCTS']);
  async function row(id) {
    const [[r]] = await db.execute('SELECT * FROM cataloging_drafts WHERE id=? FOR UPDATE',[id]);
    if (!r) fail(404,'NOT_FOUND');
    return r;
  }
  function serialize(r) {
    return { id:r.id, original_name:r.original_name, fields:json(r.fields), source_key:r.source_key, source_id:r.source_id,
      source_evidence:json(r.source_evidence), status:r.status, revision:r.revision, product_id:r.product_id,
      has_image:r.image_key !== null, created_by:r.created_by, updated_by:r.updated_by, reviewed_by:r.reviewed_by,
      updated_at:r.updated_at, reviewed_at:r.reviewed_at };
  }
  async function replay(key, fingerprint) {
    const [[event]] = await db.execute('SELECT actor_id,request_hash,result FROM cataloging_events WHERE request_key=?',[key]);
    if (!event) return null;
    if (event.actor_id !== context.user.id || !event.request_hash.equals(fingerprint)) fail(409,'CATALOGING_KEY_CONFLICT');
    return json(event.result);
  }
  async function record(id,key,fingerprint) {
    const result = serialize(await row(id));
    await db.execute('INSERT INTO cataloging_events(draft_id,actor_id,request_key,request_hash,result) VALUES(?,?,?,?,?)',
      [id,context.user.id,key,fingerprint,JSON.stringify(result)]);
    return result;
  }
  async function validateCodes(fields) {
    const [[collision]] = await db.execute(`SELECT id FROM products WHERE internal_code=? OR barcode=?
      OR (?<>'' AND (barcode=? OR internal_code=?)) LIMIT 1 FOR UPDATE`,
      [fields.internal_code,fields.internal_code,fields.barcode ?? '',fields.barcode ?? '',fields.barcode ?? '']);
    if (collision || (fields.barcode && fields.barcode === fields.internal_code)) fail(409,'CATALOGING_PRODUCT_EXISTS');
  }
  return {
    async detail(id) { return serialize(await row(id)); },
    async list(q) {
      const term = `%${q.search.replace(/[\\%_]/g,'\\$&')}%`;
      const [rows] = await db.execute(`SELECT * FROM cataloging_drafts WHERE id>? AND (?='ALL' OR status=?)
        AND (? IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(fields,'$.category_id'))=?)
        AND (original_name LIKE ? OR JSON_UNQUOTE(JSON_EXTRACT(fields,'$.approved_name')) LIKE ?
          OR JSON_UNQUOTE(JSON_EXTRACT(fields,'$.internal_code')) LIKE ?) ORDER BY id LIMIT ?`,
        [q.after,q.status,q.status,q.category,q.category,term,term,term,q.limit+1]);
      return { items:rows.slice(0,q.limit).map(serialize),next_after_id:rows.length>q.limit ? rows[q.limit-1].id : null };
    },
    async create(input,key) {
      const fingerprint = hash(['create',input]);
      const previous = await replay(key,fingerprint); if (previous) return previous;
      const [r] = await db.execute('INSERT INTO cataloging_drafts(original_name,fields,created_by,updated_by) VALUES(?,?,?,?)',
        [input.original_name,JSON.stringify(input.fields),context.user.id,context.user.id]);
      return record(r.insertId,key,fingerprint);
    },
    async update(id,input,key) {
      const fingerprint = hash(['update',id,input]);
      const previous = await replay(key,fingerprint); if (previous) return previous;
      const current = await row(id);
      if (current.revision !== input.revision) fail(409,'CATALOGING_EDIT_CONFLICT');
      if (['LINKED','PREPARED'].includes(current.status)) fail(409,'CATALOGING_CLOSED');
      if (['RETURN','PREPARE','LINK'].includes(input.action) && context.role?.name !== 'OWNER') fail(403,'FORBIDDEN');
      if (input.action === 'SAVE' && current.status !== 'DRAFT') fail(409,'CATALOGING_IN_REVIEW');
      if (['RETURN','PREPARE','LINK'].includes(input.action) && current.status !== 'REVIEW') fail(409,'CATALOGING_NOT_IN_REVIEW');
      let status = input.action === 'SUBMIT' ? 'REVIEW' : input.action === 'RETURN' ? 'DRAFT' : current.status;
      let productId = null;
      if (input.action === 'LINK') {
        const [[p]] = await db.execute('SELECT id FROM products WHERE id=? LOCK IN SHARE MODE',[input.product_id]);
        if (!p) fail(404,'NOT_FOUND');
        productId=p.id; status='LINKED'; // Explicit owner choice; never alters the existing product.
      }
      if (input.action === 'PREPARE') {
        const f=input.fields;
        if (!f.approved_name || !f.presentation || !f.internal_code || !f.category_id) fail(400,'CATALOGING_INCOMPLETE');
        const data=validateProduct({ internal_code:f.internal_code, common_name:`${f.approved_name} · ${f.presentation}`, category_id:f.category_id,
          scientific_name:f.scientific_name || null, barcode:f.barcode || null, description:[f.presentation,f.description].filter(Boolean).join('\n'), price_cents:0, is_active:false });
        await validateCodes(f);
        const [[category]] = await db.execute('SELECT id FROM categories WHERE id=? AND is_active=1 LOCK IN SHARE MODE',[f.category_id]);
        if (!category) fail(409,'REFERENCE_CONFLICT');
        const keys=Object.keys(data);
        const [p]=await db.execute(`INSERT INTO products(${keys.join(',')},preparation_mode) VALUES(${keys.map(()=>'?').join(',')},'PENDING')`,Object.values(data));
        productId=p.insertId; status='PREPARED';
        if (current.image_key) {
          const info=json(current.image_info);
          await db.execute('INSERT INTO product_images(product_id,storage_key,width,height,byte_size,is_primary,alt_text) VALUES(?,?,?,?,?,1,?)',
            [productId,current.image_key,info.width,info.height,info.size,data.common_name]);
        }
      }
      await db.execute(`UPDATE cataloging_drafts SET fields=?,status=?,product_id=?,revision=revision+1,updated_by=?,updated_at=UTC_TIMESTAMP(6),
        reviewed_by=?,reviewed_at=IF(? IS NULL,NULL,UTC_TIMESTAMP(6)) WHERE id=?`,
        [JSON.stringify(input.fields),status,productId,context.user.id,productId ? context.user.id : null,productId,id]);
      return record(id,key,fingerprint);
    },
    async photo(id,revision,key,store,uploaded) {
      const fingerprint=hash(['photo',id,revision,hash(uploaded.data).toString('hex')]);
      const previous=await replay(key,fingerprint); if(previous)return previous;
      const current=await row(id);
      if(current.revision!==revision)fail(409,'CATALOGING_EDIT_CONFLICT');
      if(current.status!=='DRAFT')fail(409,'CATALOGING_IN_REVIEW');
      const storage=await store.put(uploaded.data);
      await db.execute('UPDATE cataloging_drafts SET image_key=?,image_info=?,revision=revision+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?',
        [storage,JSON.stringify(uploaded.info),context.user.id,id]);
      return record(id,key,fingerprint);
    },
    async photoKey(id) { const r=await row(id); if(!r.image_key)fail(404,'NOT_FOUND'); return r.image_key; },
  };
}

export async function productEditVersion(db,id) {
  const [[p]]=await db.execute('SELECT catalog_revision FROM products WHERE id=? FOR UPDATE',[id]);
  if(!p)fail(404,'NOT_FOUND');
  return {id,revision:p.catalog_revision};
}
export async function checkProductEditVersion(db,id,header) {
  if(header===undefined)return; // Preserve older API/Android clients.
  if(!/^"[1-9][0-9]*"$/.test(header))fail(400,'CATALOGING_INPUT_INVALID');
  if(String((await productEditVersion(db,id)).revision)!==header.slice(1,-1))fail(409,'CATALOGING_EDIT_CONFLICT');
}
