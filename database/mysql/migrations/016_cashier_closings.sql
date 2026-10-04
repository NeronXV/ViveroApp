CREATE TABLE cashier_closings (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id INT UNSIGNED NOT NULL,
    cashier_id INT UNSIGNED NOT NULL,
    opening_cash_cents BIGINT NOT NULL CHECK (opening_cash_cents BETWEEN 0 AND 9007199254740991),
    cash_sales_cents BIGINT NOT NULL CHECK (cash_sales_cents BETWEEN 0 AND 9007199254740991),
    card_sales_cents BIGINT NOT NULL CHECK (card_sales_cents BETWEEN 0 AND 9007199254740991),
    transfer_sales_cents BIGINT NOT NULL CHECK (transfer_sales_cents BETWEEN 0 AND 9007199254740991),
    cash_refunds_cents BIGINT NOT NULL CHECK (cash_refunds_cents BETWEEN 0 AND 9007199254740991),
    other_refunds_cents BIGINT NOT NULL CHECK (other_refunds_cents BETWEEN 0 AND 9007199254740991),
    counted_cash_cents BIGINT NOT NULL CHECK (counted_cash_cents BETWEEN 0 AND 9007199254740991),
    expected_cash_cents BIGINT NOT NULL CHECK (expected_cash_cents BETWEEN -9007199254740991 AND 9007199254740991),
    difference_cents BIGINT NOT NULL CHECK (difference_cents BETWEEN -9007199254740991 AND 9007199254740991),
    idempotency_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (cashier_id) REFERENCES users(id) ON DELETE RESTRICT,
    CHECK (expected_cash_cents = opening_cash_cents + cash_sales_cents - cash_refunds_cents),
    CHECK (difference_cents = counted_cash_cents - expected_cash_cents)
) ENGINE=InnoDB;

-- A payment/refund belongs to exactly one closing. These relations are required
-- to assign operation IDs atomically without timestamp boundaries.
CREATE TABLE cashier_closing_payments (
    payment_id INT UNSIGNED PRIMARY KEY,
    closing_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES cashier_payments(id) ON DELETE RESTRICT,
    FOREIGN KEY (closing_id) REFERENCES cashier_closings(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE cashier_closing_refunds (
    refund_id INT UNSIGNED PRIMARY KEY,
    closing_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (refund_id) REFERENCES sale_refunds(id) ON DELETE RESTRICT,
    FOREIGN KEY (closing_id) REFERENCES cashier_closings(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TRIGGER cashier_closing_payment_guard BEFORE INSERT ON cashier_closing_payments FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cashier_payments p JOIN cashier_closings c
        ON c.branch_id = p.branch_id AND c.cashier_id = p.cashier_id
        WHERE p.id = NEW.payment_id AND c.id = NEW.closing_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CLOSING_PAYMENT_INVALID';
    END IF;
END;
CREATE TRIGGER cashier_closing_refund_guard BEFORE INSERT ON cashier_closing_refunds FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sale_refunds r JOIN cashier_closings c
        ON c.branch_id = r.branch_id AND c.cashier_id = r.refunded_by
        WHERE r.id = NEW.refund_id AND c.id = NEW.closing_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CLOSING_REFUND_INVALID';
    END IF;
END;
GRANT SELECT, INSERT ON vivero.cashier_closings TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.cashier_closing_payments TO 'catalog_api'@'%';
GRANT SELECT, INSERT ON vivero.cashier_closing_refunds TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('016_cashier_closings');
