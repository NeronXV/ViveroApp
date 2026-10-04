CREATE TABLE suppliers (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(32) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 160),
 CHECK (code REGEXP '^[A-Z0-9][A-Z0-9-]{1,31}$' AND BINARY code = BINARY UPPER(code)),
 CHECK (is_active IN (0,1))
) ENGINE=InnoDB;
CREATE TABLE supplier_presentations (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 supplier_id INT UNSIGNED NOT NULL,
 code VARCHAR(24) NOT NULL,
 display_name VARCHAR(80) NULL,
 nominal_size DECIMAL(10,2) NULL,
 size_unit VARCHAR(3) NULL,
 notes VARCHAR(240) NULL,
 UNIQUE(supplier_id,code),
 FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,
 CHECK(CHAR_LENGTH(TRIM(code)) BETWEEN 1 AND 24),
 CHECK((nominal_size IS NULL AND size_unit IS NULL) OR (nominal_size > 0 AND size_unit IN ('cm','in','l','gal')))
) ENGINE=InnoDB;
CREATE TABLE supplier_purchase_documents (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 supplier_id INT UNSIGNED NOT NULL,
 branch_id INT UNSIGNED NOT NULL,
 document_date DATE NOT NULL,
 external_reference VARCHAR(80) NULL,
 payment_terms VARCHAR(8) NOT NULL CHECK(payment_terms IN ('CASH','CREDIT','OTHER')),
 expected_total_cents BIGINT UNSIGNED NOT NULL CHECK(expected_total_cents <= 9007199254740991),
 currency VARCHAR(3) NOT NULL DEFAULT 'MXN' CHECK(currency = 'MXN'),
 source_file_name VARCHAR(180) NULL,
 source_items JSON NOT NULL CHECK(JSON_TYPE(source_items) = 'ARRAY'),
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','RECEIVED','CANCELLED')),
 idempotency_hash BINARY(32) NOT NULL UNIQUE,
 request_hash BINARY(32) NOT NULL,
 confirmation_hash BINARY(32) NULL UNIQUE,
 created_by INT UNSIGNED NOT NULL,
 received_by INT UNSIGNED NULL,
 received_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(supplier_id,external_reference),
 FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,
 FOREIGN KEY(branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE RESTRICT,
 FOREIGN KEY(received_by) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK((status = 'RECEIVED' AND confirmation_hash IS NOT NULL AND received_by IS NOT NULL AND received_at IS NOT NULL)
 OR (status <> 'RECEIVED' AND confirmation_hash IS NULL AND received_by IS NULL AND received_at IS NULL))
) ENGINE=InnoDB;
CREATE TABLE supplier_purchase_items (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 purchase_id INT UNSIGNED NOT NULL,
 line_number INT UNSIGNED NOT NULL CHECK(line_number > 0),
 raw_description VARCHAR(240) NOT NULL,
 supplier_container_code VARCHAR(24) NOT NULL,
 suggested_common_name VARCHAR(160) NULL,
 suggested_presentation VARCHAR(120) NULL,
 quantity INT UNSIGNED NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),
 unit_cost_cents BIGINT UNSIGNED NOT NULL CHECK(unit_cost_cents <= 9007199254740991),
 line_total_cents BIGINT UNSIGNED NOT NULL CHECK(line_total_cents <= 9007199254740991 AND line_total_cents = quantity * unit_cost_cents),
 product_id INT UNSIGNED NULL,
 resolution_status VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED',
 resolved_by INT UNSIGNED NULL,
 resolved_at DATETIME(6) NULL,
 movement_id INT UNSIGNED NULL UNIQUE,
 UNIQUE(purchase_id,line_number),
 FOREIGN KEY(purchase_id) REFERENCES supplier_purchase_documents(id) ON DELETE RESTRICT,
 FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE RESTRICT,
 FOREIGN KEY(resolved_by) REFERENCES users(id) ON DELETE RESTRICT,
 FOREIGN KEY(movement_id) REFERENCES inventory_movements(id) ON DELETE RESTRICT,
 CHECK((resolution_status IN ('MATCHED','AUTO_MATCHED') AND product_id IS NOT NULL)
 OR (resolution_status IN ('UNMATCHED','IGNORED') AND product_id IS NULL)),
 CHECK((resolution_status = 'UNMATCHED' AND resolved_by IS NULL AND resolved_at IS NULL)
 OR (resolution_status <> 'UNMATCHED' AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)),
 CHECK(movement_id IS NULL OR resolution_status IN ('MATCHED','AUTO_MATCHED'))
) ENGINE=InnoDB;
CREATE TABLE supplier_product_aliases (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 supplier_id INT UNSIGNED NOT NULL,
 alias_hash BINARY(32) NOT NULL,
 raw_description VARCHAR(240) NOT NULL,
 supplier_container_code VARCHAR(24) NOT NULL,
 product_id INT UNSIGNED NOT NULL,
 updated_by INT UNSIGNED NOT NULL,
 UNIQUE(supplier_id,alias_hash),
 FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,
 FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE RESTRICT,
 FOREIGN KEY(updated_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
GRANT SELECT, INSERT ON vivero.suppliers TO 'catalog_api'@'%';
GRANT UPDATE(code,name,is_active,updated_at) ON vivero.suppliers TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.supplier_presentations TO 'catalog_api'@'%';
GRANT UPDATE(display_name,nominal_size,size_unit,notes) ON vivero.supplier_presentations TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.supplier_purchase_documents TO 'catalog_api'@'%';
GRANT UPDATE(status,confirmation_hash,received_by,received_at) ON vivero.supplier_purchase_documents TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.supplier_purchase_items TO 'catalog_api'@'%';
GRANT UPDATE(product_id,resolution_status,resolved_by,resolved_at,suggested_common_name,suggested_presentation,movement_id) ON vivero.supplier_purchase_items TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.supplier_product_aliases TO 'catalog_api'@'%';
GRANT UPDATE(product_id,updated_by,raw_description,supplier_container_code) ON vivero.supplier_product_aliases TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES('020_supplier_purchases');
