-- Preserve source timestamps without rounding imported promotion boundaries.
ALTER TABLE promotions MODIFY starts_at DATETIME(6) NULL, MODIFY ends_at DATETIME(6) NULL;

CREATE TABLE catalog_promotion_sources (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_id INT UNSIGNED NOT NULL UNIQUE,
    source_hash BINARY(32) NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (source_key, source_id),
    CONSTRAINT catalog_promotion_sources_fk FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Pricing needs only the original UUID for deterministic ties, never the hash/source label.
GRANT SELECT (promotion_id, source_id) ON vivero.catalog_promotion_sources TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('007_promotion_imports');
