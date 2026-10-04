CREATE TABLE inventory_movements (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    movement_type VARCHAR(24) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    idempotency_hash BINARY(32) NULL UNIQUE,
    notes VARCHAR(240) NULL,
    created_by INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id, product_id) REFERENCES inventory(branch_id, product_id) ON DELETE RESTRICT,
    CHECK (movement_type IN ('OPENING', 'RECEPTION', 'ADJUSTMENT_ADD', 'ADJUSTMENT_SUB')),
    CHECK ((movement_type IN ('OPENING', 'RECEPTION', 'ADJUSTMENT_ADD') AND quantity > 0)
        OR (movement_type = 'ADJUSTMENT_SUB' AND quantity < 0)),
    CHECK ((movement_type = 'OPENING' AND created_by IS NULL AND idempotency_hash IS NULL)
        OR (movement_type <> 'OPENING' AND created_by IS NOT NULL AND idempotency_hash IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE inventory_counts (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    movement_id INT UNSIGNED NULL UNIQUE,
    idempotency_hash BINARY(32) NOT NULL UNIQUE,
    branch_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    previous_quantity DECIMAL(14,3) NOT NULL CHECK (previous_quantity >= 0),
    counted_quantity DECIMAL(14,3) NOT NULL CHECK (counted_quantity >= 0),
    adjustment_quantity DECIMAL(14,3) NOT NULL,
    reason VARCHAR(240) NOT NULL,
    counted_by INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (movement_id) REFERENCES inventory_movements(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (counted_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (adjustment_quantity = counted_quantity - previous_quantity),
    CHECK ((adjustment_quantity = 0 AND movement_id IS NULL) OR (adjustment_quantity <> 0 AND movement_id IS NOT NULL)),
    CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 3 AND 240)
) ENGINE=InnoDB;

-- Preserve the current projection as opening ledger entries before the trigger;
-- installing it first would double every existing balance.
INSERT INTO inventory_movements (branch_id, product_id, movement_type, quantity, notes, created_at)
SELECT branch_id, product_id, 'OPENING', quantity, 'Saldo inicial al migrar inventario', updated_at
FROM inventory WHERE quantity > 0;

CREATE TRIGGER inventory_movement_apply_balance AFTER INSERT ON inventory_movements
FOR EACH ROW
    UPDATE inventory SET quantity = quantity + NEW.quantity
    WHERE branch_id = NEW.branch_id AND product_id = NEW.product_id;

GRANT SELECT ON vivero.inventory TO 'catalog_api'@'%';
GRANT INSERT (branch_id, product_id, quantity), UPDATE (product_id) ON vivero.inventory TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.inventory_movements TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.inventory_counts TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('011_inventory_operations');
