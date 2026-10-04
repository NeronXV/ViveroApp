import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, writeFile, symlink, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateImageManifest, readImportImage } from '../scripts/image-import.js';

const fixture = () => readFile(new URL('./fixtures/image-import-demo.json', import.meta.url), 'utf8').then(JSON.parse);

test('image manifests require complete primary sets, stable IDs, hashes and safe flat filenames', async () => {
  const input = await fixture();
  assert.equal(validateImageManifest(input).images.length, 1);
  for (const file of ['../photo.png', '/photo.png', 'https://host/photo.png', 'a\\b.png', '.photo.png']) {
    const copy = structuredClone(input); copy.images[0].file = file;
    assert.throws(() => validateImageManifest(copy), { code: 'INVALID_FILE' });
  }
  const duplicate = structuredClone(input); duplicate.images.push(duplicate.images[0]);
  assert.throws(() => validateImageManifest(duplicate), { code: 'DUPLICATE_SOURCE_ID' });
  const missingPrimary = structuredClone(input); missingPrimary.images[0].is_primary = false;
  assert.throws(() => validateImageManifest(missingPrimary), { code: 'PRODUCT_IMAGE_SET_REQUIRED' });
  const tied = structuredClone(input);
  tied.images.push({ ...tied.images[0], id: '40000000-0000-4000-8000-000000000002', is_primary: false });
  assert.throws(() => validateImageManifest(tied), { code: 'AMBIGUOUS_IMAGE_ORDER' });
  const unknown = structuredClone(input); unknown.images[0].url = 'https://example.invalid';
  assert.throws(() => validateImageManifest(unknown), { code: 'INVALID_FIELDS' });
  const hash = structuredClone(input); hash.images[0].sha256 = 'bad';
  assert.throws(() => validateImageManifest(hash), { code: 'INVALID_FILE' });
});

test('file reader bounds input and rejects traversal and symlinks on Linux', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'image-import-'));
  try {
    await writeFile(join(dir, 'large.png'), Buffer.alloc(5242881));
    await assert.rejects(readImportImage(dir, 'large.png'), { code: 'FILE_LIMIT' });
    await assert.rejects(readImportImage(dir, '../large.png'), { code: 'INVALID_FILE' });
    if (process.platform !== 'win32') {
      await symlink(join(dir, 'large.png'), join(dir, 'link.png'));
      await assert.rejects(readImportImage(dir, 'link.png'), { code: 'ELOOP' });
    }
  } finally { await rm(dir, { recursive: true, force: true }); }
});
