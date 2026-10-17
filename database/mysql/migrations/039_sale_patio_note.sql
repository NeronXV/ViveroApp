-- Optional patio reference on a pending sale. Payment references remain separate.
ALTER TABLE sales ADD COLUMN patio_note VARCHAR(120) NULL;
INSERT INTO schema_migrations(version) VALUES('039_sale_patio_note');
