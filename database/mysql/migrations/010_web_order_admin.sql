ALTER TABLE web_orders
    MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'READY', 'CANCELLED')),
    ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE web_orders SET updated_at = created_at;

CREATE TABLE web_order_status_history (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id INT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL,
    previous_status VARCHAR(24) NULL,
    new_status VARCHAR(24) NOT NULL,
    changed_by INT UNSIGNED NULL,
    observation VARCHAR(500) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (order_id, revision),
    FOREIGN KEY (order_id) REFERENCES web_orders(id) ON DELETE RESTRICT,
    FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK ((revision = 0 AND previous_status IS NULL AND new_status = 'PENDING' AND changed_by IS NULL)
        OR (revision > 0 AND changed_by IS NOT NULL AND previous_status IS NOT NULL AND
          ((previous_status = 'PENDING' AND new_status IN ('CONFIRMED', 'CANCELLED'))
          OR (previous_status = 'CONFIRMED' AND new_status IN ('READY', 'CANCELLED'))
          OR (previous_status = 'READY' AND new_status = 'CANCELLED'))))
) ENGINE=InnoDB;

-- Existing 009 orders can only be PENDING. Preserve their original creation time.
INSERT INTO web_order_status_history (order_id, revision, new_status, changed_at)
SELECT id, 0, 'PENDING', created_at FROM web_orders;

GRANT SELECT, INSERT ON vivero.web_order_status_history TO 'catalog_api'@'%';
GRANT UPDATE (status, revision, updated_at) ON vivero.web_orders TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('010_web_order_admin');
