CREATE TABLE promotions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 100),
    description VARCHAR(500) NOT NULL DEFAULT '',
    scope VARCHAR(24) NOT NULL CHECK (scope IN ('ALL_PRODUCTS', 'SELECTED_PRODUCTS')),
    promo_type VARCHAR(16) NOT NULL CHECK (promo_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    percentage_bps SMALLINT UNSIGNED NULL,
    fixed_amount_cents BIGINT NULL,
    min_purchase_cents BIGINT NOT NULL DEFAULT 0 CHECK (min_purchase_cents BETWEEN 0 AND 9007199254740991),
    max_discount_cents BIGINT NULL CHECK (max_discount_cents IS NULL OR max_discount_cents BETWEEN 1 AND 9007199254740991),
    starts_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at),
    CHECK (
        (promo_type = 'PERCENTAGE' AND percentage_bps IS NOT NULL AND percentage_bps BETWEEN 1 AND 10000 AND fixed_amount_cents IS NULL)
        OR (promo_type = 'FIXED_AMOUNT' AND fixed_amount_cents IS NOT NULL AND fixed_amount_cents BETWEEN 1 AND 9007199254740991 AND percentage_bps IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE promotion_products (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    promotion_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    CONSTRAINT promotion_products_pair UNIQUE (promotion_id, product_id),
    CONSTRAINT promotion_products_promotion_fk FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT,
    CONSTRAINT promotion_products_product_fk FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

GRANT SELECT, INSERT, UPDATE ON vivero.promotions TO 'catalog_api'@'%';
GRANT SELECT, INSERT, UPDATE ON vivero.promotion_products TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('005_catalog_promotions');
