-- Preserve source_items and existing receipts; additions only.
ALTER TABLE supplier_purchase_documents
 ADD revision INT UNSIGNED NOT NULL DEFAULT 1,
 ADD last_edit_hash BINARY(32) NULL,
 ADD confirmation_request_hash BINARY(32) NULL,
 ADD source_fingerprint BINARY(32) NULL,
 ADD INDEX purchase_source_fingerprint(supplier_id,branch_id,source_fingerprint);
ALTER TABLE supplier_purchase_items ADD new_product JSON NULL CHECK(new_product IS NULL OR JSON_TYPE(new_product)='OBJECT');
GRANT UPDATE(revision,last_edit_hash,confirmation_request_hash,expected_total_cents,source_fingerprint) ON vivero.supplier_purchase_documents TO 'catalog_api'@'%';
GRANT UPDATE(quantity,line_total_cents,new_product) ON vivero.supplier_purchase_items TO 'catalog_api'@'%';
CREATE TABLE plant_edit_events (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 product_id INT UNSIGNED NOT NULL,
 actor_id INT UNSIGNED NOT NULL,
 branch_id INT UNSIGNED NOT NULL,
 request_key BINARY(32) NOT NULL UNIQUE,
 request_hash BINARY(32) NOT NULL,
 result JSON NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(product_id) REFERENCES products(id),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 FOREIGN KEY(branch_id) REFERENCES branches(id)
) ENGINE=InnoDB;
GRANT SELECT,INSERT ON vivero.plant_edit_events TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES('037_receiving_pending_plants');
