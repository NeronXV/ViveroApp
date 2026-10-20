import { createHash } from 'node:crypto';
import { ApiError } from './catalog.js';
import { catalogingKey, catalogingScope, productEditVersion } from './cataloging.js';
import { plantSnapshot, recordPlantHistory } from './plant-history.js';

export async function plantEdit(db,context,request,id,payload,action){
  const header=request.headers['idempotency-key'];
  if(!header){
    const before=await plantSnapshot(db,id),result=await action();
    await recordPlantHistory(db,context,id,'EDIT','Edición de ficha',before,await plantSnapshot(db,id));
    return result;
  }
  catalogingScope(context,request);
  const key=catalogingKey(header),fingerprint=createHash('sha256').update(JSON.stringify(payload)).digest();
  await productEditVersion(db,id);
  const [[event]]=await db.execute('SELECT * FROM plant_edit_events WHERE request_key=? FOR UPDATE',[key]);
  if(event){
    if(event.product_id!==id||event.actor_id!==context.user.id||event.branch_id!==context.branch.id||!event.request_hash.equals(fingerprint))throw new ApiError(409,'CATALOGING_KEY_CONFLICT');
    return typeof event.result==='string'?JSON.parse(event.result):event.result;
  }
  const before=await plantSnapshot(db,id);
  const result={...await action(),revision:(await productEditVersion(db,id)).revision};
  await recordPlantHistory(db,context,id,payload[0]==='photo'?'PHOTO':payload[0]==='confirm'?'CONFIRM':'EDIT',
    payload[0]==='photo'?'Fotografía actualizada':payload[0]==='confirm'?'Ficha confirmada':'Edición de ficha',before,await plantSnapshot(db,id));
  await db.execute('INSERT INTO plant_edit_events(product_id,actor_id,branch_id,request_key,request_hash,result) VALUES(?,?,?,?,?,?)',[id,context.user.id,context.branch.id,key,fingerprint,JSON.stringify(result)]);
  return result;
}
