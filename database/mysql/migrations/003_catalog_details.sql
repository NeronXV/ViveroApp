-- Additive catalog upgrade. Apply once through the maintenance migrator.
-- Existing products retain their price, identity, category and active state.
ALTER TABLE products
    ADD COLUMN wholesale_price_cents BIGINT NULL,
    ADD COLUMN watering_advice VARCHAR(2000) NOT NULL DEFAULT '',
    ADD COLUMN light_type VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN recommended_climate VARCHAR(160) NOT NULL DEFAULT '',
    ADD CONSTRAINT products_wholesale_price_check CHECK (
        wholesale_price_cents IS NULL OR wholesale_price_cents BETWEEN 0 AND 9007199254740991
    );

INSERT INTO schema_migrations (version) VALUES ('003_catalog_details');
