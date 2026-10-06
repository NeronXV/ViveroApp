-- A fence for uncommitted draft keys. Never deletes a purchase or its source lines.
CREATE TABLE purchase_draft_retirements (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id INT UNSIGNED NOT NULL,
  branch_id INT UNSIGNED NOT NULL,
  idempotency_hash BINARY(32) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
GRANT SELECT, INSERT ON vivero.purchase_draft_retirements TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('030_purchase_draft_retirement');
