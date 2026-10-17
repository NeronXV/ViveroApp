-- Fast-track publishing capability for cataloging drafts and draft audit fields.
INSERT INTO permissions (name) VALUES ('PUBLISH_CATALOGING');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'PUBLISH_CATALOGING' AND r.name IN ('OWNER', 'ADMIN', 'MANAGER');

ALTER TABLE cataloging_drafts
    ADD COLUMN approved_by INT UNSIGNED NULL AFTER reviewed_at,
    ADD COLUMN approved_at DATETIME(6) NULL AFTER approved_by,
    ADD CONSTRAINT cataloging_drafts_approved_by_fk FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT;

GRANT SELECT, INSERT, UPDATE ON vivero.cataloging_drafts TO 'catalog_api'@'%';
GRANT SELECT ON vivero.permissions TO 'catalog_api'@'%';
GRANT SELECT ON vivero.role_permissions TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('035_cataloging_publish');
