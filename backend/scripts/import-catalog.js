import { open } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { openAdminDb } from './admin-db.js';
import { ImportError, importCatalog } from './catalog-import.js';
import { importPromotions } from './promotion-import.js';
import { importImages } from './image-import.js';
import { createImageStore } from '../src/images.js';

let db;
try {
  const args = process.argv.slice(2);
  const apply = args[0] === '--apply';
  if (!(args.length === 0 || (args.length === 1 && args[0] === '--dry-run') || (args.length === 3 && apply && args[1] === '--expected-sha256' && /^[a-f0-9]{64}$/.test(args[2])))) {
    throw new ImportError('USE_DRY_RUN_OR_APPLY_WITH_EXPECTED_SHA256');
  }
  const file = await open('/imports/catalog.json', 'r');
  let bytes;
  try {
    const stat = await file.stat();
    if (!stat.isFile() || stat.size > 5 * 1024 * 1024) throw new ImportError('FILE_LIMIT');
    const buffer = Buffer.alloc(5 * 1024 * 1024 + 1);
    let size = 0;
    while (size < buffer.length) {
      const { bytesRead } = await file.read(buffer, size, buffer.length - size, null);
      if (!bytesRead) break;
      size += bytesRead;
    }
    if (size > 5 * 1024 * 1024) throw new ImportError('FILE_LIMIT');
    bytes = buffer.subarray(0, size);
  } finally { await file.close(); }
  const digest = createHash('sha256').update(bytes).digest('hex');
  if (apply && args[2] !== digest) throw new ImportError('INPUT_HASH_MISMATCH');
  let input;
  try { input = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); }
  catch { throw new ImportError('INVALID_JSON'); }
  db = await openAdminDb();
  const result = input?.schema_version === 3
    ? await importImages(db, input, { apply, directory: '/imports/images', store: createImageStore('/data/catalog-images') })
    : await (input?.schema_version === 2 ? importPromotions : importCatalog)(db, input, { apply });
  console.log(JSON.stringify({ input_sha256: digest, ...result }, null, 2));
  if (!result.can_apply) process.exitCode = 2;
} catch (error) {
  const safe = error instanceof ImportError ? { code: error.code, entity: error.entity, index: error.index } : { code: 'LOCAL_IMPORT_FAILED' };
  console.error(JSON.stringify({ error: safe }));
  process.exitCode = 1;
} finally { if (db) await db.end(); }
