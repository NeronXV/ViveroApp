-- UUIDs are external references only, never primary keys or client-facing IDs.
CREATE TABLE catalog_category_sources (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category_id INT UNSIGNED NOT NULL UNIQUE,
    source_hash BINARY(32) NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (source_key, source_id),
    CONSTRAINT catalog_category_sources_fk FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE catalog_product_sources (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_id INT UNSIGNED NOT NULL UNIQUE,
    source_hash BINARY(32) NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (source_key, source_id),
    CONSTRAINT catalog_product_sources_fk FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- No grants for catalog_api. Only the local administrative CLI uses these maps.
INSERT INTO schema_migrations (version) VALUES ('006_catalog_imports');
