import { open } from 'node:fs/promises';
import { inspectSourceExport, SourceExportError } from './source-export-preflight.js';

let handle;
try {
  if (process.argv.length !== 4 || process.argv[2] !== '--file') throw new SourceExportError('EXPORT_ARGUMENTS_INVALID');
  handle = await open(process.argv[3], 'r');
  const stat = await handle.stat();
  if (!stat.isFile() || stat.size === 0 || stat.size > 500 * 1024 * 1024) throw new SourceExportError('EXPORT_SIZE_INVALID');
  console.log(JSON.stringify(inspectSourceExport(await handle.readFile())));
} catch (error) {
  // Never print rows, paths, customer names or native exception contents.
  console.error(JSON.stringify({ error: error instanceof SourceExportError ? error.code : 'EXPORT_READ_FAILED', import_applied: false }));
  process.exitCode = 1;
} finally { await handle?.close(); }
