import { createHash } from 'node:crypto';
import { open } from 'node:fs/promises';
import { constants } from 'node:fs';
import { join } from 'node:path';
import { normalizeImage, validateImagePatch } from '../src/images.js';
import { exactKeys, uuid, hash, canonical, fail, runImportTransaction } from './catalog-import.js';

const digest = bytes => createHash('sha256').update(bytes).digest();

export function validateImageManifest(input) {
  exactKeys(input, ['schema_version', 'source_key', 'images']);
  if (input.schema_version !== 3 || typeof input.source_key !== 'string' || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(input.source_key)) fail('INVALID_ENVELOPE');
  if (!Array.isArray(input.images) || !input.images.length || input.images.length > 24) fail('BATCH_LIMIT');
  const ids = new Set();
  const groups = new Map();
  const images = input.images.map((row, index) => {
    exactKeys(row, ['id', 'product_id', 'file', 'sha256', 'content_type', 'alt_text', 'sort_order', 'is_primary']);
    const sourceId = uuid(row.id);
    const productId = uuid(row.product_id);
    if (ids.has(sourceId)) fail('DUPLICATE_SOURCE_ID', 'images', index);
    ids.add(sourceId);
    if (typeof row.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(row.sha256)
      || typeof row.file !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,100}\.(png|jpg|jpeg|webp)$/.test(row.file)
      || !['image/png', 'image/jpeg', 'image/webp'].includes(row.content_type)) fail('INVALID_FILE', 'images', index);
    if (typeof row.is_primary !== 'boolean' || typeof row.alt_text !== 'string' || !row.alt_text.isWellFormed()) fail('INVALID_RECORD', 'images', index);
    const data = validateImagePatch({ alt_text: row.alt_text, sort_order: row.sort_order });
    if (data.alt_text !== row.alt_text) fail('NORMALIZATION_REQUIRED', 'images', index);
    data.is_primary = row.is_primary;
    const group = groups.get(productId) ?? [];
    group.push(row);
    groups.set(productId, group);
    return { sourceId, productId, data, file: row.file, sha256: row.sha256, contentType: row.content_type,
      sourceHash: hash({ ...row, id: sourceId, product_id: productId }) };
  });
  for (const group of groups.values()) {
    if (group.length > 12 || group.filter(row => row.is_primary).length !== 1) fail('PRODUCT_IMAGE_SET_REQUIRED');
    if (new Set(group.map(row => row.sort_order)).size !== group.length) fail('AMBIGUOUS_IMAGE_ORDER');
  }
  return { sourceKey: input.source_key, images };
}

// Flat names and O_NOFOLLOW prevent traversal and symlinks on the Linux tools container.
export async function readImportImage(directory, name) {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,100}\.(png|jpg|jpeg|webp)$/.test(name)) fail('INVALID_FILE');
  const file = await open(join(directory, name), constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0));
  try {
    const stat = await file.stat();
    if (!stat.isFile() || stat.size > 5242880) fail('FILE_LIMIT');
    const buffer = Buffer.alloc(5242881);
    let size = 0;
    while (size < buffer.length) {
      const { bytesRead } = await file.read(buffer, size, buffer.length - size, null);
      if (!bytesRead) break;
      size += bytesRead;
    }
    if (size > 5242880) fail('FILE_LIMIT');
    return buffer.subarray(0, size);
  } finally { await file.close(); }
}

