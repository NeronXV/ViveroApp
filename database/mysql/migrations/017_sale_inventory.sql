ALTER TABLE branches
    ADD COLUMN inventory_enabled BOOLEAN NOT NULL DEFAULT FALSE CHECK (inventory_enabled IN (0, 1)),
    ADD COLUMN inventory_activated_at DATETIME(6) NULL,
    ADD COLUMN inventory_activated_by INT UNSIGNED NULL,
    ADD FOREIGN KEY (inventory_activated_by) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT branch_inventory_activation_check CHECK
      ((inventory_enabled = 0 AND inventory_activated_at IS NULL AND inventory_activated_by IS NULL)
       OR (inventory_enabled = 1 AND inventory_activated_at IS NOT NULL AND inventory_activated_by IS NOT NULL));

ALTER TABLE inventory_movements
    ADD COLUMN sale_id INT UNSIGNED NULL,
    ADD COLUMN refund_id INT UNSIGNED NULL,
    ADD FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    ADD FOREIGN KEY (refund_id) REFERENCES sale_refunds(id) ON DELETE RESTRICT,
    ADD UNIQUE (sale_id, product_id),
    ADD UNIQUE (refund_id, product_id),
    DROP CONSTRAINT CONSTRAINT_1,
    DROP CONSTRAINT CONSTRAINT_2,
    DROP CONSTRAINT CONSTRAINT_3,
    ADD CONSTRAINT inventory_movement_type_check CHECK (movement_type IN ('OPENING', 'RECEPTION', 'ADJUSTMENT_ADD', 'ADJUSTMENT_SUB', 'SALE', 'REFUND')),
    ADD CONSTRAINT inventory_movement_sign_check CHECK
      ((movement_type IN ('OPENING', 'RECEPTION', 'ADJUSTMENT_ADD', 'REFUND') AND quantity > 0)
       OR (movement_type IN ('ADJUSTMENT_SUB', 'SALE') AND quantity < 0)),
    ADD CONSTRAINT inventory_movement_actor_check CHECK
      ((movement_type = 'OPENING' AND created_by IS NULL AND idempotency_hash IS NULL)
       OR (movement_type <> 'OPENING' AND created_by IS NOT NULL AND idempotency_hash IS NOT NULL)),
    ADD CONSTRAINT inventory_movement_reference_check CHECK
      ((movement_type = 'SALE' AND sale_id IS NOT NULL AND refund_id IS NULL)
       OR (movement_type = 'REFUND' AND sale_id IS NULL AND refund_id IS NOT NULL)
       OR (movement_type NOT IN ('SALE', 'REFUND') AND sale_id IS NULL AND refund_id IS NULL));

CREATE TRIGGER inventory_sale_reference_guard BEFORE INSERT ON inventory_movements FOR EACH ROW
BEGIN
    IF NEW.movement_type = 'SALE' AND NOT EXISTS
      (SELECT 1 FROM sales s JOIN cashier_payments p ON p.sale_id = s.id
       JOIN branches b ON b.id = s.branch_id
       WHERE s.id = NEW.sale_id AND s.branch_id = NEW.branch_id AND s.status = 'PAID'
       AND b.inventory_enabled = 1 AND p.cashier_id = NEW.created_by
       AND -NEW.quantity = (SELECT SUM(i.quantity) FROM sale_items i WHERE i.sale_id = s.id AND i.product_id = NEW.product_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INVENTORY_SALE_INVALID';
    END IF;
    IF NEW.movement_type = 'REFUND' AND NOT EXISTS
      (SELECT 1 FROM sale_refunds r JOIN inventory_movements m ON m.sale_id = r.sale_id
       AND m.product_id = NEW.product_id AND m.movement_type = 'SALE'
       WHERE r.id = NEW.refund_id AND r.branch_id = NEW.branch_id AND r.restock = 1
       AND r.refunded_by = NEW.created_by AND NEW.quantity = -m.quantity) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INVENTORY_REFUND_INVALID';
    END IF;
END;

GRANT UPDATE (inventory_enabled, inventory_activated_at, inventory_activated_by) ON vivero.branches TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('017_sale_inventory');
