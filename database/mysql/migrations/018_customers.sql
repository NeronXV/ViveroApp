CREATE TABLE customers (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL CHECK (CHAR_LENGTH(TRIM(full_name)) BETWEEN 2 AND 160),
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
    phone VARCHAR(20) NULL CHECK (phone IS NULL OR CHAR_LENGTH(TRIM(phone)) BETWEEN 8 AND 20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_by INT UNSIGNED NOT NULL,
    updated_by INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (email IS NULL OR (BINARY email = BINARY LOWER(email)
      AND email REGEXP '^[a-z0-9._%+-]+@[a-z0-9.-]+[.][a-z]{2,}$'))
) ENGINE=InnoDB;
GRANT SELECT, INSERT ON vivero.customers TO 'catalog_api'@'%';
GRANT UPDATE (full_name, email, phone, is_active, updated_by, updated_at) ON vivero.customers TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('018_customers');
