import { readFile } from 'node:fs/promises';
import { openAdminDb } from './admin-db.js';
import { importIdentity, IdentityImportError } from './identity-import.js';

let db;
try {
  const args = process.argv.slice(2);
  if (args.length !== 5 || !['--dry-run', '--apply'].includes(args[0]) || args[1] !== '--source-key'
    || args[3] !== '--expected-sha256' || !/^[a-f0-9]{64}$/.test(args[4])) throw new IdentityImportError('USE_MODE_SOURCE_KEY_AND_EXPECTED_SHA256');
  const bytes = await readFile('/imports/source.json');
  const resolutions = JSON.parse(await readFile('/imports/resolutions.json', 'utf8'));
  db = await openAdminDb();
  const result = await importIdentity(db, bytes, { sourceKey: args[2], expectedSha256: args[4], resolutions, apply: args[0] === '--apply' });
  console.log(JSON.stringify(result));
  if (!result.can_prepare) process.exitCode = 2;
} catch (error) {
  console.error(JSON.stringify({ error: error instanceof IdentityImportError ? error.message : 'IDENTITY_IMPORT_FAILED', import_applied: false }));
  process.exitCode = 1;
} finally { if (db) await db.end(); }
