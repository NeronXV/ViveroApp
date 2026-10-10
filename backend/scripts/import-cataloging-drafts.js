import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { openAdminDb } from './admin-db.js';
import { importCatalogingDrafts } from './cataloging-import.js';

let db;
try {
  const [mode,hashOption,expected]=process.argv.slice(2);
  if(!['--dry-run','--apply'].includes(mode) || hashOption!=='--expected-sha256' || !/^[a-f0-9]{64}$/.test(expected ?? '') || process.argv.length!==5)throw new Error('Arguments');
  const bytes=await readFile('/imports/cataloging-drafts.json');
  if(bytes.length>5*1024*1024||createHash('sha256').update(bytes).digest('hex')!==expected)throw new Error('Input hash');
  const input=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
  db=await openAdminDb(); // Explicitly restricted to local development, never production.
  const result=await importCatalogingDrafts(db,input,Number(process.env.CATALOGING_ACTOR_ID),{apply:mode==='--apply'});
  console.log(JSON.stringify(result));if(!result.can_apply)process.exitCode=2;
}catch{console.error('Cataloging draft import blocked. No product or inventory import was attempted.');process.exitCode=1;}
finally{if(db)await db.end();}
