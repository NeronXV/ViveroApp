import { createHash } from 'node:crypto';
import { validateSnapshot, ImportError } from './catalog-import.js';

const categories = ['Follaje', 'Arbustos y setos', 'Floral', 'Arbolado'];
const digest = value => createHash('sha256').update(value).digest('hex');
const uuid = value => {
  const h = digest(value);
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
};
const normalize = value => String(value ?? '').normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase().replace(/\s+/g, ' ');
function text(value, max, optional = false) {
  if (optional && (value === undefined || value === null)) return null;
  if (typeof value !== 'string' || !value.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(value)
    || !value.trim() || [...value].length > max) throw new Error('PLANT_LIST_INVALID');
  return value;
}

// Pure preparation only. The caller supplies a read-only product/category
// snapshot; no database connection, stock values or confirmed prices here.
export function preparePlantLists(input, existing) {
  if (!input || input.schema_version !== 1 || !Array.isArray(input.items) || !input.items.length
    || !existing || !Array.isArray(existing.products) || !Array.isArray(existing.categories)) throw new Error('PLANT_LIST_INVALID');
  const source = text(input.source_key, 64);
  if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(source)) throw new Error('PLANT_LIST_INVALID');
  const codes = new Set(existing.products.map(p => normalize(p.internal_code)));
  const barcodes = new Set(existing.products.filter(p => p.barcode).map(p => normalize(p.barcode)));
  const ids = new Set(), names = new Set();
  const report = [];
  const catalog = { schema_version: 1, source_key: source, categories: [], products: [] };
  const categoryIds = new Map();
  for (const name of categories) {
    const matches = existing.categories.filter(c => normalize(c.name) === normalize(name));
    const sourceId = uuid(`${source}:category:${name}`);
    categoryIds.set(name, sourceId);
    // Existing categories require explicit source mapping before the canonical
    // importer can reuse them. Do not silently create near-duplicate categories.
    catalog.categories.push({ id: sourceId, name, description: '', is_active: true });
    if (matches.length) report.push({ type: 'CATEGORY_REVIEW', name, candidates: matches.map(c => c.id) });
  }
  for (const item of input.items) {
    if (!item || Object.keys(item).some(k => !['entry_id', 'original_name', 'approved_name', 'category', 'scientific_name', 'presentation', 'size', 'internal_code', 'barcode'].includes(k))) throw new Error('PLANT_LIST_INVALID');
    const entryId = text(item.entry_id, 80), name = text(item.original_name, 160);
    if (!categories.includes(item.category) || ids.has(entryId)) throw new Error('PLANT_LIST_DUPLICATE_ENTRY');
    ids.add(entryId);
    const scientific = text(item.scientific_name, 160, true);
    const approvedName = text(item.approved_name, 160, true);
    const presentation = text(item.presentation, 120, true), size = text(item.size, 80, true);
    const code = item.internal_code == null ? `VDP-${digest(`${source}:${entryId}`).slice(0, 12).toUpperCase()}` : text(item.internal_code, 40);
    const barcode = text(item.barcode, 128, true);
    const candidates = existing.products.filter(p => normalize(p.common_name) === normalize(name)
      || (scientific && normalize(p.scientific_name) === normalize(scientific))
      || normalize(p.internal_code) === normalize(code) || (barcode && normalize(p.barcode) === normalize(barcode)));
    const identity = [normalize(name), normalize(scientific), normalize(presentation), normalize(size)].join('|');
    const conflicts = [];
    if (codes.has(normalize(code))) conflicts.push('CODE_COLLISION');
    if (barcode && barcodes.has(normalize(barcode))) conflicts.push('BARCODE_COLLISION');
    if (barcodes.has(normalize(code)) || (barcode && codes.has(normalize(barcode))) || (barcode && normalize(barcode) === normalize(code))) conflicts.push('SCAN_CODE_COLLISION');
    if (names.has(identity)) conflicts.push('DUPLICATE_NAME_PRESENTATION');
    if (candidates.length) conflicts.push('EXISTING_PRODUCT_REVIEW');
    codes.add(normalize(code)); if (barcode) barcodes.add(normalize(barcode)); names.add(identity);
    report.push({ type: 'PRODUCT', entry_id: entryId, original_name: name, proposed_normalized_name: name.trim().replace(/\s+/g, ' '),
      presentation, size, proposed_code: code, candidates: candidates.map(p => p.id), conflicts });
    catalog.products.push({ id: uuid(`${source}:product:${entryId}`), category_id: categoryIds.get(item.category),
      internal_code: code, barcode, common_name: approvedName ?? name, scientific_name: scientific,
      description: [presentation, size].filter(Boolean).join(' · '), price_cents: 0, wholesale_price_cents: null,
      unit: 'pieza', watering_advice: '', light_type: '', recommended_climate: '', is_active: false });
  }
  try { validateSnapshot(catalog) }
  catch (error) {
    if (!(error instanceof ImportError) || error.code !== 'NORMALIZATION_REQUIRED') throw error;
    // Keep the original for review. The canonical importer will also refuse to
    // apply this preview until normalization has been explicitly approved.
    report.filter(r => r.type === 'PRODUCT')[error.index].conflicts.push('NORMALIZATION_REVIEW_REQUIRED');
  }
  return { catalog, report, can_apply: report.every(r => r.type !== 'CATEGORY_REVIEW' && !r.conflicts?.length),
    note: 'Zero price is an unconfirmed schema placeholder; no quantities or images are supplied. Run the canonical importer dry-run against the target before assigning/applying codes.' };
}
