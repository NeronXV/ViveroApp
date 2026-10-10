import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { preparePlantLists } from './plant-list-preparation.js';

// Offline only. No credentials, database connection or implicit application.
try {
  const [inputPath, snapshotPath, output] = process.argv.slice(2);
  if (!inputPath || !snapshotPath || !output) throw new Error('Expected input, read-only snapshot and output directory');
  const result = preparePlantLists(JSON.parse(await readFile(inputPath, 'utf8')), JSON.parse(await readFile(snapshotPath, 'utf8')));
  await mkdir(output, { recursive: true });
  await writeFile(join(output, 'catalog.json'), JSON.stringify(result.catalog, null, 2), { flag: 'wx' });
  await writeFile(join(output, 'review.json'), JSON.stringify({ report: result.report, can_apply: result.can_apply, note: result.note }, null, 2), { flag: 'wx' });
  console.log(JSON.stringify({ prepared: true, imported: false, can_apply: result.can_apply, products: result.catalog.products.length }));
} catch {
  console.error('Plant-list preparation failed. No database was accessed and existing output files were not overwritten.');
  process.exitCode = 1;
}
