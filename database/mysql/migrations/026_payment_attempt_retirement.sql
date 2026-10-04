-- Fence only uncommitted keys; never cancel a sale, payment or inventory movement.
CREATE TABLE payment_attempt_retirements (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id INT UNSIGNED NOT NULL,
  branch_id INT UNSIGNED NOT NULL,
  sale_id INT UNSIGNED NOT NULL,
  idempotency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
  FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
GRANT SELECT, INSERT ON vivero.payment_attempt_retirements TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('026_payment_attempt_retirement');
