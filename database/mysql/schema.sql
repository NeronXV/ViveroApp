-- Baseline 001. MariaDB 11.4 / InnoDB. Apply only to an empty database.
-- This does not import Supabase data or implement sales/payment workflows.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE branches (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(24) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (CHAR_LENGTH(TRIM(code)) BETWEEN 2 AND 24),
    CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 120)
) ENGINE=InnoDB;

CREATE TABLE roles (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(24) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    CHECK (name IN ('SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER'))
) ENGINE=InnoDB;

-- One role per user, as in the current Supabase user_roles PK(user_id).
-- Profile fields belong here; no redundant profiles/user_roles tables.
-- No login/authentication endpoints are enabled in phase 1.
CREATE TABLE users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    full_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NULL,
    branch_id INT UNSIGNED NULL,
    role_id INT UNSIGNED NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE CHECK (is_active IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT,
    CHECK (CHAR_LENGTH(TRIM(full_name)) BETWEEN 2 AND 160)
) ENGINE=InnoDB;

CREATE TABLE categories (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 100)
) ENGINE=InnoDB;

CREATE TABLE products (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    internal_code VARCHAR(40) NOT NULL UNIQUE,
    barcode VARCHAR(128) NULL UNIQUE,
    common_name VARCHAR(160) NOT NULL,
    scientific_name VARCHAR(160) NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    category_id INT UNSIGNED NOT NULL,
    price_cents BIGINT NOT NULL CHECK (price_cents BETWEEN 0 AND 9007199254740991),
    unit VARCHAR(10) NOT NULL DEFAULT 'pieza' CHECK (unit IN ('pieza', 'maceta', 'charola', 'bolsa', 'kg')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CHECK (CHAR_LENGTH(TRIM(internal_code)) BETWEEN 2 AND 40),
    CHECK (CHAR_LENGTH(TRIM(common_name)) BETWEEN 2 AND 160),
    CHECK (barcode IS NULL OR CHAR_LENGTH(TRIM(barcode)) BETWEEN 4 AND 128)
) ENGINE=InnoDB;

CREATE TABLE inventory (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    quantity DECIMAL(14,3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    minimum_stock DECIMAL(14,3) NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (branch_id, product_id),
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE sales (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    folio VARCHAR(40) NOT NULL UNIQUE,
    branch_id INT UNSIGNED NOT NULL,
    created_by INT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SENT_TO_CASHIER', 'PAYMENT_PENDING', 'PAID', 'CANCELLED', 'DELIVERED')),
    subtotal_cents BIGINT NOT NULL CHECK (subtotal_cents BETWEEN 0 AND 9007199254740991),
    discount_cents BIGINT NOT NULL DEFAULT 0 CHECK (discount_cents BETWEEN 0 AND subtotal_cents),
    total_cents BIGINT NOT NULL CHECK (total_cents = subtotal_cents - discount_cents),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE sale_items (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL,
    product_id INT UNSIGNED NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents BETWEEN 0 AND 9007199254740991),
    line_total_cents BIGINT NOT NULL CHECK (line_total_cents BETWEEN 0 AND 9007199254740991),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE cashier_payments (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL UNIQUE,
    cashier_id INT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    method VARCHAR(10) NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
    amount_due_cents BIGINT NOT NULL CHECK (amount_due_cents BETWEEN 1 AND 9007199254740991),
    amount_received_cents BIGINT NOT NULL CHECK (amount_received_cents BETWEEN amount_due_cents AND 9007199254740991),
    change_cents BIGINT NOT NULL CHECK (change_cents = amount_received_cents - amount_due_cents),
    reference VARCHAR(120) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (cashier_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 16 AND 100),
    CHECK (method = 'CASH' OR change_cents = 0),
    CHECK (method <> 'TRANSFER' OR CHAR_LENGTH(TRIM(COALESCE(reference, ''))) > 0)
) ENGINE=InnoDB;
