CREATE TABLE sale_refunds (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL UNIQUE,
    payment_id INT UNSIGNED NOT NULL UNIQUE,
    branch_id INT UNSIGNED NOT NULL,
    refunded_by INT UNSIGNED NOT NULL,
    amount_cents BIGINT NOT NULL CHECK (amount_cents BETWEEN 1 AND 9007199254740991),
    method VARCHAR(10) NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
    reason VARCHAR(300) NOT NULL CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 5 AND 300),
    restock BOOLEAN NOT NULL CHECK (restock IN (0, 1)),
    idempotency_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    request_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (payment_id) REFERENCES cashier_payments(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (refunded_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TRIGGER sale_refund_payment_guard BEFORE INSERT ON sale_refunds FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sales s JOIN cashier_payments p ON p.sale_id = s.id
        WHERE s.id = NEW.sale_id AND s.branch_id = NEW.branch_id AND s.status = 'PAID'
        AND p.id = NEW.payment_id AND p.branch_id = NEW.branch_id AND p.amount_due_cents = NEW.amount_cents) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REFUND_PAYMENT_INVALID';
    END IF;
END;

CREATE OR REPLACE TRIGGER web_order_checkout_guard BEFORE UPDATE ON web_orders FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status AND NEW.status = 'CANCELLED'
        AND EXISTS (SELECT 1 FROM sales WHERE web_order_id = NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_ALREADY_IN_CASHIER';
    END IF;
    IF NEW.status <> OLD.status AND NEW.status = 'COMPLETED'
        AND NOT EXISTS (SELECT 1 FROM sales s JOIN cashier_payments p ON p.sale_id = s.id
            WHERE s.web_order_id = NEW.id AND s.status = 'PAID'
            AND NOT EXISTS (SELECT 1 FROM sale_refunds r WHERE r.sale_id = s.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_PAYMENT_REQUIRED';
    END IF;
END;
GRANT SELECT, INSERT ON vivero.sale_refunds TO 'catalog_api'@'%';
INSERT INTO schema_migrations (version) VALUES ('015_sale_refunds');
