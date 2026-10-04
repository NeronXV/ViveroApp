-- Historical transfers use the same ledger and balance trigger, not a parallel ledger.
ALTER TABLE inventory MODIFY updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE inventory_movements
  ADD COLUMN transfer_counterpart_id INT UNSIGNED NULL,
  ADD FOREIGN KEY (transfer_counterpart_id) REFERENCES inventory_movements(id) ON DELETE RESTRICT,
  DROP CONSTRAINT inventory_movement_type_check,
  DROP CONSTRAINT inventory_movement_sign_check,
  ADD CONSTRAINT inventory_movement_type_check CHECK (movement_type IN ('OPENING','RECEPTION','ADJUSTMENT_ADD','ADJUSTMENT_SUB','SALE','REFUND','TRANSFER_IN','TRANSFER_OUT')),
  ADD CONSTRAINT inventory_movement_sign_check CHECK
    ((movement_type IN ('OPENING','RECEPTION','ADJUSTMENT_ADD','REFUND','TRANSFER_IN') AND quantity>0)
    OR (movement_type IN ('ADJUSTMENT_SUB','SALE','TRANSFER_OUT') AND quantity<0)),
  ADD CONSTRAINT inventory_transfer_reference_check CHECK
    (transfer_counterpart_id IS NULL OR (movement_type IN ('TRANSFER_IN','TRANSFER_OUT')));
CREATE TABLE inventory_movements_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  movement_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key,source_id),
  UNIQUE (source_key,movement_id),
  FOREIGN KEY (movement_id) REFERENCES inventory_movements(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE inventory_counts_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  count_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key,source_id),
  UNIQUE (source_key,count_id),
  FOREIGN KEY (count_id) REFERENCES inventory_counts(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE inventory_balance_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  inventory_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key,source_branch_id,source_product_id),
  UNIQUE (source_key,inventory_id),
  FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
INSERT INTO schema_migrations(version) VALUES ('029_inventory_imports');
