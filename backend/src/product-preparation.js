import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

// Only internal SQL aliases are accepted. All external values are parameters.
export function sellableProductSql(branchSql = null, alias = 'p') {
  if (!['p', 'product'].includes(alias)) throw new Error('Invalid internal product alias');
  const branch = branchSql === null ? '' : `AND ready.branch_id = ${branchSql}`;
  return `${alias}.price_cents > 0 AND (${alias}.preparation_mode = 'LEGACY' OR (
    ${alias}.price_confirmed_at IS NOT NULL AND EXISTS (
      SELECT 1 FROM product_branch_preparation ready JOIN branches rb ON rb.id = ready.branch_id
      WHERE ready.product_id = ${alias}.id AND rb.is_active = 1 ${branch}
        AND ready.initial_count_confirmed_at IS NOT NULL AND ready.activated_at IS NOT NULL)))`;
}

export function createProductPreparation(db, context) {
  requireCapabilities(context, ['MANAGE_PRODUCTS'], context.branch?.id);
  if (!context.branch?.is_active) throw new ApiError(403, 'BRANCH_FORBIDDEN');
  const branchId = context.branch.id;
  async function state(id) {
    const [[p]] = await db.execute(`SELECT p.id, p.preparation_mode, p.is_active, p.price_cents, p.price_confirmed_at, p.commercial_disabled_at,
      EXISTS (SELECT 1 FROM catalog_product_sources src WHERE src.product_id = p.id) AS imported,
      c.is_active AS category_active, r.initial_count_confirmed_at, r.activated_at
      FROM products p JOIN categories c ON c.id = p.category_id
      LEFT JOIN product_branch_preparation r ON r.product_id = p.id AND r.branch_id = ? WHERE p.id = ?`, [branchId, id]);
    if (!p) throw new ApiError(404, 'NOT_FOUND');
    const legacy = p.preparation_mode === 'LEGACY';
    const price = Number(p.price_cents) > 0 && (legacy || p.price_confirmed_at !== null);
    const count = legacy || p.initial_count_confirmed_at !== null;
    const activation = legacy || p.activated_at !== null;
    const disabled = !p.is_active && (legacy || p.commercial_disabled_at !== null);
    const reasons = [disabled && 'DISABLED', !p.is_active && !disabled && 'PRODUCT_PENDING', !p.category_active && 'CATEGORY_INACTIVE',
      !price && 'PRICE_PENDING', !count && 'INITIAL_COUNT_PENDING', !activation && 'ACTIVATION_PENDING'].filter(Boolean);
    return { schema_version: 1, product_id: id, branch_id: branchId, preparation_mode: p.preparation_mode,
      commercial_state: disabled ? 'DISABLED' : reasons.length === 0 ? 'READY' : p.imported ? 'IMPORTED_PENDING' : 'PENDING',
      legacy_compatibility: legacy,
      price_confirmed: price, initial_count_confirmed: count, activated: activation,
      ready: reasons.length === 0, reasons };
  }
  return {
    state,
    async activate(id) {
      requireCapabilities(context, ['MANAGE_PRICES']);
      // Same branch -> product order used by quotes, counts and inventory.
      await db.execute('SELECT id FROM branches WHERE id = ? FOR UPDATE', [branchId]);
      await db.execute('SELECT id FROM products WHERE id = ? FOR UPDATE', [id]);
      const before = await state(id);
      if (before.reasons.some(reason => !['ACTIVATION_PENDING', 'PRODUCT_PENDING'].includes(reason))) throw new ApiError(409, 'PRODUCT_PREPARATION_INCOMPLETE');
      if (before.preparation_mode === 'PENDING') await db.execute('UPDATE products SET is_active = 1 WHERE id = ?', [id]);
      if (!before.activated) await db.execute(`UPDATE product_branch_preparation SET activated_at = UTC_TIMESTAMP(6), activated_by = ?
        WHERE branch_id = ? AND product_id = ? AND activated_at IS NULL`, [context.user.id, branchId, id]);
      return { ...await state(id), idempotent_replay: before.activated };
    },
  };
}
