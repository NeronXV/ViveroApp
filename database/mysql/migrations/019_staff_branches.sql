CREATE TABLE administration_changes (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_id INT UNSIGNED NOT NULL,
    user_id INT UNSIGNED NULL,
    branch_id INT UNSIGNED NULL,
    action VARCHAR(24) NOT NULL,
    previous_value JSON NULL,
    new_value JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    CHECK ((action IN ('USER_ROLE', 'USER_ACTIVE', 'USER_BRANCH') AND user_id IS NOT NULL AND branch_id IS NULL)
        OR (action IN ('BRANCH_CREATE', 'BRANCH_UPDATE', 'BRANCH_ACTIVE') AND user_id IS NULL AND branch_id IS NOT NULL))
) ENGINE=InnoDB;

GRANT UPDATE (is_active, role_id, branch_id, updated_at) ON vivero.users TO 'catalog_api'@'%';
GRANT INSERT (code, name) ON vivero.branches TO 'catalog_api'@'%';
GRANT UPDATE (code, name, is_active, updated_at) ON vivero.branches TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.administration_changes TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('019_staff_branches');
