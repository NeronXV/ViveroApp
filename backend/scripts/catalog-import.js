import { createHash } from 'node:crypto';
import { validateCategory, validateProduct } from '../src/catalog.js';

export class ImportError extends Error {
  constructor(code, entity, index) {
    super(code);
    this.code = code;
    this.entity = entity;
    this.index = index;
  }
}
export const fail = (code, entity, index) => { throw new ImportError(code, entity, index); };
export const canonical = value => JSON.stringify(Object.fromEntries(Object.entries(value).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)));
export const hash = value => createHash('sha256').update(canonical(value)).digest();
export const uuid = value => {
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) fail('INVALID_SOURCE_ID');
  return value.toLowerCase();
};
const categoryFields = ['id', 'name', 'description', 'is_active'];
const productFields = ['id', 'category_id', 'internal_code', 'barcode', 'common_name', 'scientific_name', 'description', 'price_cents', 'wholesale_price_cents', 'unit', 'watering_advice', 'light_type', 'recommended_climate', 'is_active'];
export function exactKeys(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== keys.length || keys.some(key => !Object.hasOwn(value, key))) fail('INVALID_FIELDS');
}

export function validateSnapshot(input) {
  exactKeys(input, ['schema_version', 'source_key', 'categories', 'products']);
  if (input.schema_version !== 1 || typeof input.source_key !== 'string' || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(input.source_key)) fail('INVALID_ENVELOPE');
  if (!Array.isArray(input.categories) || !Array.isArray(input.products) || input.categories.length > 500 || input.products.length > 1000) fail('BATCH_LIMIT');
  if (!input.categories.length && !input.products.length) fail('EMPTY_BATCH');
  const result = { sourceKey: input.source_key, categories: [], products: [] };
  for (const entity of ['categories', 'products']) {
    const ids = new Set();
    for (const [index, row] of input[entity].entries()) {
      try {
        exactKeys(row, entity === 'categories' ? categoryFields : productFields);
        const sourceId = uuid(row.id);
        if (ids.has(sourceId)) fail('DUPLICATE_SOURCE_ID');
        ids.add(sourceId);
        const { id: _id, category_id: sourceCategory, ...raw } = row;
        if (Object.values(raw).some(value => typeof value === 'string' && !value.isWellFormed())) fail('INVALID_UNICODE');
        // Supabase categories allow null descriptions; target uses empty text.
        if (entity === 'categories' && raw.description === null) raw.description = '';
        const data = entity === 'categories' ? validateCategory(raw, true) : validateProduct({ ...raw, category_id: 1 });
        delete data.category_id;
        if (canonical(raw) !== canonical(data)) fail('NORMALIZATION_REQUIRED');
        const categorySourceId = entity === 'products' ? uuid(sourceCategory) : undefined;
        const sourceHash = hash(categorySourceId ? { ...data, category_id: categorySourceId } : data);
        result[entity].push({ sourceId, categorySourceId, sourceHash, data });
      } catch (error) {
        fail(error instanceof ImportError ? error.code : 'INVALID_RECORD', entity, index);
      }
    }
  }
  const categoryIds = new Set(result.categories.map(row => row.sourceId));
  for (const [index, row] of result.products.entries()) {
    if (!categoryIds.has(row.categorySourceId)) fail('CATEGORY_NOT_IN_BATCH', 'products', index);
  }
  return result;
}

const definitions = {
  categories: { map: 'catalog_category_sources', target: 'category_id', unique: ['name'] },
  products: { map: 'catalog_product_sources', target: 'product_id', unique: ['internal_code', 'barcode'] },
};

async function duplicateFields(db, rows, fields) {
  const collisions = new Map();
  for (const field of fields) {
    // Let MariaDB apply the same accent/case-insensitive collation as its UNIQUE keys.
    const values = JSON.stringify(rows.map(row => row.data[field]));
    const [duplicates] = await db.execute(`SELECT MIN(t.position) - 1 AS first_index FROM
      JSON_TABLE(?, '$[*]' COLUMNS (position FOR ORDINALITY, value VARCHAR(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$')) t
      WHERE t.value IS NOT NULL GROUP BY t.value HAVING COUNT(*) > 1`, [values]);
    for (const row of duplicates) collisions.set(Number(row.first_index), 'DUPLICATE_BUSINESS_KEY_IN_BATCH');
  }
  return collisions;
}

