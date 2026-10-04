-- Product administration edits the minimum for the actor's active branch.
-- Quantities remain writable only through the inventory movement trigger.
GRANT UPDATE (minimum_stock) ON vivero.inventory TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('023_catalog_branch_minimum');
