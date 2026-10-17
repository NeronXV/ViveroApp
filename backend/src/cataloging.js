import { createHash } from 'node:crypto';
import { ApiError, positiveId, validateProduct, createCatalog } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { lockInventoryRow, parseMilli, formatMilli } from './inventory.js';
import { createProductPreparation } from './product-preparation.js';

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
    else if (k === 'suggested_price_cents') {
      if (v !== null && (!Number.isSafeInteger(v) || v < 0)) fail(400, 'CATALOGING_INPUT_INVALID');
      result[k] = v;
    }
    else if (k === 'initial_quantity') {
      if (v !== null && (!Number.isSafeInteger(v) || v < 0)) fail(400, 'CATALOGING_INPUT_INVALID');
      result[k] = v;
    }
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
export function catalogingPublishInput(value) {
  object(value);
  const allowed = ['revision', 'fields', 'price_cents', 'initial_quantity'];
  if (Object.keys(value).some(k => !allowed.includes(k))) fail(400, 'CATALOGING_INPUT_INVALID');
  if (!Number.isInteger(value.revision) || value.revision < 1) fail(400, 'CATALOGING_INPUT_INVALID');
  const fields = value.fields !== undefined ? catalogingFields(value.fields) : undefined;
  let price_cents = null;
  if (value.price_cents !== undefined && value.price_cents !== null) {
    if (!Number.isSafeInteger(value.price_cents) || value.price_cents < 0) fail(400, 'CATALOGING_INPUT_INVALID');
    price_cents = value.price_cents;
  }
  let initial_quantity = null;
  if (value.initial_quantity !== undefined && value.initial_quantity !== null) {
    const qtyStr = String(value.initial_quantity);
    const milli = parseMilli(qtyStr, { zero: true, integer: true });
    initial_quantity = Number(milli / 1000n);
  }
  return { revision: value.revision, fields, price_cents, initial_quantity };
}
export function plantConfirmationInput(value) {
  object(value);
  if (Object.keys(value).some(k => !['target','id','revision','fields','price_cents','product_id'].includes(k))) fail(400,'CATALOGING_INPUT_INVALID');
  if (!['draft','product'].includes(value.target) || !Number.isInteger(value.revision) || value.revision < 1) fail(400,'CATALOGING_INPUT_INVALID');
  const id=positiveId(value.id);
  if (!Number.isSafeInteger(value.price_cents) || value.price_cents < 0) fail(400,'CATALOGING_INPUT_INVALID');
  if (value.target==='product' && Object.hasOwn(value,'product_id')) fail(400,'CATALOGING_INPUT_INVALID');
  const fields=value.target==='draft' ? catalogingFields(value.fields) : validateProduct(value.fields,true);
  if (value.target==='draft' && Object.hasOwn(fields,'initial_quantity')) fail(400,'CATALOGING_INPUT_INVALID');
  if (value.target==='product' && Object.keys(fields).some(k => !['common_name','category_id','description','scientific_name','light_type','watering_advice'].includes(k))) fail(400,'CATALOGING_INPUT_INVALID');
  return {target:value.target,id,revision:value.revision,fields,price_cents:value.price_cents,
    ...(Object.hasOwn(value,'product_id') ? {product_id:positiveId(value.product_id)} : {})};
}
export async function generateSku(db, draftId) {
  let candidate = `PLN-${String(draftId).padStart(5, '0')}`;
  let counter = 1;
  while (true) {
    const [[collision]] = await db.execute(
      'SELECT id FROM products WHERE internal_code = ? OR barcode = ? LIMIT 1 FOR UPDATE',
      [candidate, candidate]
    );
    if (!collision) return candidate;
    counter++;
    candidate = `PLN-${String(draftId).padStart(5, '0')}-${counter}`;
  }
}
export function catalogingKey(key) {
  if (typeof key !== 'string' || !/^[A-Za-z0-9._:-]{16,128}$/.test(key)) fail(400,'CATALOGING_KEY_REQUIRED');
  return hash(key);
}
export function catalogingScope(context, request, requiredCapability = 'MANAGE_PRODUCTS') {
  requireCapabilities(context, [requiredCapability]);
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
      approved_by:r.approved_by ?? null, updated_at:r.updated_at, reviewed_at:r.reviewed_at, approved_at:r.approved_at ?? null };
  }
  async function replay(key, fingerprint) {
    const [[event]] = await db.execute('SELECT actor_id,request_hash,result FROM cataloging_events WHERE request_key=? FOR UPDATE',[key]);
    if (!event) return null;
    if (event.actor_id !== context.user.id || !event.request_hash.equals(fingerprint)) fail(409,'CATALOGING_KEY_CONFLICT');
    return json(event.result);
  }
  async function record(id,key,fingerprint,extra = {}) {
    const result = { ...serialize(await row(id)), ...extra };
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
      const current = await row(id);
      const previous = await replay(key,fingerprint); if (previous) return previous;
      if (current.revision !== input.revision) fail(409,'CATALOGING_EDIT_CONFLICT');
      if (['LINKED','PREPARED'].includes(current.status)) fail(409,'CATALOGING_CLOSED');
      if (current.source_key === 'supplier_purchase_item' && ['LINK','PREPARE'].includes(input.action)) fail(409,'CATALOGING_USE_CONFIRM');
      if (current.source_key === 'supplier_purchase_item' && Object.hasOwn(input.fields,'initial_quantity')) fail(409,'CATALOGING_RECEIPT_CONFLICT');
      const publisher = context.capabilities.includes('PUBLISH_CATALOGING');
      if (['RETURN','PREPARE'].includes(input.action) && context.role?.name !== 'OWNER') fail(403,'FORBIDDEN');
      if (input.action === 'LINK' && !publisher && context.role?.name !== 'OWNER') fail(403,'FORBIDDEN');
      if (input.action === 'SAVE' && current.status === 'REVIEW' && !publisher) fail(409,'CATALOGING_IN_REVIEW');
      if (input.action === 'SUBMIT' && current.status !== 'DRAFT') fail(409,'CATALOGING_IN_REVIEW');
      if (['RETURN','PREPARE'].includes(input.action) && current.status !== 'REVIEW') fail(409,'CATALOGING_NOT_IN_REVIEW');
      const savedFields = { ...json(current.fields), ...input.fields };
      let status = input.action === 'SUBMIT' ? 'REVIEW' : input.action === 'RETURN' ? 'DRAFT' : current.status;
      let productId = null;
      if (input.action === 'LINK') {
        const [[p]] = await db.execute('SELECT id FROM products WHERE id=? LOCK IN SHARE MODE',[input.product_id]);
        if (!p) fail(404,'NOT_FOUND');
        productId=p.id; status='LINKED'; // Explicit authorized choice; never alters the existing product.
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
        [JSON.stringify(savedFields),status,productId,context.user.id,productId ? context.user.id : null,productId,id]);
      return record(id,key,fingerprint);
    },
    async publish(id, input, key, branchId) {
      requireCapabilities(context, ['PUBLISH_CATALOGING']);
      if (!context.branch?.is_active || branchId !== context.branch.id) fail(403, 'BRANCH_FORBIDDEN');
      const fingerprint = hash(['publish', id, input]);
      const current = await row(id);
      const previous = await replay(key, fingerprint);
      if (previous) {
        if (previous.publication && previous.publication.branch_id !== branchId) fail(409, 'CATALOGING_SESSION_CHANGED');
        // Older results have no publication metadata. Never replay their stock in another branch.
        if (!previous.publication && input.initial_quantity > 0) {
          const [[count]] = await db.execute('SELECT branch_id FROM inventory_counts WHERE product_id=? AND idempotency_hash=?',
            [previous.product_id, hash(`cataloging-publish-count:${id}:${branchId}`)]);
          if (!count || count.branch_id !== branchId) fail(409, 'CATALOGING_SESSION_CHANGED');
        }
        return previous;
      }
      if (current.revision !== input.revision) fail(409, 'CATALOGING_EDIT_CONFLICT');
      if (['LINKED', 'PREPARED'].includes(current.status)) fail(409, 'CATALOGING_CLOSED');
      if (current.source_key === 'supplier_purchase_item') fail(409, 'CATALOGING_USE_CONFIRM');

      const f = { ...json(current.fields), ...(input.fields ?? {}) };
      if (!f.approved_name || !f.presentation || !f.category_id) fail(400, 'CATALOGING_INCOMPLETE');
      if (!f.internal_code || !f.internal_code.trim()) {
        f.internal_code = await generateSku(db, id);
      }
      await validateCodes(f);
      const [[category]] = await db.execute('SELECT id FROM categories WHERE id=? AND is_active=1 LOCK IN SHARE MODE', [f.category_id]);
      if (!category) fail(409, 'REFERENCE_CONFLICT');

      const priceCents = input.price_cents !== null && input.price_cents !== undefined ? input.price_cents : 0;
      const initialQty = input.initial_quantity !== null && input.initial_quantity !== undefined ? input.initial_quantity : 0;
      const hasConfirmedPrice = priceCents > 0;
      const hasStock = initialQty > 0;
      const canSell = hasConfirmedPrice && hasStock;

      const data = validateProduct({
        internal_code: f.internal_code,
        common_name: `${f.approved_name} · ${f.presentation}`,
        category_id: f.category_id,
        scientific_name: f.scientific_name || null,
        barcode: f.barcode || null,
        description: [f.presentation, f.description].filter(Boolean).join('\n'),
        price_cents: priceCents,
        is_active: canSell,
      });

      const keys = Object.keys(data);
      const [p] = await db.execute(
        `INSERT INTO products (${keys.join(',')}, preparation_mode) VALUES (${keys.map(() => '?').join(',')}, 'PENDING')`,
        Object.values(data)
      );
      const productId = p.insertId;

      if (current.image_key) {
        const info = json(current.image_info);
        await db.execute(
          'INSERT INTO product_images(product_id,storage_key,width,height,byte_size,is_primary,alt_text) VALUES(?,?,?,?,?,1,?)',
          [productId, current.image_key, info.width, info.height, info.size, data.common_name]
        );
      }

      if (hasConfirmedPrice) {
        await db.execute(
          'UPDATE products SET price_confirmed_at = UTC_TIMESTAMP(6), price_confirmed_by = ? WHERE id = ?',
          [context.user.id, productId]
        );
      }

      if (hasStock) {
        const quantityMilli = parseMilli(String(initialQty), { zero: false, integer: true });
        await lockInventoryRow(db, branchId, productId);
        const [[hasMovements]] = await db.execute(
          'SELECT 1 FROM inventory_movements WHERE branch_id = ? AND product_id = ? LIMIT 1',
          [branchId, productId]
        );
        if (!hasMovements) {
          const movementHash = hash(`cataloging-publish-movement:${id}:${branchId}`);
          const [movement] = await db.execute(
            `INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, idempotency_hash, notes, created_by)
             VALUES (?, ?, 'ADJUSTMENT_ADD', ?, ?, ?, ?)`,
            [branchId, productId, formatMilli(quantityMilli), movementHash, 'Existencia inicial aprobada en catalogación', context.user.id]
          );
          const countHash = hash(`cataloging-publish-count:${id}:${branchId}`);
          await db.execute(
            `INSERT INTO inventory_counts (movement_id, idempotency_hash, branch_id, product_id, previous_quantity, counted_quantity, adjustment_quantity, reason, counted_by)
             VALUES (?, ?, ?, ?, '0.000', ?, ?, 'Conteo inicial en catalogación', ?)`,
            [movement.insertId, countHash, branchId, productId, formatMilli(quantityMilli), formatMilli(quantityMilli), context.user.id]
          );
          await db.execute(
            `INSERT INTO product_branch_preparation (product_id, branch_id, initial_count_confirmed_at, initial_count_confirmed_by)
             VALUES (?, ?, UTC_TIMESTAMP(6), ?) ON DUPLICATE KEY UPDATE initial_count_confirmed_at = VALUES(initial_count_confirmed_at), initial_count_confirmed_by = VALUES(initial_count_confirmed_by)`,
            [productId, branchId, context.user.id]
          );
        }
      }

      if (canSell) {
        await db.execute(
          `UPDATE product_branch_preparation SET activated_at = UTC_TIMESTAMP(6), activated_by = ?
           WHERE product_id = ? AND branch_id = ?`,
          [context.user.id, productId, branchId]
        );
      }

      await db.execute(
        `UPDATE cataloging_drafts SET fields=?, status='PREPARED', product_id=?, revision=revision+1,
         updated_by=?, updated_at=UTC_TIMESTAMP(6), reviewed_by=?, reviewed_at=UTC_TIMESTAMP(6),
         approved_by=?, approved_at=UTC_TIMESTAMP(6) WHERE id=?`,
        [JSON.stringify(f), productId, context.user.id, context.user.id, context.user.id, id]
      );

      return record(id, key, fingerprint, { publication: {
        branch_id: branchId, price_cents: priceCents, initial_quantity: initialQty, ready: canSell,
      } });
    },
    async confirm(id,input,key) {
      requireCapabilities(context,['PUBLISH_CATALOGING','MANAGE_PRICES']);
      const branchId=context.branch.id;
      await db.execute('SELECT id FROM branches WHERE id=? FOR UPDATE',[branchId]);
      const current=await row(id), fingerprint=hash(['confirm',id,input,branchId]);
      const previous=await replay(key,fingerprint); if(previous)return previous;
      if(current.revision!==input.revision)fail(409,'CATALOGING_EDIT_CONFLICT');
      if(['LINKED','PREPARED'].includes(current.status))fail(409,'CATALOGING_CLOSED');
      const f={...json(current.fields),...input.fields};
      let receipt=null;
      if(current.source_key==='supplier_purchase_item'){
        const [[line]]=await db.execute(`SELECT i.id,i.line_number,i.quantity,i.product_id,i.movement_id,i.new_product,
          d.id AS purchase_id,d.branch_id,d.status AS purchase_status
          FROM supplier_purchase_items i JOIN supplier_purchase_documents d ON d.id=i.purchase_id
          WHERE i.id=? FOR UPDATE`,[positiveId(current.source_id)]);
        if(!line || line.purchase_status!=='RECEIVED' || line.branch_id!==branchId || line.product_id!==null || line.movement_id!==null || !line.new_product || line.quantity<=0)fail(409,'CATALOGING_RECEIPT_CONFLICT');
        receipt=line;
        f.initial_quantity=line.quantity;
      }
      if(!f.approved_name || !f.presentation || !f.category_id)fail(400,'CATALOGING_INCOMPLETE');
      const [[category]]=await db.execute('SELECT id FROM categories WHERE id=? AND is_active=1 LOCK IN SHARE MODE',[f.category_id]);
      if(!category)fail(409,'REFERENCE_CONFLICT');
      let productId=input.product_id, status='LINKED', priceCents=input.price_cents;
      const name=`${f.approved_name} · ${f.presentation}`;
      if(productId){
        const [[existing]]=await db.execute('SELECT id,internal_code,price_cents FROM products WHERE id=? FOR UPDATE',[productId]);
        if(!existing)fail(404,'NOT_FOUND');
        if(priceCents===0)priceCents=Number(existing.price_cents);
        await createCatalog(db,context).update(productId,{common_name:name,category_id:f.category_id,
          description:[f.presentation,f.description].filter(Boolean).join('\n'),scientific_name:f.scientific_name||null,
          price_cents:priceCents});
      }else{
        if(!f.internal_code?.trim())f.internal_code=await generateSku(db,id);
        await validateCodes(f);
        const data=validateProduct({internal_code:f.internal_code,common_name:name,category_id:f.category_id,
          scientific_name:f.scientific_name||null,barcode:f.barcode||null,
          description:[f.presentation,f.description].filter(Boolean).join('\n'),price_cents:priceCents,is_active:false});
        const keys=Object.keys(data);
        const [p]=await db.execute(`INSERT INTO products(${keys.join(',')},preparation_mode) VALUES(${keys.map(()=>'?').join(',')},'PENDING')`,Object.values(data));
        productId=p.insertId;status='PREPARED';
      }
      if(current.image_key){
        const info=json(current.image_info);
        if(input.product_id)await db.execute('UPDATE product_images SET is_primary=0 WHERE product_id=? AND is_primary=1',[productId]);
        await db.execute('INSERT INTO product_images(product_id,storage_key,width,height,byte_size,is_primary,alt_text) VALUES(?,?,?,?,?,1,?)',
          [productId,current.image_key,info.width,info.height,info.size,name]);
      }
      await db.execute('UPDATE products SET price_confirmed_at=IF(? > 0,UTC_TIMESTAMP(6),NULL),price_confirmed_by=? WHERE id=?',
        [priceCents,priceCents>0?context.user.id:null,productId]);
      if(receipt){
        await lockInventoryRow(db,branchId,productId);
        const movementHash=createHash('sha256').update(`supplier-purchase-line\0${receipt.id}`).digest();
        const [movement]=await db.execute("INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity,idempotency_hash,notes,created_by) VALUES(?,?,'RECEPTION',?,?,?,?)",
          [branchId,productId,receipt.quantity,movementHash,`Compra ${receipt.purchase_id}, línea ${receipt.line_number}`,context.user.id]);
        await db.execute("UPDATE supplier_purchase_items SET product_id=?,resolution_status='MATCHED',resolved_by=?,resolved_at=UTC_TIMESTAMP(6),movement_id=? WHERE id=?",
          [productId,context.user.id,movement.insertId,receipt.id]);
        if(!input.product_id)await db.execute('INSERT INTO product_branch_preparation(product_id,branch_id,initial_count_confirmed_at,initial_count_confirmed_by) VALUES(?,?,UTC_TIMESTAMP(6),?) ON DUPLICATE KEY UPDATE initial_count_confirmed_at=COALESCE(initial_count_confirmed_at,VALUES(initial_count_confirmed_at)),initial_count_confirmed_by=COALESCE(initial_count_confirmed_by,VALUES(initial_count_confirmed_by))',
          [productId,branchId,context.user.id]);
      }
      const preparation=createProductPreparation(db,context), before=await preparation.state(productId);
      const final=before.initial_count_confirmed && priceCents>0 && !before.reasons.some(r=>['DISABLED','CATEGORY_INACTIVE'].includes(r))
        ? await preparation.activate(productId) : before;
      await db.execute(`UPDATE cataloging_drafts SET fields=?,status=?,product_id=?,revision=revision+1,updated_by=?,updated_at=UTC_TIMESTAMP(6),
        reviewed_by=?,reviewed_at=UTC_TIMESTAMP(6),approved_by=?,approved_at=UTC_TIMESTAMP(6) WHERE id=?`,
        [JSON.stringify(f),status,productId,context.user.id,context.user.id,context.user.id,id]);
      return record(id,key,fingerprint,{publication:{branch_id:branchId,price_cents:priceCents,initial_quantity:receipt?.quantity??0,
        ready:final.ready,needs_initial_count:!final.initial_count_confirmed}});
    },
    async photo(id,revision,key,store,uploaded) {
      const fingerprint=hash(['photo',id,revision,hash(uploaded.data).toString('hex')]);
      const current=await row(id);
      const previous=await replay(key,fingerprint); if(previous)return previous;
      if(current.revision!==revision)fail(409,'CATALOGING_EDIT_CONFLICT');
      if(['LINKED','PREPARED'].includes(current.status))fail(409,'CATALOGING_CLOSED');
      if(current.status==='REVIEW' && !context.capabilities.includes('PUBLISH_CATALOGING'))fail(409,'CATALOGING_IN_REVIEW');
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