async function plan(db, snapshot, apply) {
  const report = { mode: apply ? 'apply' : 'dry-run', can_apply: true, items: [] };
  const categoryTargets = new Map();
  const lock = apply ? ' FOR UPDATE' : '';
  for (const entity of ['categories', 'products']) {
    const definition = definitions[entity];
    const duplicates = await duplicateFields(db, snapshot[entity], definition.unique);
    for (const [index, row] of snapshot[entity].entries()) {
      const [[mapping]] = await db.execute(`SELECT ${definition.target} AS target_id, source_hash FROM ${definition.map} WHERE source_key = ? AND source_id = ?${lock}`, [snapshot.sourceKey, row.sourceId]);
      const item = { entity, index, action: mapping ? 'reuse' : 'create', target_id: mapping?.target_id ?? null };
      const conflict = code => { item.action = 'conflict'; item.code = code; report.can_apply = false; };
      if (duplicates.has(index)) conflict(duplicates.get(index));
      const expected = entity === 'products' ? { ...row.data, category_id: categoryTargets.get(row.categorySourceId) ?? null } : row.data;
      if (mapping) {
        if (!mapping.source_hash.equals(row.sourceHash)) conflict('SOURCE_CHANGED');
        const [[stored]] = await db.execute(`SELECT ${Object.keys(expected).join(',')} FROM ${entity} WHERE id = ?${lock}`, [mapping.target_id]);
        if (!stored) conflict('MISSING_TARGET');
        else {
          stored.is_active = Boolean(stored.is_active);
          for (const key of ['price_cents', 'wholesale_price_cents']) if (Object.hasOwn(stored, key) && stored[key] !== null) stored[key] = Number(stored[key]);
          if (canonical(stored) !== canonical(expected)) conflict(item.code ?? 'TARGET_CHANGED');
        }
      } else {
        for (const key of definition.unique) {
          if (row.data[key] === null) continue;
          const [existing] = await db.execute(`SELECT id FROM ${entity} WHERE ${key} = ?${lock}`, [row.data[key]]);
          if (existing.length) conflict('UNMAPPED_BUSINESS_KEY_COLLISION');
        }
      }
      if (entity === 'categories') categoryTargets.set(row.sourceId, mapping?.target_id ?? null);
      report.items.push(item);
    }
  }
  return report;
}

// Dedicated maintenance connection, never a pooled HTTP connection.
export async function importCatalog(db, input, { apply = false } = {}) {
  const snapshot = validateSnapshot(input);
  return runImportTransaction(db, apply, '006_catalog_imports', async () => {
    const report = await plan(db, snapshot, apply);
    if (!apply || !report.can_apply) return report;
    const categoryTargets = new Map();
    for (const item of report.items) {
      const row = snapshot[item.entity][item.index];
      const definition = definitions[item.entity];
      if (item.action === 'create') {
        const data = item.entity === 'products' ? { ...row.data, category_id: categoryTargets.get(row.categorySourceId) } : row.data;
        const keys = Object.keys(data);
        const [created] = await db.execute(`INSERT INTO ${item.entity} (${keys.join(',')}) VALUES (${keys.map(() => '?').join(',')})`, Object.values(data));
        item.target_id = created.insertId;
        await db.execute(`INSERT INTO ${definition.map} (source_key, source_id, ${definition.target}, source_hash) VALUES (?, ?, ?, ?)`, [snapshot.sourceKey, row.sourceId, item.target_id, row.sourceHash]);
      }
      if (item.entity === 'categories') categoryTargets.set(row.sourceId, item.target_id);
    }
    return report;
  });
}

// Both formats share the same local lock, read-only preview and commit handling.
export async function runImportTransaction(db, apply, migration, action) {
  let locked = false;
  let transaction = false;
  let committing = false;
  try {
    if (apply) {
      const [[row]] = await db.execute("SELECT GET_LOCK('vivero_catalog_import', 10) AS acquired");
      if (row.acquired !== 1) fail('IMPORT_BUSY');
      locked = true;
    }
    await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
    await db.query(apply ? 'START TRANSACTION' : 'START TRANSACTION READ ONLY, WITH CONSISTENT SNAPSHOT');
    transaction = true;
    const [migrations] = await db.execute('SELECT version FROM schema_migrations WHERE version = ?', [migration]);
    if (!migrations.length) fail('MIGRATION_REQUIRED');
    const report = await action();
    if (!apply || !report.can_apply) {
      await db.rollback();
      transaction = false;
      return report;
    }
    committing = true;
    await db.commit();
    transaction = false;
    report.committed = true;
    return report;
  } catch (error) {
    if (transaction) await db.rollback().catch(() => {});
    if (committing) fail('COMMIT_UNCERTAIN_RECHECK_SAME_FILE');
    if (error instanceof ImportError) throw error;
    fail('IMPORT_FAILED_NO_PARTIAL_COMMIT');
  } finally {
    if (locked) await db.execute("SELECT RELEASE_LOCK('vivero_catalog_import')").catch(() => {});
  }
}
