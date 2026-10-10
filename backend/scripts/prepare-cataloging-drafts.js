import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export function parseCsv(text) {
  const rows=[]; let row=[],value='',quoted=false;
  for(let i=0;i<text.length;i++) {
    const c=text[i];
    if(c==='"') { if(quoted && text[i+1]==='"'){value+='"';i++;}else quoted=!quoted; }
    else if(!quoted && c===','){row.push(value);value='';}
    else if(!quoted && (c==='\n'||c==='\r')){if(c==='\r' && text[i+1]==='\n')i++;row.push(value);if(row.some(v=>v!==''))rows.push(row);row=[];value='';}
    else value+=c;
  }
  if(quoted)throw new Error('Unclosed CSV string');
  if(value || row.length){row.push(value);rows.push(row);}
  const headers=rows.shift()?.map(v=>v.replace(/^\uFEFF/,''));
  if(!headers || new Set(headers).size!==headers.length)throw new Error('Invalid CSV header');
  return rows.map(values=>{if(values.length!==headers.length)throw new Error('Invalid CSV row');return Object.fromEntries(headers.map((h,i)=>[h,values[i]]));});
}
export function prepareCatalogingDrafts(records,sourceKey) {
  if(!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(sourceKey)||!records.length||records.length>500)throw new Error('Invalid draft source');
  const ids=new Set();
  return {schema_version:1,source_key:sourceKey,items:records.map(r=>{
    if(!r.entry_id || !/^[A-Za-z0-9_-]{1,80}$/.test(r.entry_id)||ids.has(r.entry_id)||!r.nombre_original?.trim()||[...r.nombre_original].length>240)throw new Error('Invalid original identity');
    ids.add(r.entry_id);
    // Nothing in the reconciliation is an approved commercial field. Keep every
    // candidate/proposal exclusively in provenance for explicit human review.
    return {source_id:r.entry_id,original_name:r.nombre_original,fields:{},source_evidence:r};
  })};
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const [input,output,sourceKey]=process.argv.slice(2);
    const bytes=await readFile(input), manifest=prepareCatalogingDrafts(parseCsv(new TextDecoder('utf-8',{fatal:true}).decode(bytes)),sourceKey);
    await writeFile(output,JSON.stringify({...manifest,input_sha256:createHash('sha256').update(bytes).digest('hex')},null,2),{flag:'wx'});
    console.log(JSON.stringify({drafts:manifest.items.length,products_created:0,imported:false}));
  } catch { console.error('Draft preparation failed; no database was accessed and outputs were not overwritten.');process.exitCode=1; }
}
