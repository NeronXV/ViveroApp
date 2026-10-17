-- Preserve document lines even when their confirmed received quantity is zero.
ALTER TABLE supplier_purchase_items
 MODIFY COLUMN quantity INT UNSIGNED NOT NULL CHECK (quantity BETWEEN 0 AND 1000000);
INSERT INTO schema_migrations(version) VALUES('038_zero_received_purchase_lines');
