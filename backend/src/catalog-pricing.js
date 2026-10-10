import { sellableProductSql } from './product-preparation.js';

// Single statement/snapshot and server clock for the entire page.
// Decimal arithmetic prevents BIGINT overflow and floating-point rounding errors.
export function pricedCatalogQuery(columns, all) {
  return priceCatalogPage(`
    SELECT ${columns} FROM products p LEFT JOIN product_images i ON i.primary_product_id = p.id
    WHERE p.id > ? ${all ? '' : `AND p.is_active = 1 AND EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id AND c.is_active = 1) AND ${sellableProductSql()}`}
    AND (? IS NULL OR p.category_id = ?)
    AND (? = '' OR LOCATE(?, p.common_name) > 0 OR LOCATE(?, COALESCE(p.scientific_name, '')) > 0
      OR LOCATE(?, p.internal_code) > 0 OR LOCATE(?, COALESCE(p.barcode, '')) > 0)
    ORDER BY p.id LIMIT ?`);
}

// pageSql is built only by internal catalog/order code, never request text.
export function priceCatalogPage(pageSql) {
  return `WITH page AS (${pageSql}), discounts AS (
    SELECT p.id AS product_id, promo.id AS promotion_id, promo.name AS promotion_name, promo.created_at, origin.source_id AS source_order_key,
      LEAST(p.price_cents, COALESCE(promo.max_discount_cents, p.price_cents),
        CASE WHEN promo.promo_type = 'PERCENTAGE'
          THEN ROUND(CAST(p.price_cents AS DECIMAL(30,0)) * promo.percentage_bps / 10000, 0)
          ELSE promo.fixed_amount_cents END) AS discount_cents
    FROM page p JOIN promotions promo ON promo.is_active = 1
      AND (promo.starts_at IS NULL OR promo.starts_at <= UTC_TIMESTAMP(6))
      AND (promo.ends_at IS NULL OR promo.ends_at > UTC_TIMESTAMP(6))
      AND promo.min_purchase_cents <= p.price_cents
      AND (promo.scope = 'ALL_PRODUCTS' OR (promo.scope = 'SELECTED_PRODUCTS' AND EXISTS (
        SELECT 1 FROM promotion_products pp WHERE pp.promotion_id = promo.id AND pp.product_id = p.id AND pp.is_active = 1)))
    LEFT JOIN catalog_promotion_sources origin ON origin.promotion_id = promo.id
    WHERE p.is_active = 1
  ), ranked AS (
    SELECT discounts.*, ROW_NUMBER() OVER (PARTITION BY product_id ORDER BY discount_cents DESC, created_at, (source_order_key IS NULL), source_order_key, promotion_id) AS position
    FROM discounts WHERE discount_cents > 0
  )
  SELECT page.*, page.price_cents - COALESCE(r.discount_cents, 0) AS effective_price_cents,
    r.promotion_id, r.promotion_name,
    ROUND(CAST(r.discount_cents AS DECIMAL(40,20)) * 100 / NULLIF(page.price_cents, 0), 2) AS discount_percent
  FROM page LEFT JOIN ranked r ON r.product_id = page.id AND r.position = 1 ORDER BY page.id`;
}
