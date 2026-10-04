import { readFile,stat } from 'node:fs/promises';
import { openAdminDb } from './admin-db.js';
import { importHistory,HistoryImportError } from './history-import.js';
import { SourceSalesError } from './source-sales.js';
import { SourceExportError } from './source-export-preflight.js';
let db;
try {
  const args=process.argv.slice(2);
  if(args.length!==5||!['--dry-run','--apply'].includes(args[0])||args[1]!=='--source-key'||args[3]!=='--expected-sha256'||!/^[a-f0-9]{64}$/.test(args[4])) throw new HistoryImportError('USE_MODE_SOURCE_KEY_AND_EXPECTED_SHA256');
  const info=await stat('/imports/source.json');
  if(!info.isFile()||info.size>500*1024*1024) throw new HistoryImportError('EXPORT_SIZE_INVALID');
  const bytes=await readFile('/imports/source.json');
  db=await openAdminDb();
  console.log(JSON.stringify(await importHistory(db,bytes,{sourceKey:args[2],expectedSha256:args[4],apply:args[0]==='--apply'})));
} catch(error) {
  const safe=error instanceof HistoryImportError||error instanceof SourceSalesError||error instanceof SourceExportError;
  console.error(JSON.stringify({error:safe?error.message:'HISTORY_IMPORT_FAILED',import_applied:error?.message==='HISTORY_COMMIT_UNCERTAIN_RECHECK_SAME_SOURCE'?null:false}));
  process.exitCode=1;
} finally {if(db)await db.end();}
