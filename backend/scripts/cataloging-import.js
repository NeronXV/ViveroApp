import { runImportTransaction, ImportError, canonical } from './catalog-import.js';

export function validateDraftManifest(input) {
  const fail=()=>{throw new ImportError('INVALID_DRAFT_MANIFEST');};
  if(!input || input.schema_version!==1 || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(input.source_key) || !Array.isArray(input.items)||!input.items.length||input.items.length>500)fail();
  const ids=new Set();
  for(const r of input.items) {
    if(!r || Object.keys(r).sort().join(',')!=='fields,original_name,source_evidence,source_id' || typeof r.source_id!=='string' || !/^[A-Za-z0-9_-]{1,80}$/.test(r.source_id)||ids.has(r.source_id)
      || typeof r.original_name!=='string' || !r.original_name.trim() || [...r.original_name].length>240 || !r.original_name.isWellFormed() || /[\u0000-\u001f\u007f]/u.test(r.original_name)
      || !r.fields || Array.isArray(r.fields) || typeof r.fields!=='object' || Object.keys(r.fields).length || !r.source_evidence || typeof r.source_evidence!=='object' || Array.isArray(r.source_evidence))fail();
    ids.add(r.source_id);
  }
  return input;
}
export async function importCatalogingDrafts(db,input,actorId,{apply=false}={}) {
  validateDraftManifest(input);
  if(!Number.isInteger(actorId)||actorId<1)throw new ImportError('INVALID_DRAFT_ACTOR');
  return runImportTransaction(db,apply,'034_cataloging_drafts',async()=>{
    const [[actor]]=await db.execute(`SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=? AND u.is_active=1 AND r.name='OWNER'
      AND EXISTS(SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id WHERE rp.role_id=r.id AND p.name='MANAGE_PRODUCTS')`,[actorId]);
    if(!actor)throw new ImportError('INVALID_DRAFT_ACTOR');
    const items=[];let canApply=true;
    for(const r of input.items) {
      const [[previous]]=await db.execute(`SELECT id,original_name,source_evidence FROM cataloging_drafts WHERE source_key=? AND source_id=?${apply ? ' FOR UPDATE' : ''}`,[input.source_key,r.source_id]);
      const evidence=typeof previous?.source_evidence==='string' ? JSON.parse(previous.source_evidence) : previous?.source_evidence;
      const action=previous ? previous.original_name===r.original_name && canonical(evidence)===canonical(r.source_evidence) ? 'reuse' : 'conflict' : 'create';
      if(action==='conflict')canApply=false;
      items.push({source_id:r.source_id,action,id:previous?.id??null});
    }
    if(apply && canApply)for(const [i,r] of input.items.entries()) {
      if(items[i].action!=='create')continue;
      const [created]=await db.execute('INSERT INTO cataloging_drafts(source_key,source_id,original_name,source_evidence,fields,created_by,updated_by) VALUES(?,?,?,?,?,?,?)',
        [input.source_key,r.source_id,r.original_name,JSON.stringify(r.source_evidence),'{}',actorId,actorId]);
      items[i].id=created.insertId;
    }
    return {mode:apply ? 'apply' : 'dry-run',can_apply:canApply,items,products_created:0};
  });
}
