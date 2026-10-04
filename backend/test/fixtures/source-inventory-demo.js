import {sourceHistoryFixture} from './source-history-demo.js';
export function sourceInventoryFixture(){
  const source=sourceHistoryFixture(),t=source.tables,id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
  const product=t.products[0].id,user=t.profiles[0].id,[matriz,centro]=t.branches.map(row=>row.id);
  const movement=(n,branch,type,quantity,reference,minute)=>({id:id(n),branch_id:branch,product_id:product,movement_type:type,quantity,reference_id:id(reference),notes:'Demo movement',created_by:user,location_id:null,created_at:`2026-10-01T12:0${minute}:00.123456Z`});
  t.inventory_movements=[movement(20,matriz,'RECEPTION',10,90,0),movement(21,matriz,'TRANSFER_OUT',-3,91,1),movement(22,centro,'TRANSFER_IN',3,91,1),movement(23,centro,'ADJUSTMENT_SUB',-1,24,2)];
  t.inventory_counts=[{id:id(24),branch_id:centro,product_id:product,previous_quantity:3,counted_quantity:2,adjustment_quantity:-1,counted_by:user,reason:'Demo count',location_id:null,created_at:'2026-10-01T12:02:00.123456Z'},
    {id:id(25),branch_id:matriz,product_id:product,previous_quantity:7,counted_quantity:7,adjustment_quantity:0,counted_by:user,reason:'Demo zero count',location_id:null,created_at:'2026-10-01T12:03:00.123456Z'}];
  t.inventory_balances=[{branch_id:matriz,product_id:product,total_quantity:7,updated_at:'2026-10-01T12:03:00.123456Z'},{branch_id:centro,product_id:product,total_quantity:2,updated_at:'2026-10-01T12:03:00.123456Z'}];
  source.primary_keys.find(row=>row.table==='inventory_balances').columns=['branch_id','product_id'];
  return source;
}
