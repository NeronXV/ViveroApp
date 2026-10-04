CREATE TABLE sale_payment_claims (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL,
    branch_id INT UNSIGNED NOT NULL,
    cashier_id INT UNSIGNED NOT NULL,
    claim_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    renewed_at DATETIME(6) NULL,
    renewal_count INT UNSIGNED NOT NULL DEFAULT 0,
    released_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    closed_reason VARCHAR(16) NULL,
    active_sale_id INT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN released_at IS NULL AND consumed_at IS NULL THEN sale_id ELSE NULL END) STORED UNIQUE,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (cashier_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (expires_at > created_at),
    CHECK ((released_at IS NULL AND consumed_at IS NULL AND closed_reason IS NULL)
        OR (released_at IS NOT NULL AND consumed_at IS NULL AND closed_reason IS NOT NULL AND closed_reason IN ('RELEASED', 'EXPIRED'))
        OR (released_at IS NULL AND consumed_at IS NOT NULL AND closed_reason IS NOT NULL AND closed_reason = 'CONFIRMED'))
) ENGINE=InnoDB;

ALTER TABLE cashier_payments
    ADD COLUMN branch_id INT UNSIGNED NULL,
    ADD COLUMN claim_id INT UNSIGNED NULL UNIQUE,
    ADD COLUMN requested_amount_received_cents BIGINT NULL,
    ADD COLUMN request_hash BINARY(32) NULL,
    ADD FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    ADD FOREIGN KEY (claim_id) REFERENCES sale_payment_claims(id) ON DELETE RESTRICT,
    ADD CONSTRAINT cashier_payment_request_check CHECK (request_hash IS NULL
        OR (branch_id IS NOT NULL AND claim_id IS NOT NULL
            AND ((method = 'CASH' AND requested_amount_received_cents IS NOT NULL
                AND requested_amount_received_cents = amount_received_cents AND reference IS NULL)
            OR (method <> 'CASH' AND requested_amount_received_cents IS NULL))));
UPDATE cashier_payments p JOIN sales s ON s.id = p.sale_id
SET p.branch_id = s.branch_id,
    p.requested_amount_received_cents = CASE WHEN p.method = 'CASH' THEN p.amount_received_cents ELSE NULL END;

-- The only anonymous CHECK in 012 is CONSTRAINT_1 (MariaDB's generated name).
ALTER TABLE sale_status_history DROP CONSTRAINT CONSTRAINT_1;
ALTER TABLE sale_status_history ADD CONSTRAINT sale_status_history_transition_check
    CHECK ((previous_status IS NULL AND new_status = 'DRAFT')
        OR (previous_status = 'DRAFT' AND new_status = 'SENT_TO_CASHIER')
        OR (previous_status = 'SENT_TO_CASHIER' AND new_status = 'PAID'));

GRANT SELECT, INSERT ON vivero.sale_payment_claims TO 'catalog_api'@'%';
GRANT UPDATE (expires_at, renewed_at, renewal_count, released_at, consumed_at, closed_reason)
    ON vivero.sale_payment_claims TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.cashier_payments TO 'catalog_api'@'%';
GRANT UPDATE (status, updated_at) ON vivero.sales TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('013_cashier_payments');
