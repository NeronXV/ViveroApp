import { readFile, stat, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { prepareSourceCatalog, SourceCatalogError } from './source-catalog.js';
import { ImportError } from './catalog-import.js';
import { SourceExportError } from './source-export-preflight.js';

try {
  const args = process.argv.slice(2);
  if (args.length !== 8 || args[0] !== '--file' || args[2] !== '--source-key'
    || args[4] !== '--expected-sha256' || args[6] !== '--output-dir') throw new SourceCatalogError('USE_FILE_SOURCE_KEY_HASH_OUTPUT_DIR');
  const info = await stat(args[1]);
  if (!info.isFile() || info.size > 500 * 1024 * 1024) throw new SourceCatalogError('EXPORT_SIZE_INVALID');
  const result = prepareSourceCatalog(await readFile(args[1]), { sourceKey: args[3], expectedSha256: args[5] });
  const catalog = JSON.stringify(result.catalog), metadata = JSON.stringify(result.metadata);
  // An existing directory is never overwritten; keep all output outside Git.
  await mkdir(args[7]);
  await writeFile(join(args[7], 'catalog.json'), catalog, { flag: 'wx', mode: 0o600 });
  await writeFile(join(args[7], 'metadata.json'), metadata, { flag: 'wx', mode: 0o600 });
  console.log(JSON.stringify({ prepared: true, import_applied: false,
    input_sha256: result.metadata.input_sha256, catalog_sha256: createHash('sha256').update(catalog).digest('hex'),
    categories: result.catalog.categories.length, products: result.catalog.products.length,
    minimums_preserved: result.metadata.inventory_minimums.length }));
} catch (error) {
  console.error(JSON.stringify({ prepared: false, import_applied: false,
    error: error instanceof SourceCatalogError || error instanceof SourceExportError ? error.message
      : error instanceof ImportError ? error.code : 'CATALOG_PREPARATION_FAILED' }));
  process.exitCode = 1;
}
