-- Historical maintenance only; no additional runtime privileges.
ALTER TABLE sales
  MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  MODIFY updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE cashier_payments MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE sale_items ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
CREATE TABLE history_sales_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sale_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, sale_id),
  FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE history_items_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  item_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, item_id),
  FOREIGN KEY (item_id) REFERENCES sale_items(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE history_claims_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  claim_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, claim_id),
  FOREIGN KEY (claim_id) REFERENCES sale_payment_claims(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE history_payments_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payment_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, payment_id),
  FOREIGN KEY (payment_id) REFERENCES cashier_payments(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE history_history_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  history_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, history_id),
  FOREIGN KEY (history_id) REFERENCES sale_status_history(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
INSERT INTO schema_migrations(version) VALUES ('028_history_imports');
