import {createHash} from 'node:crypto';
import {reconcileSourceInventory,quantityMilli,scopeKey} from './source-inventory.js';
import {historyDate} from './history-import.js';
export class InventoryImportError extends Error {}
const fail=code=>{throw new InventoryImportError(code);};
function canonical(value){if(Buffer.isBuffer(value))return value.toString('hex');if(Array.isArray(value))return value.map(canonical);if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])]));return value;}
const hash=value=>createHash('sha256').update(JSON.stringify(canonical(value))).digest();
const decimal=value=>{const amount=quantityMilli(value),absolute=amount<0n?-amount:amount;return `${amount<0n?'-':''}${absolute/1000n}.${String(absolute%1000n).padStart(3,'0')}`;};
export async function importInventory(db,bytes,{sourceKey,expectedSha256,apply=false}){
  const reconciled=reconcileSourceInventory(bytes,expectedSha256),{tables}=JSON.parse(bytes.toString('utf8'));
  if(typeof sourceKey!=='string'||!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(sourceKey))fail('SOURCE_KEY_INVALID');
  for(const row of [...tables.inventory_movements,...tables.inventory_counts])if(typeof row.id!=='string'||!/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(row.id))fail('INVENTORY_SOURCE_ID_INVALID');
  for(const row of tables.inventory_movements)if(row.reference_id!==null&&(typeof row.reference_id!=='string'||!/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(row.reference_id)))fail('INVENTORY_SOURCE_REFERENCE_INVALID');
  const [[lock]]=await db.execute("SELECT GET_LOCK('vivero_inventory_import',10) AS acquired");if(lock.acquired!==1)fail('IMPORT_LOCK_UNAVAILABLE');
  let committing=false;
  try{
    await db.beginTransaction();
    const [migration]=await db.execute("SELECT id FROM schema_migrations WHERE version='029_inventory_imports'");if(!migration.length)fail('MIGRATION_REQUIRED');
    const maps={};
    for(const [entity,table,id]of [['branches','identity_branch_sources','branch_id'],['users','identity_user_sources','user_id'],['products','catalog_product_sources','product_id']]){const [rows]=await db.execute(`SELECT source_id,${id} AS target_id FROM ${table} WHERE source_key=? FOR UPDATE`,[sourceKey]);maps[entity]=new Map(rows.map(row=>[row.source_id,row.target_id]));}
    const mapped=(entity,id)=>{if(!maps[entity].has(id))fail('INVENTORY_MAPPING_MISSING');return maps[entity].get(id);};
    const readBalance=async id=>{const [[row]]=await db.execute("SELECT branch_id,product_id,quantity,minimum_stock,DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s.%f') AS updated_at FROM inventory WHERE id=? FOR UPDATE",[id]);return row;};
    const readMovement=async id=>{const [[row]]=await db.execute("SELECT branch_id,product_id,movement_type,quantity,idempotency_hash,notes,created_by,transfer_counterpart_id,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') AS created_at FROM inventory_movements WHERE id=? FOR UPDATE",[id]);return row;};
    const readCount=async id=>{const [[row]]=await db.execute("SELECT movement_id,idempotency_hash,branch_id,product_id,previous_quantity,counted_quantity,adjustment_quantity,reason,counted_by,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') AS created_at FROM inventory_counts WHERE id=? FOR UPDATE",[id]);return row;};
    const [storedBalances]=await db.execute('SELECT * FROM inventory_balance_sources WHERE source_key=? FOR UPDATE',[sourceKey]);
    const [storedMovements]=await db.execute('SELECT * FROM inventory_movements_sources WHERE source_key=? FOR UPDATE',[sourceKey]);
    const [storedCounts]=await db.execute('SELECT * FROM inventory_counts_sources WHERE source_key=? FOR UPDATE',[sourceKey]);
    const minimum=row=>tables.products.find(product=>product.id===row.product_id).minimum_stock;
    const summary={input_sha256:reconciled.input_sha256,reconciled:true,counts:reconciled.counts,import_applied:apply};
    if(storedBalances.length||storedMovements.length||storedCounts.length){
      if(storedBalances.length!==reconciled.scopes.length||storedMovements.length!==tables.inventory_movements.length||storedCounts.length!==tables.inventory_counts.length)fail('INVENTORY_PARTIAL_MAPPING');
      for(const stored of storedBalances){const row=reconciled.scopes.find(row=>row.branch_id===stored.source_branch_id&&row.product_id===stored.source_product_id);if(!row||!stored.source_hash.equals(hash({row,minimum_stock:minimum(row)})))fail('INVENTORY_SOURCE_CHANGED');const target=await readBalance(stored.inventory_id);if(!target||!stored.target_hash.equals(hash(target)))fail('INVENTORY_TARGET_CHANGED');}
      for(const [storedRows,source,reader,key]of [[storedMovements,tables.inventory_movements,readMovement,'movement_id'],[storedCounts,tables.inventory_counts,readCount,'count_id']])for(const stored of storedRows){const row=source.find(row=>row.id===stored.source_id);if(!row||!stored.source_hash.equals(hash(row)))fail('INVENTORY_SOURCE_CHANGED');const target=await reader(stored[key]);if(!target||!stored.target_hash.equals(hash(target)))fail('INVENTORY_TARGET_CHANGED');}
      await db.rollback();return {...summary,action:'reuse'};
    }
    const balanceIds=new Map(),movementIds=new Map(),countIds=new Map();
    for(const row of reconciled.scopes){
      const branch=mapped('branches',row.branch_id),product=mapped('products',row.product_id);
      const [existing]=await db.execute('SELECT id FROM inventory WHERE branch_id=? AND product_id=? FOR UPDATE',[branch,product]);if(existing.length)fail('INVENTORY_UNMAPPED_SCOPE_COLLISION');
      if(apply){const [inserted]=await db.execute('INSERT INTO inventory(branch_id,product_id,quantity,minimum_stock) VALUES(?,?,0,?)',[branch,product,decimal(minimum(row))]);balanceIds.set(scopeKey(row),inserted.insertId);}
    }
    // Validate all actor mappings even in dry-run.
    for(const row of tables.inventory_movements)mapped('users',row.created_by);
    for(const row of tables.inventory_counts)mapped('users',row.counted_by);
    if(!apply){await db.rollback();return {...summary,import_applied:false,action:'create'};}
    for(const row of reconciled.ordered){
      const [inserted]=await db.execute('INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity,idempotency_hash,notes,created_by,created_at) VALUES(?,?,?,?,?,?,?,?)',
        [mapped('branches',row.branch_id),mapped('products',row.product_id),row.movement_type,decimal(row.quantity),hash(['historical-inventory',sourceKey,row.id]),row.notes,mapped('users',row.created_by),historyDate(row.created_at)]);
      movementIds.set(row.id,inserted.insertId);
    }
    for(const [left,right]of reconciled.transfer_pairs){await db.execute('UPDATE inventory_movements SET transfer_counterpart_id=? WHERE id=?',[movementIds.get(right),movementIds.get(left)]);await db.execute('UPDATE inventory_movements SET transfer_counterpart_id=? WHERE id=?',[movementIds.get(left),movementIds.get(right)]);}
    for(const row of tables.inventory_counts){
      const movement=reconciled.count_movements[row.id];
      const [inserted]=await db.execute('INSERT INTO inventory_counts(movement_id,idempotency_hash,branch_id,product_id,previous_quantity,counted_quantity,adjustment_quantity,reason,counted_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)',
        [movement?movementIds.get(movement):null,hash(['historical-count',sourceKey,row.id]),mapped('branches',row.branch_id),mapped('products',row.product_id),decimal(row.previous_quantity),decimal(row.counted_quantity),decimal(row.adjustment_quantity),row.reason,mapped('users',row.counted_by),historyDate(row.created_at)]);
      countIds.set(row.id,inserted.insertId);
    }
    for(const row of reconciled.scopes){
      const id=balanceIds.get(scopeKey(row)),current=await readBalance(id);
      if(quantityMilli(current.quantity)!==quantityMilli(row.total_quantity))fail('INVENTORY_SQL_BALANCE_MISMATCH');
      await db.execute('UPDATE inventory SET updated_at=? WHERE id=?',[historyDate(row.updated_at),id]);
      await db.execute('INSERT INTO inventory_balance_sources(source_key,source_branch_id,source_product_id,inventory_id,source_hash,target_hash) VALUES(?,?,?,?,?,?)',[sourceKey,row.branch_id,row.product_id,id,hash({row,minimum_stock:minimum(row)}),hash(await readBalance(id))]);
    }
    for(const row of tables.inventory_movements)await db.execute('INSERT INTO inventory_movements_sources(source_key,source_id,source_reference_id,movement_id,source_hash,target_hash) VALUES(?,?,?,?,?,?)',[sourceKey,row.id,row.reference_id,movementIds.get(row.id),hash(row),hash(await readMovement(movementIds.get(row.id)))]);
    for(const row of tables.inventory_counts)await db.execute('INSERT INTO inventory_counts_sources(source_key,source_id,count_id,source_hash,target_hash) VALUES(?,?,?,?,?)',[sourceKey,row.id,countIds.get(row.id),hash(row),hash(await readCount(countIds.get(row.id)))]);
    committing=true;await db.commit();return {...summary,action:'create'};
  }catch(error){await db.rollback().catch(()=>{});if(committing)fail('INVENTORY_COMMIT_UNCERTAIN_RECHECK_SAME_SOURCE');if(error instanceof InventoryImportError)throw error;fail('INVENTORY_IMPORT_FAILED');}
  finally{await db.execute("SELECT RELEASE_LOCK('vivero_inventory_import')").catch(()=>{});}
}
