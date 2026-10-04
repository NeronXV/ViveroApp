import { createHash, randomBytes } from 'node:crypto';
import { reconcileSourceSales } from './source-sales.js';

export class HistoryImportError extends Error {}
const fail = code => { throw new HistoryImportError(code); };
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  return value;
}
const hash = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest();
export function historyDate(value) {
  if (value === null) return null;
  const match = typeof value === 'string' && /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?(?:Z|\+00:00)$/.exec(value);
  if (!match || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0,19) !== `${match[1]}T${match[2]}`) fail('HISTORY_UTC_DATE_REQUIRED');
  return `${match[1]} ${match[2]}.${(match[3] ?? '').padEnd(6,'0')}`;
}
const definitions = [
  { source: 'sales', map: 'history_sales_sources', table: 'sales', id: 'sale_id', dates: ['created_at','updated_at'] },
  { source: 'sale_items', map: 'history_items_sources', table: 'sale_items', id: 'item_id', dates: ['created_at'] },
  { source: 'sale_payment_claims', map: 'history_claims_sources', table: 'sale_payment_claims', id: 'claim_id', dates: ['created_at','expires_at','renewed_at','released_at','consumed_at'] },
  { source: 'sale_payments', map: 'history_payments_sources', table: 'cashier_payments', id: 'payment_id', dates: ['created_at'] },
  { source: 'sale_status_history', map: 'history_history_sources', table: 'sale_status_history', id: 'history_id', dates: ['created_at'] },
];
export async function importHistory(db, bytes, { sourceKey, expectedSha256, apply = false }) {
  const reconciled = reconcileSourceSales(bytes, expectedSha256);
  if (typeof sourceKey !== 'string' || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(sourceKey)) fail('SOURCE_KEY_INVALID');
  const { tables } = JSON.parse(bytes.toString('utf8'));
  for (const definition of definitions) {
    if (tables[definition.source].some(row => typeof row.id !== 'string' || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(row.id))) fail('HISTORY_SOURCE_ID_INVALID');
  }
  const [[lock]] = await db.execute("SELECT GET_LOCK('vivero_history_import',10) AS acquired");
  if (lock.acquired !== 1) fail('IMPORT_LOCK_UNAVAILABLE');
  const maps = {};
  let committing = false;
  try {
    await db.beginTransaction();
    const [migrations] = await db.execute("SELECT id FROM schema_migrations WHERE version='028_history_imports'");
    if (!migrations.length) fail('MIGRATION_REQUIRED');
    for (const [entity,table,id] of [['branches','identity_branch_sources','branch_id'],['users','identity_user_sources','user_id'],['products','catalog_product_sources','product_id']]) {
      const [rows] = await db.execute(`SELECT source_id,${id} AS target_id FROM ${table} WHERE source_key=? FOR UPDATE`,[sourceKey]);
      maps[entity] = new Map(rows.map(row => [row.source_id,row.target_id]));
    }
    const mapped = (entity,id) => {
      if (!maps[entity].has(id)) fail('HISTORY_MAPPING_MISSING');
      return maps[entity].get(id);
    };
    const dataFor = (source,row) => {
      if (source === 'sales') return { folio:row.folio,branch_id:mapped('branches',row.branch_id),created_by:mapped('users',row.created_by),status:row.status,
        subtotal_cents:row.subtotal_cents,discount_cents:row.discount_cents,total_cents:row.total_cents,created_at:historyDate(row.created_at),updated_at:historyDate(row.updated_at) };
      if (source === 'sale_items') return { sale_id:mapped('sales',row.sale_id),product_id:mapped('products',row.product_id),product_name:row.product_name,
        quantity:String(row.quantity),unit_price_cents:row.unit_price_cents,line_total_cents:row.line_total_cents,internal_code:row.internal_code,
        list_price_cents:row.list_price_cents,promotion_id:null,promotion_name:row.promotion_name,created_at:historyDate(row.created_at) };
      if (source === 'sale_payment_claims') return { sale_id:mapped('sales',row.sale_id),branch_id:mapped('branches',row.branch_id),cashier_id:mapped('users',row.cashier_id),
        created_at:historyDate(row.created_at),expires_at:historyDate(row.expires_at),renewed_at:historyDate(row.renewed_at),renewal_count:row.renewal_count,
        released_at:historyDate(row.released_at),consumed_at:historyDate(row.consumed_at),closed_reason:row.closed_reason };
      if (source === 'sale_payments') return { sale_id:mapped('sales',row.sale_id),cashier_id:mapped('users',row.cashier_id),branch_id:mapped('branches',row.branch_id),claim_id:mapped('sale_payment_claims',row.claim_id),
        idempotency_key:createHash('sha256').update(`historical-payment:${sourceKey}:${row.id}`).digest('hex'),method:row.method,amount_due_cents:row.amount_due_cents,
        amount_received_cents:row.amount_received_cents,change_cents:row.change_cents,reference:row.reference,requested_amount_received_cents:row.requested_amount_received_cents,
        request_hash:null,created_at:historyDate(row.created_at) };
      return { sale_id:mapped('sales',row.sale_id),previous_status:row.previous_status,new_status:row.new_status,changed_by:mapped('users',row.changed_by),observation:row.observation,created_at:historyDate(row.changed_at) };
    };
    const readTarget = async (definition,id,data) => {
      const columns = Object.keys(data).map(key => definition.dates.includes(key) ? `DATE_FORMAT(${key},'%Y-%m-%d %H:%i:%s.%f') AS ${key}` : key);
      const [[row]] = await db.execute(`SELECT ${columns.join(',')} FROM ${definition.table} WHERE id=? FOR UPDATE`,[id]);
      return row;
    };
    const items = [];
    for (const definition of definitions) {
      maps[definition.source] = new Map();
      for (const [index,row] of tables[definition.source].entries()) {
        const [[stored]] = await db.execute(`SELECT ${definition.id} AS target_id,source_hash,target_hash FROM ${definition.map} WHERE source_key=? AND source_id=? FOR UPDATE`,[sourceKey,row.id]);
        const data = dataFor(definition.source,row);
        if (stored) {
          if (!stored.source_hash.equals(hash(row))) fail('HISTORY_SOURCE_CHANGED');
          const target = await readTarget(definition,stored.target_id,data);
          if (!target || !stored.target_hash.equals(hash(target))) fail('HISTORY_TARGET_CHANGED');
          maps[definition.source].set(row.id,stored.target_id);
          items.push({ entity:definition.source,index,action:'reuse',target_id:stored.target_id });
          continue;
        }
        if (definition.source === 'sales') {
          const [collision] = await db.execute('SELECT id FROM sales WHERE folio=? FOR UPDATE',[row.folio]);
          if (collision.length) fail('HISTORY_FOLIO_COLLISION');
        }
        let targetId = null;
        if (apply) {
          const insert = definition.source === 'sale_payment_claims' ? { ...data,claim_token:randomBytes(32).toString('hex') } : data;
          const [created] = await db.execute(`INSERT INTO ${definition.table}(${Object.keys(insert).join(',')}) VALUES(${Object.keys(insert).map(() => '?').join(',')})`,Object.values(insert));
          targetId = created.insertId;
          const target = await readTarget(definition,targetId,data);
          await db.execute(`INSERT INTO ${definition.map}(source_key,source_id,${definition.id},source_hash,target_hash) VALUES(?,?,?,?,?)`,[sourceKey,row.id,targetId,hash(row),hash(target)]);
        }
        maps[definition.source].set(row.id,targetId);
        items.push({ entity:definition.source,index,action:'create',target_id:targetId });
      }
    }
    if (apply) { committing = true; await db.commit(); } else await db.rollback();
    return { ...reconciled, import_applied:apply, items };
  } catch (error) {
    await db.rollback().catch(() => {});
    if (committing) fail('HISTORY_COMMIT_UNCERTAIN_RECHECK_SAME_SOURCE');
    if (error instanceof HistoryImportError) throw error;
    fail('HISTORY_IMPORT_FAILED');
  } finally { await db.execute("SELECT RELEASE_LOCK('vivero_history_import')").catch(() => {}); }
}
