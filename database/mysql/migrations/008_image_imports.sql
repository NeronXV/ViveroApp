CREATE TABLE catalog_image_sources (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    image_id INT UNSIGNED NOT NULL UNIQUE,
    source_hash BINARY(32) NOT NULL,
    stored_hash BINARY(32) NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (source_key, source_id),
    CONSTRAINT catalog_image_sources_fk FOREIGN KEY (image_id) REFERENCES product_images(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Maintenance only: the HTTP account needs no access to provenance.
INSERT INTO schema_migrations (version) VALUES ('008_image_imports');
