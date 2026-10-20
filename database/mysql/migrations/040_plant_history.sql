-- Append-only audit; no existing rows or balances are changed.
CREATE TABLE plant_history (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 product_id INT UNSIGNED NOT NULL,
 branch_id INT UNSIGNED NOT NULL,
 actor_id INT UNSIGNED NOT NULL,
 action VARCHAR(32) NOT NULL,
 reason VARCHAR(240) NOT NULL,
 before_values JSON NOT NULL,
 after_values JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(product_id) REFERENCES products(id),
 FOREIGN KEY(branch_id) REFERENCES branches(id),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 INDEX history_branch_product(branch_id,product_id,id)
) ENGINE=InnoDB;
GRANT SELECT,INSERT ON vivero.plant_history TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES('040_plant_history');
