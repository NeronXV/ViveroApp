import sharp from 'sharp';
import { randomBytes } from 'node:crypto';
import { access, readFile, writeFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { ApiError } from './catalog.js';

const MAX_BYTES = 5 * 1024 * 1024;
const formats = { 'image/jpeg': 'jpeg', 'image/png': 'png', 'image/webp': 'webp' };
const keyPattern = /^[a-f0-9]{64}\.webp$/;
const notFound = () => { throw new ApiError(404, 'NOT_FOUND'); };
const invalid = () => { throw new ApiError(400, 'INVALID_IMAGE'); };

export async function normalizeImage(input, contentType) {
  const expected = formats[contentType];
  if (!expected) throw new ApiError(415, 'IMAGE_TYPE_REQUIRED');
  if (!input.length || input.length > MAX_BYTES) invalid();
  // Reject non-raster formats before the decoder sees them (including SVG).
  const signature = input.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) ? 'png'
    : input[0] === 255 && input[1] === 216 && input[2] === 255 ? 'jpeg'
      : input.toString('ascii', 0, 4) === 'RIFF' && input.toString('ascii', 8, 12) === 'WEBP' ? 'webp' : null;
  if (signature !== expected) invalid();
  try {
    const image = sharp(input, { failOn: 'warning', limitInputPixels: 16000000 });
    const metadata = await image.metadata();
    if (metadata.format !== expected || (metadata.pages ?? 1) !== 1) invalid();
    // Decode fully, orient and re-encode; original metadata/filenames are not retained.
    const result = await image.rotate().resize({ width: 2048, height: 2048, fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 85 }).toBuffer({ resolveWithObject: true });
    if (result.data.length > MAX_BYTES) invalid();
    return result;
  } catch { invalid(); }
}

export function createImageStore(directory) {
  if (!directory || !isAbsolute(directory)) throw new Error('Configure absolute IMAGE_STORAGE_DIR');
  const path = key => {
    if (!keyPattern.test(key)) throw new Error('Invalid stored image key');
    return join(directory, key);
  };
  return {
    async check() { await access(directory, constants.R_OK | constants.W_OK); },
    async put(data) {
      const key = `${randomBytes(32).toString('hex')}.webp`;
      await writeFile(path(key), data, { flag: 'wx', mode: 0o600 });
      return key;
    },
    async get(key) { return readFile(path(key)); },
  };
}

export function validateImagePatch(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !Object.keys(value).length) invalid();
  const result = {};
  for (const [key, input] of Object.entries(value)) {
    if (key === 'alt_text' && typeof input === 'string' && [...input.trim()].length <= 500 && !/[\u0000-\u001f\u007f]/u.test(input)) result[key] = input.trim();
    else if (key === 'sort_order' && Number.isInteger(input) && input >= 0 && input <= 65535) result[key] = input;
    else if (key === 'is_primary' && input === true) result[key] = true;
    else invalid();
  }
  return result;
}

export async function lockImageProduct(db, productId) {
  const [[row]] = await db.execute('SELECT id FROM products WHERE id = ? FOR UPDATE', [productId]);
  if (!row) notFound();
}

export function createImages(db) {
  return {
    async list(productId, admin = false) {
      const [[product]] = await db.execute(`SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? ${admin ? '' : 'AND p.is_active = 1 AND c.is_active = 1'}`, [productId]);
      if (!product) notFound();
      const [rows] = await db.execute(`SELECT id, alt_text, sort_order, is_primary, width, height, byte_size
        FROM product_images WHERE product_id = ? AND is_active = 1 ORDER BY is_primary DESC, sort_order, id`, [productId]);
      return { items: rows.map(row => ({ ...row, is_primary: Boolean(row.is_primary), url: `/api/v1/images/${row.id}` })) };
    },
    async file(id, admin = false) {
      const [[row]] = await db.execute(`SELECT i.storage_key FROM product_images i
        JOIN products p ON p.id = i.product_id JOIN categories c ON c.id = p.category_id
        WHERE i.id = ? AND i.is_active = 1 ${admin ? '' : 'AND p.is_active = 1 AND c.is_active = 1'}`, [id]);
      if (!row) notFound();
      return row.storage_key;
    },
    async add(productId, store, data, info) {
      await lockImageProduct(db, productId);
      const [[count]] = await db.execute('SELECT COUNT(*) AS total FROM product_images WHERE product_id = ? AND is_active = 1', [productId]);
      if (count.total >= 12) throw new ApiError(409, 'IMAGE_LIMIT_REACHED');
      // Persist only after the limit check, before publishing the SQL reference.
      const key = await store.put(data);
      const [result] = await db.execute(`INSERT INTO product_images
        (product_id, storage_key, width, height, byte_size, is_primary) VALUES (?, ?, ?, ?, ?, ?)`,
      [productId, key, info.width, info.height, info.size, Number(count.total) === 0]);
      await db.execute('UPDATE products SET catalog_revision = catalog_revision WHERE id = ?', [productId]);
      return { id: result.insertId, url: `/api/v1/images/${result.insertId}` };
    },
    async update(productId, id, data, remove = false) {
      await lockImageProduct(db, productId);
      const [[current]] = await db.execute('SELECT is_primary, is_active FROM product_images WHERE product_id = ? AND id = ? FOR UPDATE', [productId, id]);
      if (!current || (!remove && !current.is_active)) notFound();
      if (remove && !current.is_active) return { id };
      if (data.is_primary) await db.execute('UPDATE product_images SET is_primary = 0 WHERE product_id = ? AND is_primary = 1', [productId]);
      const values = remove ? { is_active: false, is_primary: false } : data;
      await db.execute(`UPDATE product_images SET ${Object.keys(values).map(key => `${key} = ?`).join(', ')} WHERE id = ?`, [...Object.values(values), id]);
      if (remove && current.is_primary) {
        await db.execute(`UPDATE product_images SET is_primary = 1 WHERE product_id = ? AND is_active = 1 ORDER BY sort_order, id LIMIT 1`, [productId]);
      }
      await db.execute('UPDATE products SET catalog_revision = catalog_revision WHERE id = ?', [productId]);
      return { id };
    },
  };
}

// Two buffered uploads/decoders per API process; reject rather than build a queue.
export function createImageUploader() {
  let active = 0;
  return async request => {
    if (active >= 2) throw new ApiError(429, 'IMAGE_UPLOAD_BUSY');
    const type = request.headers['content-type']?.split(';')[0].trim().toLowerCase();
    if (!Object.hasOwn(formats, type ?? '')) throw new ApiError(415, 'IMAGE_TYPE_REQUIRED');
    if (request.headers['content-encoding']) throw new ApiError(415, 'IMAGE_ENCODING_UNSUPPORTED');
    active++;
    try {
      let size = 0;
      const chunks = [];
      for await (const chunk of request.iterator({ destroyOnReturn: false })) {
        size += chunk.length;
        if (size > MAX_BYTES) {
          request.resume();
          throw new ApiError(413, 'BODY_TOO_LARGE');
        }
        chunks.push(chunk);
      }
      return await normalizeImage(Buffer.concat(chunks), type);
    } finally { active--; }
  };
}