export async function importImages(db, input, { apply = false, directory, store } = {}) {
  const snapshot = validateImageManifest(input);
  // All source files are read, hashed and decoded before any SQL or storage writes.
  for (const [index, row] of snapshot.images.entries()) {
    let bytes;
    try { bytes = await readImportImage(directory, row.file); }
    catch { fail('SOURCE_FILE_UNREADABLE', 'images', index); }
    if (digest(bytes).toString('hex') !== row.sha256) fail('SOURCE_FILE_HASH_MISMATCH', 'images', index);
    try { row.normalized = await normalizeImage(bytes, row.contentType); }
    catch { fail('INVALID_IMAGE', 'images', index); }
  }
  return runImportTransaction(db, apply, '008_image_imports', async () => {
    const report = { mode: apply ? 'apply' : 'dry-run', can_apply: true, items: [] };
    const lock = apply ? ' FOR UPDATE' : '';
    const groups = new Map();
    for (const sourceId of [...new Set(snapshot.images.map(row => row.productId))].sort()) {
      const [[product]] = await db.execute(`SELECT p.id FROM catalog_product_sources m JOIN products p ON p.id = m.product_id
        WHERE m.source_key = ? AND m.source_id = ?${lock}`, [snapshot.sourceKey, sourceId]);
      const [existing] = product ? await db.execute(`SELECT id FROM product_images WHERE product_id = ? AND is_active = 1${lock}`, [product.id]) : [[]];
      groups.set(sourceId, { id: product?.id, existing, mapped: [] });
    }
    for (const [index, row] of snapshot.images.entries()) {
      const group = groups.get(row.productId);
      const [[mapping]] = await db.execute(`SELECT image_id, source_hash, stored_hash FROM catalog_image_sources
        WHERE source_key = ? AND source_id = ?${lock}`, [snapshot.sourceKey, row.sourceId]);
      const item = { entity: 'images', index, action: mapping ? 'reuse' : 'create', target_id: mapping?.image_id ?? null };
      const conflict = code => { item.action = 'conflict'; item.code = code; report.can_apply = false; };
      if (!group.id) conflict('PRODUCT_MAPPING_REQUIRED');
      if (mapping) {
        group.mapped.push(mapping.image_id);
        if (!mapping.source_hash.equals(row.sourceHash)) conflict('SOURCE_CHANGED');
        const [[stored]] = await db.execute(`SELECT product_id, alt_text, sort_order, is_primary, is_active, storage_key, width, height, byte_size
          FROM product_images WHERE id = ?${lock}`, [mapping.image_id]);
        if (!stored) conflict('MISSING_TARGET');
        else {
          const expected = { ...row.data, product_id: group.id, is_active: true };
          const actual = { product_id: stored.product_id, alt_text: stored.alt_text, sort_order: stored.sort_order,
            is_primary: Boolean(stored.is_primary), is_active: Boolean(stored.is_active) };
          if (canonical(actual) !== canonical(expected)) conflict('TARGET_CHANGED');
          try {
            const bytes = await store.get(stored.storage_key);
            if (!digest(bytes).equals(mapping.stored_hash) || bytes.length !== Number(stored.byte_size)) conflict('TARGET_FILE_CHANGED');
            const info = row.normalized.info;
            if (stored.width !== info.width || stored.height !== info.height) conflict('TARGET_CHANGED');
          } catch { conflict('TARGET_FILE_UNREADABLE'); }
        }
      }
      report.items.push(item);
    }
    // A batch contains the complete active set of every included product. Never
    // replace an API upload or implicitly retire an image omitted from the manifest.
    for (const [sourceId, group] of groups) {
      if (group.existing.some(image => !group.mapped.includes(image.id))) {
        report.can_apply = false;
        for (const item of report.items.filter(item => snapshot.images[item.index].productId === sourceId)) {
          item.action = 'conflict'; item.code = 'UNLISTED_ACTIVE_IMAGE';
        }
      }
    }
    if (!apply || !report.can_apply) return report;
    for (const item of report.items) {
      if (item.action !== 'create') continue;
      const row = snapshot.images[item.index];
      const { data, info } = row.normalized;
      const key = await store.put(data);
      const [created] = await db.execute(`INSERT INTO product_images
        (product_id, storage_key, alt_text, sort_order, is_primary, width, height, byte_size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [groups.get(row.productId).id, key, row.data.alt_text, row.data.sort_order, row.data.is_primary, info.width, info.height, data.length]);
      item.target_id = created.insertId;
      await db.execute(`INSERT INTO catalog_image_sources (source_key, source_id, image_id, source_hash, stored_hash) VALUES (?, ?, ?, ?, ?)`,
        [snapshot.sourceKey, row.sourceId, item.target_id, row.sourceHash, digest(data)]);
    }
    return report;
  });
}
