-- Permanent cataloging drafts, isolated from products, sales and inventory.
ALTER TABLE products ADD catalog_revision INT UNSIGNED NOT NULL DEFAULT 1;
CREATE TRIGGER product_catalog_revision BEFORE UPDATE ON products FOR EACH ROW
    SET NEW.catalog_revision = OLD.catalog_revision + 1;

CREATE TABLE cataloging_drafts (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
    original_name VARCHAR(240) NOT NULL,
    source_evidence JSON NULL,
    fields JSON NOT NULL CHECK (JSON_TYPE(fields) = 'OBJECT'),
    status ENUM('DRAFT','REVIEW','LINKED','PREPARED') NOT NULL DEFAULT 'DRAFT',
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    product_id INT UNSIGNED NULL,
    image_key VARCHAR(69) NULL,
    image_info JSON NULL,
    created_by INT UNSIGNED NOT NULL,
    updated_by INT UNSIGNED NOT NULL,
    reviewed_by INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at DATETIME(6) NULL,
    UNIQUE(source_key, source_id),
    FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY(updated_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK ((source_key IS NULL) = (source_id IS NULL)),
    CHECK (CHAR_LENGTH(TRIM(original_name)) BETWEEN 1 AND 240),
    CHECK ((status IN ('DRAFT','REVIEW') AND product_id IS NULL AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('LINKED','PREPARED') AND product_id IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CHECK ((image_key IS NULL) = (image_info IS NULL))
) ENGINE=InnoDB;
CREATE TABLE cataloging_events (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    draft_id INT UNSIGNED NOT NULL,
    actor_id INT UNSIGNED NOT NULL,
    request_key BINARY(32) NOT NULL UNIQUE,
    request_hash BINARY(32) NOT NULL,
    result JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(draft_id) REFERENCES cataloging_drafts(id) ON DELETE RESTRICT,
    FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
GRANT SELECT, INSERT, UPDATE ON vivero.cataloging_drafts TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.cataloging_events TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('034_cataloging_drafts');
