CREATE TABLE web_orders (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id INT UNSIGNED NOT NULL,
    idempotency_hash BINARY(32) NOT NULL UNIQUE,
    request_hash BINARY(32) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    customer_phone VARCHAR(24) NULL,
    customer_email VARCHAR(254) NULL,
    notes VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status = 'PENDING'),
    subtotal_cents BIGINT NOT NULL CHECK (subtotal_cents BETWEEN 1 AND 9007199254740991),
    discount_cents BIGINT NOT NULL CHECK (discount_cents BETWEEN 0 AND subtotal_cents),
    total_cents BIGINT NOT NULL CHECK (total_cents BETWEEN 1 AND 9007199254740991 AND total_cents = subtotal_cents - discount_cents),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    CHECK (CHAR_LENGTH(TRIM(customer_name)) BETWEEN 2 AND 160),
    CHECK (customer_phone IS NOT NULL OR customer_email IS NOT NULL),
    CHECK (customer_phone IS NULL OR CHAR_LENGTH(customer_phone) BETWEEN 8 AND 24),
    CHECK (customer_email IS NULL OR CHAR_LENGTH(customer_email) BETWEEN 5 AND 254)
) ENGINE=InnoDB;

CREATE TABLE web_order_items (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    internal_code VARCHAR(40) NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    list_price_cents BIGINT NOT NULL CHECK (list_price_cents BETWEEN 0 AND 9007199254740991),
    unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents BETWEEN 0 AND list_price_cents),
    promotion_id INT UNSIGNED NULL,
    promotion_name VARCHAR(160) NULL,
    discount_cents BIGINT GENERATED ALWAYS AS ((list_price_cents - unit_price_cents) * quantity) STORED,
    line_total_cents BIGINT GENERATED ALWAYS AS (unit_price_cents * quantity) STORED,
    UNIQUE (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES web_orders(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT,
    CHECK (list_price_cents * quantity <= 9007199254740991)
) ENGINE=InnoDB;

GRANT SELECT, INSERT ON vivero.web_orders TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.web_order_items TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('009_web_orders');
