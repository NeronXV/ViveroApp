-- Additive preparation metadata. Existing products keep their legacy contracts;
-- products created after this migration must complete preparation per branch.
ALTER TABLE products
    ADD preparation_mode ENUM('LEGACY', 'PENDING') NOT NULL DEFAULT 'LEGACY',
    ADD price_confirmed_at DATETIME(6) NULL,
    ADD price_confirmed_by INT UNSIGNED NULL,
    ADD commercial_disabled_at DATETIME(6) NULL,
    ADD commercial_disabled_by INT UNSIGNED NULL,
    ADD CONSTRAINT product_price_confirmer_fk FOREIGN KEY (price_confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT product_disabler_fk FOREIGN KEY (commercial_disabled_by) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE products ALTER preparation_mode SET DEFAULT 'PENDING';
-- A historical zero price is not a confirmed commercial price. Keep all rows
-- and their history, but require preparation before their next new sale.
UPDATE products SET preparation_mode = 'PENDING' WHERE price_cents = 0;

CREATE TABLE product_branch_preparation (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id INT UNSIGNED NOT NULL,
    branch_id INT UNSIGNED NOT NULL,
    initial_count_confirmed_at DATETIME(6) NOT NULL,
    initial_count_confirmed_by INT UNSIGNED NOT NULL,
    activated_at DATETIME(6) NULL,
    activated_by INT UNSIGNED NULL,
    UNIQUE (branch_id, product_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (initial_count_confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (activated_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK ((activated_at IS NULL) = (activated_by IS NULL))
) ENGINE=InnoDB;

CREATE TABLE inventory_count_observations (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    observed_quantity DECIMAL(14,3) NOT NULL CHECK (observed_quantity >= 0),
    baseline_quantity DECIMAL(14,3) NOT NULL CHECK (baseline_quantity >= 0),
    baseline_movement_id INT UNSIGNED NOT NULL,
    reason VARCHAR(240) NOT NULL CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 3 AND 240),
    observed_by INT UNSIGNED NOT NULL,
    observed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    idempotency_hash BINARY(32) NOT NULL UNIQUE,
    status ENUM('PENDING', 'APPLIED', 'CLOSED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    decided_by INT UNSIGNED NULL,
    decided_at DATETIME(6) NULL,
    decision_reason VARCHAR(240) NULL,
    decision_key BINARY(32) NULL UNIQUE,
    decision_hash BINARY(32) NULL,
    count_id INT UNSIGNED NULL UNIQUE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (observed_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (decided_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (count_id) REFERENCES inventory_counts(id) ON DELETE RESTRICT,
    INDEX observation_queue (branch_id, status, id),
    CHECK ((status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL AND decision_key IS NULL AND decision_hash IS NULL AND count_id IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL AND CHAR_LENGTH(TRIM(decision_reason)) BETWEEN 3 AND 240 AND decision_key IS NOT NULL AND decision_hash IS NOT NULL
            AND ((status = 'REJECTED' AND count_id IS NULL) OR (status IN ('APPLIED', 'CLOSED') AND count_id IS NOT NULL))))
) ENGINE=InnoDB;

-- No changes to roles, capabilities, balances, sale records or historic counts.
GRANT SELECT, INSERT, UPDATE ON vivero.product_branch_preparation TO 'catalog_api'@'%';
GRANT SELECT (product_id) ON vivero.catalog_product_sources TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.inventory_count_observations TO 'catalog_api'@'%';
GRANT UPDATE (status, decided_by, decided_at, decision_reason, decision_key, decision_hash, count_id)
    ON vivero.inventory_count_observations TO 'catalog_api'@'%';
GRANT UPDATE (price_confirmed_at, price_confirmed_by) ON vivero.products TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('033_inventory_preparation');
