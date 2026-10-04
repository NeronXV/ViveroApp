CREATE TABLE product_images (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id INT UNSIGNED NOT NULL,
    storage_key CHAR(69) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    alt_text VARCHAR(500) NOT NULL DEFAULT '',
    sort_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    width SMALLINT UNSIGNED NOT NULL,
    height SMALLINT UNSIGNED NOT NULL,
    byte_size INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    primary_product_id INT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN is_primary = 1 AND is_active = 1 THEN product_id ELSE NULL END) STORED,
    CONSTRAINT product_images_product_fk FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT product_images_one_primary UNIQUE (primary_product_id),
    CHECK (is_primary IN (0, 1) AND is_active IN (0, 1)),
    CHECK (width BETWEEN 1 AND 2048 AND height BETWEEN 1 AND 2048),
    CHECK (byte_size BETWEEN 1 AND 5242880)
) ENGINE=InnoDB;

GRANT SELECT, INSERT, UPDATE ON vivero.product_images TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('004_product_images');
