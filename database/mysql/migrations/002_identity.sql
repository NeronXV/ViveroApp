-- Phase 2: apply once, after baseline schema/seed/grants, as migration owner.
-- DDL is not transactional in MariaDB. Do not retry a partial failure blindly.
CREATE TABLE IF NOT EXISTS schema_migrations (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(80) NOT NULL UNIQUE,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE permissions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_id INT UNSIGNED NOT NULL,
    permission_id INT UNSIGNED NOT NULL,
    UNIQUE (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE auth_sessions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    token_hash BINARY(32) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (expires_at > created_at)
) ENGINE=InnoDB;

CREATE TABLE auth_login_limits (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    key_hash BINARY(32) NOT NULL UNIQUE,
    attempts SMALLINT UNSIGNED NOT NULL,
    reset_at DATETIME(6) NOT NULL,
    CHECK (attempts > 0)
) ENGINE=InnoDB;

-- Disabling/re-enabling an account or changing its password revokes all sessions.
-- No publicly callable function is introduced.
CREATE TRIGGER users_revoke_sessions AFTER UPDATE ON users FOR EACH ROW
DELETE FROM auth_sessions WHERE user_id = NEW.id
    AND (NOT (BINARY NEW.password_hash <=> BINARY OLD.password_hash) OR NEW.is_active <> OLD.is_active);

INSERT INTO permissions (name) VALUES
    ('VIEW_CATALOG'), ('SCAN_PRODUCTS'), ('CREATE_SALES'), ('VIEW_OWN_SALES'),
    ('OPERATE_CASHIER'), ('VIEW_BRANCH_SALES'), ('VIEW_ALL_SALES'),
    ('MANAGE_PRODUCTS'), ('MANAGE_PRICES'), ('MANAGE_DISCOUNTS'),
    ('MANAGE_INVENTORY'), ('VIEW_INVENTORY_ALERTS'), ('VIEW_REPORTS'),
    ('MANAGE_BRANCHES'), ('MANAGE_USERS'), ('ASSIGN_ROLES'), ('VIEW_AUDIT'), ('MANAGE_SETTINGS');

-- Same capability assignments as 202608080001_auth_roles.sql in Supabase.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ADMIN', 'OWNER')
   OR (r.name = 'SALES' AND p.name IN ('VIEW_CATALOG', 'SCAN_PRODUCTS', 'CREATE_SALES', 'VIEW_OWN_SALES'))
   OR (r.name = 'CASHIER' AND p.name IN ('VIEW_CATALOG', 'OPERATE_CASHIER'))
   OR (r.name = 'INVENTORY' AND p.name IN ('VIEW_CATALOG', 'SCAN_PRODUCTS', 'MANAGE_PRODUCTS', 'MANAGE_INVENTORY', 'VIEW_INVENTORY_ALERTS'))
   OR (r.name = 'MANAGER' AND p.name IN ('VIEW_CATALOG', 'SCAN_PRODUCTS', 'CREATE_SALES', 'VIEW_OWN_SALES', 'OPERATE_CASHIER', 'VIEW_BRANCH_SALES', 'MANAGE_PRODUCTS', 'MANAGE_PRICES', 'MANAGE_DISCOUNTS', 'MANAGE_INVENTORY', 'VIEW_INVENTORY_ALERTS', 'VIEW_REPORTS'));

GRANT SELECT ON vivero.users TO 'catalog_api'@'%';
GRANT SELECT ON vivero.roles TO 'catalog_api'@'%';
GRANT SELECT ON vivero.branches TO 'catalog_api'@'%';
GRANT SELECT ON vivero.permissions TO 'catalog_api'@'%';
GRANT SELECT ON vivero.role_permissions TO 'catalog_api'@'%';
GRANT SELECT ON vivero.schema_migrations TO 'catalog_api'@'%';
GRANT SELECT, INSERT, DELETE ON vivero.auth_sessions TO 'catalog_api'@'%';
GRANT SELECT, INSERT, UPDATE ON vivero.auth_login_limits TO 'catalog_api'@'%';
-- No runtime writes to users, roles or permissions; no sales/payment grants.
INSERT INTO schema_migrations (version) VALUES ('002_identity');
