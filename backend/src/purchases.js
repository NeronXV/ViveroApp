import { createHash } from 'node:crypto';
import { ApiError, positiveId } from './catalog.js';
import { inventoryScope, lockInventoryRow } from './inventory.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const hash = value => createHash('sha256').update(value).digest();
function exact(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== keys.length || keys.some(k => !Object.hasOwn(value, k))) fail(400, 'PURCHASE_INPUT_INVALID');
}
function text(value, min, max, nullable = false) {
  if (nullable && value === null) return null;
  if (typeof value !== 'string' || !value.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(value)) fail(400, 'PURCHASE_INPUT_INVALID');
  const result = value.trim();
  if ([...result].length < min || [...result].length > max) fail(400, 'PURCHASE_INPUT_INVALID');
  return result;
}
function integer(value, max = Number.MAX_SAFE_INTEGER, zero = false) {
  if (!Number.isSafeInteger(value) || value < (zero ? 0 : 1) || value > max) fail(400, 'PURCHASE_INPUT_INVALID');
  return value;
}
export function purchaseKey(value, operation) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9._:-]{16,128}$/.test(value)) fail(400, 'PURCHASE_KEY_REQUIRED');
  return hash(`${operation}\0${value}`);
}
export function supplierInput(input) {
  exact(input, ['code', 'name', 'is_active']);
  const code = text(input.code, 2, 32).toUpperCase();
  if (!/^[A-Z0-9][A-Z0-9-]{1,31}$/.test(code) || typeof input.is_active !== 'boolean') fail(400, 'SUPPLIER_INPUT_INVALID');
  return { code, name: text(input.name, 2, 160), is_active: input.is_active };
}
export function presentationInput(input) {
  exact(input, ['code', 'display_name', 'nominal_size', 'size_unit', 'notes']);
  const size = input.nominal_size;
  if ((size === null) !== (input.size_unit === null) || (size !== null && (typeof size !== 'string' || !/^(?:0|[1-9][0-9]{0,7})(?:\.[0-9]{1,2})?$/.test(size) || Number(size) <= 0 || !['cm', 'in', 'l', 'gal'].includes(input.size_unit)))) fail(400, 'PRESENTATION_INPUT_INVALID');
  return { code: text(input.code, 1, 24).toUpperCase(), display_name: text(input.display_name, 2, 80, true), nominal_size: size, size_unit: input.size_unit, notes: text(input.notes, 2, 240, true) };
}
export function draftInput(input) {
  exact(input, ['supplier_id', 'document_date', 'external_reference', 'payment_terms', 'expected_total_cents', 'source_file_name', 'items']);
  const date = input.document_date;
  if (typeof date !== 'string' || !/^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$/.test(date) || !Number.isFinite(Date.parse(date + 'T00:00:00Z')) || new Date(date + 'T00:00:00Z').toISOString().slice(0, 10) !== date || !['CASH', 'CREDIT', 'OTHER'].includes(input.payment_terms) || !Array.isArray(input.items) || input.items.length < 1 || input.items.length > 100) fail(400, 'PURCHASE_INPUT_INVALID');
  const lines = new Set();
  let total = 0n;
  const items = input.items.map(item => {
    exact(item, ['line_number', 'raw_description', 'container_code', 'suggested_common_name', 'suggested_presentation', 'quantity', 'unit_cost_cents']);
    const line_number = integer(item.line_number, 2147483647);
    if (lines.has(line_number)) fail(400, 'PURCHASE_LINE_DUPLICATE');
    lines.add(line_number);
    const quantity = integer(item.quantity, 1000000), unit_cost_cents = integer(item.unit_cost_cents, Number.MAX_SAFE_INTEGER, true);
    total += BigInt(quantity) * BigInt(unit_cost_cents);
    return { line_number, raw_description: text(item.raw_description, 2, 240), container_code: text(item.container_code, 1, 24).toUpperCase(), suggested_common_name: text(item.suggested_common_name, 2, 160, true), suggested_presentation: text(item.suggested_presentation, 2, 120, true), quantity, unit_cost_cents };
  }).sort((a, b) => a.line_number - b.line_number);
  if (total !== BigInt(integer(input.expected_total_cents, Number.MAX_SAFE_INTEGER, true))) fail(400, 'PURCHASE_TOTAL_MISMATCH');
  return { supplier_id: integer(input.supplier_id, 4294967295), document_date: date, external_reference: text(input.external_reference, 1, 80, true), payment_terms: input.payment_terms, expected_total_cents: input.expected_total_cents, source_file_name: text(input.source_file_name, 1, 180, true), items };
}
export function resolutionInput(input) {
  exact(input, ['resolution_status', 'product_id', 'suggested_common_name', 'suggested_presentation']);
  if (!['MATCHED', 'IGNORED'].includes(input.resolution_status) || (input.resolution_status === 'IGNORED' ? input.product_id !== null : !Number.isInteger(input.product_id))) fail(400, 'PURCHASE_RESOLUTION_INVALID');
  return { resolution_status: input.resolution_status, product_id: input.product_id === null ? null : integer(input.product_id, 4294967295), suggested_common_name: text(input.suggested_common_name, 2, 160, true), suggested_presentation: text(input.suggested_presentation, 2, 120, true) };
}
export function purchaseQuery(params, suppliers = false) {
  const allowed = suppliers ? ['limit', 'after_id', 'include_inactive'] : ['limit', 'after_id', 'status'];
  if ([...params.keys()].some(k => !allowed.includes(k) || params.getAll(k).length !== 1)) fail(400, 'PURCHASE_QUERY_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  if (limit > 100 || (params.has('status') && !['DRAFT', 'RECEIVED', 'CANCELLED'].includes(params.get('status'))) || (params.has('include_inactive') && !['true', 'false'].includes(params.get('include_inactive')))) fail(400, 'PURCHASE_QUERY_INVALID');
  return { limit, after_id: params.has('after_id') ? positiveId(params.get('after_id')) : 0, status: params.get('status'), include_inactive: params.get('include_inactive') === 'true' };
}
const aliasHash = item => hash(JSON.stringify([item.raw_description.toLowerCase(), item.container_code.toLowerCase()]));
const page = (rows, query) => ({ schema_version: 1, items: rows.slice(0, query.limit), next_after_id: rows.length > query.limit ? rows[query.limit - 1].id : null });
export function createPurchases(db, context) {
  const branchId = inventoryScope(context, true);
  async function supplier(id, active = false) {
    const [[row]] = await db.execute('SELECT id, code, name, is_active FROM suppliers WHERE id = ? FOR UPDATE', [id]);
    if (!row || (active && !row.is_active)) fail(409, 'SUPPLIER_UNAVAILABLE');
    return { ...row, is_active: Boolean(row.is_active) };
  }
  async function purchase(id) {
    const [[row]] = await db.execute('SELECT * FROM supplier_purchase_documents WHERE id = ? AND branch_id = ? FOR UPDATE', [id, branchId]);
    if (!row) fail(404, 'PURCHASE_NOT_FOUND');
    return row;
  }
  async function detail(id) {
    const row = await purchase(id);
    const [items] = await db.execute('SELECT i.id,i.line_number,i.raw_description,i.supplier_container_code,i.suggested_common_name,i.suggested_presentation,i.quantity,i.unit_cost_cents,i.line_total_cents,i.product_id,i.resolution_status,i.resolved_by,i.resolved_at,i.movement_id,p.display_name AS supplier_presentation FROM supplier_purchase_items i LEFT JOIN supplier_presentations p ON p.supplier_id = ? AND p.code = i.supplier_container_code WHERE i.purchase_id = ? ORDER BY i.line_number', [row.supplier_id, id]);
    return { schema_version: 1, purchase: { id: row.id, supplier: await supplier(row.supplier_id), created_at: row.created_at, item_count: items.length, unmatched_count: items.filter(i => i.resolution_status === 'UNMATCHED').length, supplier_id: row.supplier_id, branch_id: row.branch_id, document_date: row.document_date, external_reference: row.external_reference, payment_terms: row.payment_terms, expected_total_cents: Number(row.expected_total_cents), currency: row.currency, source_file_name: row.source_file_name, status: row.status, created_by: row.created_by, received_by: row.received_by, received_at: row.received_at, items: items.map(i => ({ ...i, unit_cost_cents: Number(i.unit_cost_cents), line_total_cents: Number(i.line_total_cents) })) } };
  }
  async function availableProduct(id) {
    const [[row]] = await db.execute('SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.id = ? AND p.is_active = 1 AND c.is_active = 1 LOCK IN SHARE MODE', [id]);
    if (!row) fail(409, 'PURCHASE_PRODUCT_UNAVAILABLE');
  }
  return {
    detail,
    async suppliers(query) {
      const [rows] = await db.execute('SELECT id,code,name,is_active FROM suppliers WHERE id > ? AND (is_active = 1 OR ? = 1) ORDER BY id LIMIT ?', [query.after_id, query.include_inactive, query.limit + 1]);
      return page(rows.map(r => ({ ...r, is_active: Boolean(r.is_active) })), query);
    },
    async saveSupplier(id, input) {
      if (id) {
        await supplier(id);
        await db.execute('UPDATE suppliers SET code = ?, name = ?, is_active = ?, updated_at = UTC_TIMESTAMP(6) WHERE id = ?', [input.code, input.name, input.is_active, id]);
      } else {
        const [row] = await db.execute('INSERT INTO suppliers(code,name,is_active) VALUES(?,?,?)', [input.code, input.name, input.is_active]);
        id = row.insertId;
      }
      return { schema_version: 1, supplier: await supplier(id) };
    },
    async presentations(id) {
      await supplier(id);
      const [items] = await db.execute('SELECT id,code,display_name,nominal_size,size_unit,notes FROM supplier_presentations WHERE supplier_id = ? ORDER BY id', [id]);
      return { schema_version: 1, items };
    },
    async savePresentation(id, input) {
      await supplier(id, true);
      const [[existing]] = await db.execute('SELECT id FROM supplier_presentations WHERE supplier_id=? AND code=? FOR UPDATE', [id, input.code]);
      if (existing) await db.execute('UPDATE supplier_presentations SET display_name=?,nominal_size=?,size_unit=?,notes=? WHERE id=?', [input.display_name, input.nominal_size, input.size_unit, input.notes, existing.id]);
      else await db.execute('INSERT INTO supplier_presentations(supplier_id,code,display_name,nominal_size,size_unit,notes) VALUES(?,?,?,?,?,?)', [id, input.code, input.display_name, input.nominal_size, input.size_unit, input.notes]);
      const [items] = await db.execute('SELECT id,code,display_name,nominal_size,size_unit,notes FROM supplier_presentations WHERE supplier_id=? ORDER BY id', [id]);
      return { schema_version: 1, items };
    },
    async list(query) {
      const [rows] = await db.execute(`SELECT d.id,d.supplier_id,d.document_date,d.external_reference,d.payment_terms,d.expected_total_cents,d.status,d.received_at,d.created_at,s.code AS supplier_code,s.name AS supplier_name,(SELECT COUNT(*) FROM supplier_purchase_items i WHERE i.purchase_id=d.id) AS item_count,(SELECT COUNT(*) FROM supplier_purchase_items i WHERE i.purchase_id=d.id AND i.resolution_status='UNMATCHED') AS unmatched_count FROM supplier_purchase_documents d JOIN suppliers s ON s.id=d.supplier_id WHERE d.branch_id = ? AND d.id > ? AND (? IS NULL OR d.status = ?) ORDER BY d.id LIMIT ?`, [branchId, query.after_id, query.status, query.status, query.limit + 1]);
      return { ...page(rows.map(r => ({ ...r, supplier: { id: r.supplier_id, code: r.supplier_code, name: r.supplier_name }, expected_total_cents: Number(r.expected_total_cents) })), query), branch_id: branchId };
    },
    async draft(input, key) {
      // Branch is locked by withAccess; supplier lock serializes same-supplier drafts.
      await supplier(input.supplier_id); // A valid retry remains recoverable after deactivation.
      const requestHash = hash(JSON.stringify(input));
      const [[prior]] = await db.execute('SELECT id,branch_id,created_by,request_hash FROM supplier_purchase_documents WHERE idempotency_hash = ? FOR UPDATE', [key]);
      if (prior) {
        if (prior.branch_id !== branchId || prior.created_by !== context.user.id || !prior.request_hash.equals(requestHash)) fail(409, 'PURCHASE_IDEMPOTENCY_CONFLICT');
        return { ...await detail(prior.id), idempotent_replay: true };
      }
      await supplier(input.supplier_id, true);
      const [created] = await db.execute('INSERT INTO supplier_purchase_documents(supplier_id,branch_id,document_date,external_reference,payment_terms,expected_total_cents,source_file_name,source_items,idempotency_hash,request_hash,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)', [input.supplier_id, branchId, input.document_date, input.external_reference, input.payment_terms, input.expected_total_cents, input.source_file_name, JSON.stringify(input.items), key, requestHash, context.user.id]);
      for (const item of input.items) {
        const [[alias]] = await db.execute('SELECT a.product_id FROM supplier_product_aliases a JOIN products p ON p.id=a.product_id JOIN categories c ON c.id=p.category_id WHERE a.supplier_id=? AND a.alias_hash=? AND p.is_active=1 AND c.is_active=1', [input.supplier_id, aliasHash(item)]);
        await db.execute('INSERT IGNORE INTO supplier_presentations(supplier_id,code) VALUES(?,?)', [input.supplier_id, item.container_code]);
        await db.execute('INSERT INTO supplier_purchase_items(purchase_id,line_number,raw_description,supplier_container_code,suggested_common_name,suggested_presentation,quantity,unit_cost_cents,line_total_cents,product_id,resolution_status,resolved_by,resolved_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)', [created.insertId, item.line_number, item.raw_description, item.container_code, item.suggested_common_name, item.suggested_presentation, item.quantity, item.unit_cost_cents, (BigInt(item.quantity) * BigInt(item.unit_cost_cents)).toString(), alias?.product_id ?? null, alias ? 'AUTO_MATCHED' : 'UNMATCHED', alias ? context.user.id : null, alias ? new Date() : null]);
      }
      return { ...await detail(created.insertId), idempotent_replay: false };
    },
    async resolve(id, itemId, input) {
      const doc = await purchase(id);
      if (doc.status !== 'DRAFT') fail(409, 'PURCHASE_NOT_DRAFT');
      const [[item]] = await db.execute('SELECT * FROM supplier_purchase_items WHERE id = ? AND purchase_id = ? FOR UPDATE', [itemId, id]);
      if (!item) fail(404, 'PURCHASE_ITEM_NOT_FOUND');
      if (input.product_id !== null) await availableProduct(input.product_id);
      await db.execute('UPDATE supplier_purchase_items SET product_id=?,resolution_status=?,resolved_by=?,resolved_at=UTC_TIMESTAMP(6),suggested_common_name=COALESCE(?,suggested_common_name),suggested_presentation=COALESCE(?,suggested_presentation) WHERE id=?', [input.product_id, input.resolution_status, context.user.id, input.suggested_common_name, input.suggested_presentation, itemId]);
      if (input.product_id !== null) {
        const key = aliasHash({ raw_description: item.raw_description, container_code: item.supplier_container_code });
        const [[alias]] = await db.execute('SELECT id FROM supplier_product_aliases WHERE supplier_id=? AND alias_hash=? FOR UPDATE', [doc.supplier_id, key]);
        if (alias) await db.execute('UPDATE supplier_product_aliases SET product_id=?,updated_by=?,raw_description=?,supplier_container_code=? WHERE id=?', [input.product_id, context.user.id, item.raw_description, item.supplier_container_code, alias.id]);
        else await db.execute('INSERT INTO supplier_product_aliases(supplier_id,alias_hash,raw_description,supplier_container_code,product_id,updated_by) VALUES(?,?,?,?,?,?)', [doc.supplier_id, key, item.raw_description, item.supplier_container_code, input.product_id, context.user.id]);
      }
      return detail(id);
    },
    async confirm(id, key) {
      const doc = await purchase(id);
      if (doc.status === 'RECEIVED') {
        if (!doc.confirmation_hash.equals(key)) fail(409, 'PURCHASE_CONFIRMATION_CONFLICT');
        return { ...await detail(id), idempotent_replay: true };
      }
      const [items] = await db.execute('SELECT * FROM supplier_purchase_items WHERE purchase_id=? ORDER BY product_id,line_number FOR UPDATE', [id]);
      if (doc.status !== 'DRAFT' || items.some(i => i.resolution_status === 'UNMATCHED') || !items.some(i => i.product_id !== null)) fail(409, 'PURCHASE_NOT_READY');
      for (const item of items.filter(i => i.product_id !== null)) {
        await availableProduct(item.product_id);
        await lockInventoryRow(db, branchId, item.product_id);
        const [movement] = await db.execute("INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity,idempotency_hash,notes,created_by) VALUES(?,?,'RECEPTION',?,?,?,?)", [branchId, item.product_id, item.quantity, hash(`supplier-purchase-line\0${item.id}`), `Compra ${id}, línea ${item.line_number}`, context.user.id]);
        await db.execute('UPDATE supplier_purchase_items SET movement_id=? WHERE id=?', [movement.insertId, item.id]);
      }
      await db.execute("UPDATE supplier_purchase_documents SET status='RECEIVED',confirmation_hash=?,received_by=?,received_at=UTC_TIMESTAMP(6) WHERE id=?", [key, context.user.id, id]);
      return { ...await detail(id), idempotent_replay: false };
    },
  };
}
