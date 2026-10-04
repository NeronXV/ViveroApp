import { inspectSourceExport } from './source-export-preflight.js';
import { historyDate } from './history-import.js';
export class SourceInventoryError extends Error {}
const fail=code=>{throw new SourceInventoryError(code);};
export function quantityMilli(value) {
  const text=String(value);
  if(!['number','string'].includes(typeof value)||! /^-?\d{1,11}(?:\.\d{1,3})?$/.test(text)) fail('INVENTORY_PRECISION_INVALID');
  const negative=text.startsWith('-'),[whole,fraction='']=text.replace(/^-/,'').split('.');
  const number=BigInt(whole)*1000n+BigInt(fraction.padEnd(3,'0'));
  return negative?-number:number;
}
export const scopeKey=row=>JSON.stringify([row.branch_id,row.product_id]);
export function reconcileSourceInventory(bytes,expectedSha256) {
  const inspected=inspectSourceExport(bytes);
  if(inspected.input_sha256!==expectedSha256) fail('INPUT_HASH_MISMATCH');
  const {tables}=JSON.parse(bytes.toString('utf8'));
  if(tables.inventory_locations.length||tables.inventory_movements.some(row=>row.location_id!==null)||tables.inventory_counts.some(row=>row.location_id!==null)) fail('INVENTORY_LOCATIONS_REQUIRE_MAPPING');
  const balances=new Map(),transfers=new Map(),countMoves=new Map();
  for(const row of tables.inventory_balances){
    const key=scopeKey(row);
    if(balances.has(key)||quantityMilli(row.total_quantity)<0n) fail('INVENTORY_BALANCE_INVALID');
    if(!tables.branches.some(b=>b.id===row.branch_id)||!tables.products.some(p=>p.id===row.product_id)) fail('INVENTORY_REFERENCE_INVALID');
    historyDate(row.updated_at);balances.set(key,row);
  }
  const ordered=[...tables.inventory_movements].sort((a,b)=>historyDate(a.created_at).localeCompare(historyDate(b.created_at))||a.id.localeCompare(b.id));
  const sums=new Map();
  for(const row of ordered){
    const key=scopeKey(row),delta=quantityMilli(row.quantity);
    if(!balances.has(key)||!tables.profiles.some(user=>user.id===row.created_by)) fail('INVENTORY_REFERENCE_INVALID');
    if(!['RECEPTION','ADJUSTMENT_ADD','ADJUSTMENT_SUB','TRANSFER_IN','TRANSFER_OUT'].includes(row.movement_type)) fail('INVENTORY_TYPE_REQUIRES_MAPPING');
    if((['RECEPTION','ADJUSTMENT_ADD','TRANSFER_IN'].includes(row.movement_type)?delta<=0n:delta>=0n)) fail('INVENTORY_SIGN_INVALID');
    const before=sums.get(key)??0n,after=before+delta;
    if(after<0n) fail('INVENTORY_NEGATIVE_PREFIX');
    sums.set(key,after);
    if(row.movement_type.startsWith('TRANSFER')){
      if(!row.reference_id) fail('INVENTORY_TRANSFER_REFERENCE_INVALID');
      const reference=JSON.stringify([row.reference_id,row.product_id]);
      const group=transfers.get(reference)??[];group.push(row);transfers.set(reference,group);
    }
    if(['ADJUSTMENT_ADD','ADJUSTMENT_SUB'].includes(row.movement_type)&&tables.inventory_counts.some(count=>count.id===row.reference_id)){
      if(countMoves.has(row.reference_id)) fail('INVENTORY_COUNT_MOVEMENT_AMBIGUOUS');
      const count=tables.inventory_counts.find(count=>count.id===row.reference_id);
      if(scopeKey(count)!==key||quantityMilli(count.previous_quantity)!==before||quantityMilli(count.counted_quantity)!==after||count.counted_by!==row.created_by) fail('INVENTORY_COUNT_LEDGER_MISMATCH');
      countMoves.set(row.reference_id,row.id);
    }
  }
  for(const [key,row]of balances)if((sums.get(key)??0n)!==quantityMilli(row.total_quantity))fail('INVENTORY_LEDGER_BALANCE_MISMATCH');
  for(const group of transfers.values())if(group.length!==2||group[0].branch_id===group[1].branch_id||quantityMilli(group[0].quantity)+quantityMilli(group[1].quantity)!==0n||!group.some(row=>row.movement_type==='TRANSFER_IN')||!group.some(row=>row.movement_type==='TRANSFER_OUT'))fail('INVENTORY_TRANSFER_PAIR_INVALID');
  for(const count of tables.inventory_counts){
    historyDate(count.created_at);
    const previous=quantityMilli(count.previous_quantity),counted=quantityMilli(count.counted_quantity),adjustment=quantityMilli(count.adjustment_quantity);
    if(!balances.has(scopeKey(count))||!tables.profiles.some(row=>row.id===count.counted_by)||previous<0n||counted<0n||counted-previous!==adjustment)fail('INVENTORY_COUNT_INVALID');
    if((adjustment!==0n)!==countMoves.has(count.id))fail('INVENTORY_COUNT_MOVEMENT_MISSING');
    if(adjustment===0n){const atCount=ordered.filter(row=>scopeKey(row)===scopeKey(count)&&historyDate(row.created_at)<=historyDate(count.created_at)).reduce((total,row)=>total+quantityMilli(row.quantity),0n);if(atCount!==counted)fail('INVENTORY_ZERO_COUNT_LEDGER_MISMATCH');}
  }
  for(const product of tables.products)if(quantityMilli(product.minimum_stock)<0n)fail('INVENTORY_MINIMUM_INVALID');
  const scopes=[...tables.inventory_balances];
  for(const branch of tables.branches)for(const product of tables.products){
    if(quantityMilli(product.minimum_stock)>0n&&!balances.has(JSON.stringify([branch.id,product.id])))scopes.push({branch_id:branch.id,product_id:product.id,total_quantity:0,updated_at:product.updated_at,derived_from_product_minimum:true});
  }
  return {input_sha256:inspected.input_sha256,reconciled:true,import_applied:false,
    counts:{balances:balances.size,balance_scopes:scopes.length,derived_zero_minimum_scopes:scopes.length-balances.size,movements:ordered.length,counts:tables.inventory_counts.length,transfer_pairs:transfers.size},
    scopes,ordered,transfer_pairs:[...transfers.values()].map(group=>group.map(row=>row.id)),count_movements:Object.fromEntries(countMoves)};
}
