import { createHash } from 'node:crypto';
import { ApiError } from './catalog.js';
import { lockInventoryRow } from './inventory.js';
import { sellableProductSql } from './product-preparation.js';

function milli(value) {
  const text = String(value);
  if (!/^-?\d+(?:\.\d{1,3})?$/.test(text)) throw new ApiError(409, 'INVENTORY_QUANTITY_INVALID');
  const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
  return (text.startsWith('-') ? -1n : 1n) * (BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0')));
}
const digest = value => createHash('sha256').update(value).digest();
// Caller already holds the sale lock, inside the payment/refund transaction.
export async function deductSaleStock(db, sale, actor) {
  const [[branch]] = await db.execute('SELECT inventory_enabled FROM branches WHERE id = ? FOR UPDATE', [sale.branch_id]);
  const [items] = await db.execute('SELECT product_id, SUM(quantity) AS quantity FROM sale_items WHERE sale_id = ? GROUP BY product_id ORDER BY product_id', [sale.id]);
  if (!items.length) throw new ApiError(409, 'SALE_TOTAL_INVALID');
  for (const item of items) {
    // Legacy tickets retain their captured terms. Newly prepared products must
    // still be enabled for this branch even if stock enforcement is disabled.
    const [[product]] = await db.execute(`SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id
      WHERE p.id = ? AND (p.preparation_mode = 'LEGACY' OR (p.is_active = 1 AND c.is_active = 1 AND ${sellableProductSql('?')}))
      LOCK IN SHARE MODE`, [item.product_id, sale.branch_id]);
    if (!product) throw new ApiError(409, 'PRODUCT_PREPARATION_INCOMPLETE');
    if (!branch.inventory_enabled) continue;
    const balance = await lockInventoryRow(db, sale.branch_id, item.product_id);
    if (milli(balance.quantity) < milli(item.quantity)) throw new ApiError(409, 'INVENTORY_INSUFFICIENT');
    await db.execute(`INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, sale_id, idempotency_hash, created_by, notes)
      VALUES (?, ?, 'SALE', ?, ?, ?, ?, ?)`, [sale.branch_id, item.product_id, `-${item.quantity}`, sale.id, digest(`stock-sale:${sale.id}:${item.product_id}`), actor, `Venta ${sale.folio}`]);
  }
}
export async function restockRefund(db, refund, actor) {
  const [movements] = await db.execute("SELECT product_id, -quantity AS quantity FROM inventory_movements WHERE sale_id = ? AND movement_type = 'SALE' ORDER BY product_id", [refund.sale_id]);
  for (const movement of movements) {
    await lockInventoryRow(db, refund.branch_id, movement.product_id);
    await db.execute(`INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, refund_id, idempotency_hash, created_by, notes)
      VALUES (?, ?, 'REFUND', ?, ?, ?, ?, ?)`, [refund.branch_id, movement.product_id, movement.quantity, refund.id, digest(`stock-refund:${refund.id}:${movement.product_id}`), actor, `Devolución ${refund.id}`]);
  }
}
