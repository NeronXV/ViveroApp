import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

export async function plantSnapshot(db,id) {
  const [[row]]=await db.execute(`SELECT common_name,category_id,description,scientific_name,light_type,watering_advice,price_cents,is_active,
    internal_code,barcode,wholesale_price_cents,unit,recommended_climate,
    (SELECT id FROM product_images WHERE product_id=p.id AND is_primary=1 LIMIT 1) AS image_id FROM products p WHERE id=? FOR UPDATE`,[id]);
  return row ?? {};
}
export async function recordPlantHistory(db,context,id,action,reason,before,after) {
  await db.execute('INSERT INTO plant_history(product_id,branch_id,actor_id,action,reason,before_values,after_values) VALUES(?,?,?,?,?,?,?)',
    [id,context.branch.id,context.user.id,action,reason,JSON.stringify(before),JSON.stringify(after)]);
}
export async function readPlantHistory(db,context,params) {
  requireCapabilities(context,['MANAGE_PRODUCTS'],context.branch?.id);
  if(!context.branch?.is_active)throw new ApiError(403,'BRANCH_FORBIDDEN');
  if([...params.keys()].some(k=>!['product_id','before_id'].includes(k)||params.getAll(k).length!==1))throw new ApiError(400,'INVALID_INPUT');
  const product=params.has('product_id')?positiveId(params.get('product_id')):null;
  const before=params.has('before_id')?positiveId(params.get('before_id')):4294967295;
  const [rows]=await db.execute(`SELECT h.id,h.product_id,p.common_name AS product_name,u.full_name AS actor_name,h.action,h.reason,
    h.before_values,h.after_values,DATE_FORMAT(h.created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS created_at
    FROM plant_history h JOIN products p ON p.id=h.product_id JOIN users u ON u.id=h.actor_id
    WHERE h.branch_id=? AND (? IS NULL OR h.product_id=?) AND h.id<? ORDER BY h.id DESC LIMIT 51`,[context.branch.id,product,product,before]);
  const items=rows.slice(0,50).map(r=>({...r,before_values:typeof r.before_values==='string'?JSON.parse(r.before_values):r.before_values,
    after_values:typeof r.after_values==='string'?JSON.parse(r.after_values):r.after_values}));
  return {items,next_before_id:rows.length>50?items.at(-1).id:null};
}
