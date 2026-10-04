-- Additive upgrade of baseline sales; existing rows remain valid.
ALTER TABLE sales
    ADD COLUMN idempotency_hash BINARY(32) NULL UNIQUE,
    ADD COLUMN request_hash BINARY(32) NULL,
    ADD CONSTRAINT sales_request_hash_pair CHECK ((idempotency_hash IS NULL AND request_hash IS NULL)
        OR (idempotency_hash IS NOT NULL AND request_hash IS NOT NULL));

ALTER TABLE sale_items
    ADD COLUMN internal_code VARCHAR(40) NULL,
    ADD COLUMN list_price_cents BIGINT NULL,
    ADD COLUMN promotion_id INT UNSIGNED NULL,
    ADD COLUMN promotion_name VARCHAR(160) NULL,
    ADD FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT;
UPDATE sale_items SET list_price_cents = unit_price_cents;
ALTER TABLE sale_items ADD CONSTRAINT sale_items_list_price_check
    CHECK (list_price_cents IS NULL OR list_price_cents BETWEEN unit_price_cents AND 9007199254740991);

CREATE TABLE sale_status_history (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL,
    previous_status VARCHAR(24) NULL,
    new_status VARCHAR(24) NOT NULL,
    changed_by INT UNSIGNED NOT NULL,
    observation VARCHAR(240) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK ((previous_status IS NULL AND new_status = 'DRAFT')
        OR (previous_status = 'DRAFT' AND new_status = 'SENT_TO_CASHIER'))
) ENGINE=InnoDB;

-- Submission can insert/read, but cannot change a sale to PAID or edit totals.
GRANT SELECT, INSERT ON vivero.sales TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.sale_items TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.sale_status_history TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('012_sales_submission');
