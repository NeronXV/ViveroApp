-- Preserve existing sales. Customer association is optional and references real IDs.
ALTER TABLE sales ADD COLUMN customer_id INT UNSIGNED NULL,
  ADD CONSTRAINT sales_customer_fk FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT;

-- Retiring a key fences late submissions. This is not cancellation of a saved sale.
CREATE TABLE sale_attempt_retirements (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id INT UNSIGNED NOT NULL,
  branch_id INT UNSIGNED NOT NULL,
  idempotency_hash BINARY(32) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
GRANT SELECT, INSERT ON vivero.sale_attempt_retirements TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('022_counter_sale_completion');
