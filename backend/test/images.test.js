import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { normalizeImage, createImageStore, validateImagePatch, createImageUploader } from '../src/images.js';

test('images fully decode, resize and strip metadata; spoofed and broken inputs fail', async () => {
  const image = sharp({ create: { width: 2300, height: 30, channels: 3, background: 'green' } });
  for (const format of ['png', 'jpeg', 'webp']) {
    const input = await image.clone().toFormat(format).withMetadata().toBuffer();
    const result = await normalizeImage(input, `image/${format}`);
    const metadata = await sharp(result.data).metadata();
    assert.equal(metadata.format, 'webp');
    assert.equal(metadata.width, 2048);
    assert.equal(metadata.exif, undefined);
    assert.equal(metadata.icc, undefined);
  }
  await assert.rejects(normalizeImage(Buffer.from('<svg/>'), 'image/png'), { status: 400 });
  await assert.rejects(normalizeImage(Buffer.from('<svg/>'), 'image/svg+xml'), { status: 415 });
  const png = await image.png().toBuffer();
  await assert.rejects(normalizeImage(png, 'image/jpeg'), { status: 400 });
  await assert.rejects(normalizeImage(png.subarray(0, 45), 'image/png'), { status: 400 });
  const huge = await sharp({ create: { width: 4001, height: 4000, channels: 3, background: 'green' } }).png().toBuffer();
  await assert.rejects(normalizeImage(huge, 'image/png'), { status: 400 });
  const animated = Buffer.from([255, 0, 0, 0, 255, 0]);
  const animation = await sharp(animated, { raw: { width: 1, height: 2, channels: 3, pageHeight: 1 } }).webp({ loop: 0, delay: [100, 100] }).toBuffer();
  assert.equal((await sharp(animation).metadata()).pages, 2);
  await assert.rejects(normalizeImage(animation, 'image/webp'), { status: 400 });
});

test('storage keys cannot escape directory and bytes survive a new store instance', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'vivero-images-'));
  try {
    const store = createImageStore(directory);
    await store.check();
    const key = await store.put(Buffer.from('synthetic'));
    assert.match(key, /^[a-f0-9]{64}\.webp$/);
    assert.equal((await createImageStore(directory).get(key)).toString(), 'synthetic');
    for (const key of ['../secret', '/etc/passwd', '..\\secret', 'photo.webp']) await assert.rejects(store.get(key));
  } finally { await rm(directory, { recursive: true }); }
});

test('image metadata rejects arbitrary paths and invalid states', () => {
  assert.deepEqual(validateImagePatch({ alt_text: ' Planta ', is_primary: true, sort_order: 3 }), { alt_text: 'Planta', is_primary: true, sort_order: 3 });
  for (const input of [{}, null, { storage_key: 'x' }, { is_primary: false }, { is_active: true }, { sort_order: 0.5 }, { alt_text: 'x'.repeat(501) }]) assert.throws(() => validateImagePatch(input));
});

test('upload byte limit applies to chunked bodies before storage', async () => {
  const upload = createImageUploader();
  const request = { headers: { 'content-type': 'image/png' }, resume() {}, async *iterator() { yield Buffer.alloc(5 * 1024 * 1024); yield Buffer.alloc(1); } };
  await assert.rejects(upload(request), { status: 413 });
});
