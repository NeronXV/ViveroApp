import test from 'node:test';
import assert from 'node:assert/strict';
import { catalogingFields, catalogingInput, catalogingKey, catalogingScope, checkProductEditVersion } from '../src/cataloging.js';
import { prepareCatalogingDrafts, parseCsv } from '../scripts/prepare-cataloging-drafts.js';
import { validateDraftManifest } from '../scripts/cataloging-import.js';

const context={access_state:'ACTIVE',user:{id:1},role:{name:'MANAGER'},branch:{id:2,is_active:true},capabilities:['MANAGE_PRODUCTS']};
test('draft fields cannot inject price, stock, activation or provenance approval',()=>{
  assert.deepEqual(catalogingFields({presentation:'M06',category_id:null}),{presentation:'M06',category_id:null});
  for(const key of ['price_cents','quantity','is_active','preparation_mode','source_evidence'])assert.throws(()=>catalogingFields({[key]:1}));
  assert.throws(()=>catalogingInput({original_name:'',fields:{}},true));
  assert.throws(()=>catalogingInput({revision:1,action:'SAVE',fields:{},product_id:3}));
  assert.throws(()=>catalogingKey('short'));
});
test('effective permissions and expected identity are enforced',()=>{
  const request={headers:{'x-expected-actor-id':'1','x-expected-branch-id':'2'}};
  catalogingScope(context,request);
  assert.throws(()=>catalogingScope({...context,capabilities:[]},request),{status:403});
  assert.throws(()=>catalogingScope(context,{headers:{...request.headers,'x-expected-actor-id':'3'}}),{status:409});
});
test('product compare-and-save refuses stale versions, older callers retain contract',async()=>{
  const db={execute:async()=>[[{catalog_revision:4}]]};
  await checkProductEditVersion(db,1,'"4"');
  await assert.rejects(checkProductEditVersion(db,1,'"3"'),{status:409,code:'CATALOGING_EDIT_CONFLICT'});
  await checkProductEditVersion(db,1,undefined);
});
test('preparation keeps literal provenance and stable IDs, no commercial defaults',()=>{
  const records=parseCsv('entry_id,nombre_original,presentation,sku_propuesto,costo\nCOT-001,"Original, literal",B02,PROPUESTO,25\n');
  const manifest=prepareCatalogingDrafts(records,'synthetic-review');
  assert.equal(manifest.items[0].original_name,'Original, literal');
  assert.deepEqual(manifest.items[0].fields,{});
  assert.equal(manifest.items[0].source_evidence.sku_propuesto,'PROPUESTO');
  assert.deepEqual(prepareCatalogingDrafts(records,'synthetic-review'),manifest);
  validateDraftManifest(manifest);
  assert.throws(()=>validateDraftManifest({...manifest,items:[{...manifest.items[0],fields:{internal_code:'PROPOSED'}}]}));
  assert.throws(()=>prepareCatalogingDrafts([...records,...records],'synthetic-review'));
});
