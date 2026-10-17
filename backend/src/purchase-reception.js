import { ApiError, positiveId } from './catalog.js';

const invalid=()=>{throw new ApiError(400,'PURCHASE_INPUT_INVALID');};
export function receptionInput(value) {
  if(!value || Object.keys(value).sort().join(',')!=='items,revision' || !Number.isSafeInteger(value.revision) || value.revision<1 || !Array.isArray(value.items) || !value.items.length || value.items.length>100) invalid();
  const seen=new Set();
  return {revision:value.revision,items:value.items.map(line=>{
    if(!line || Object.keys(line).sort().join(',')!=='id,new_product,product_id,quantity')invalid();
    const id=positiveId(line.id);if(seen.has(id))invalid();seen.add(id);
    if(!Number.isSafeInteger(line.quantity)||line.quantity<0||line.quantity>1000000)invalid();
    const product_id=line.product_id===null?null:positiveId(line.product_id);
    let new_product=null;
    if(line.new_product!==null){
      const p=line.new_product;
      if(!p || Object.keys(p).sort().join(',')!=='category_id,name,presentation')invalid();
      if(typeof p.name!=='string'||!p.name.trim()||[...p.name.trim()].length>160||/[\x00-\x1f\x7f]/.test(p.name))invalid();
      if(p.presentation!==null&&(typeof p.presentation!=='string'||[...p.presentation.trim()].length>120||/[\x00-\x1f\x7f]/.test(p.presentation)))invalid();
      if(p.category_id!==null&&(!p.presentation?.trim()||[...`${p.name.trim()} · ${p.presentation.trim()}`].length>160))invalid();
      new_product={name:p.name.trim(),presentation:p.presentation?.trim()||null,category_id:p.category_id===null?null:positiveId(p.category_id)};
    }
    if(line.quantity>0 && Boolean(product_id)===Boolean(new_product))invalid();
    if(line.quantity===0 && (product_id||new_product))invalid();
    return {id,quantity:line.quantity,product_id,new_product};
  }).sort((a,b)=>a.id-b.id)};
}
export function confirmationInput(value){
  if(!value || typeof value!=='object'||Array.isArray(value))invalid();
  if(!Object.keys(value).length)return {};
  if(Object.keys(value).join(',')!=='revision'||!Number.isSafeInteger(value.revision)||value.revision<1)invalid();
  return {revision:value.revision};
}
