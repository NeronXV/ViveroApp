-- Additive audit; no historical operation is rewritten.
CREATE TABLE sale_cancellations (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL UNIQUE,
    branch_id INT UNSIGNED NOT NULL,
    cancelled_by INT UNSIGNED NOT NULL,
    reason VARCHAR(240) NOT NULL CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 3 AND 240),
    idempotency_hash BINARY(32) NOT NULL UNIQUE,
    request_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
ALTER TABLE sale_status_history DROP CONSTRAINT sale_status_history_transition_check;
ALTER TABLE sale_status_history ADD CONSTRAINT sale_status_history_transition_check
    CHECK ((previous_status IS NULL AND new_status = 'DRAFT')
        OR (previous_status = 'DRAFT' AND new_status = 'SENT_TO_CASHIER')
        OR (previous_status = 'SENT_TO_CASHIER' AND new_status IN ('PAID', 'CANCELLED')));
CREATE TRIGGER sale_cancellation_guard BEFORE UPDATE ON sales FOR EACH ROW
BEGIN
    IF OLD.status = 'CANCELLED' AND NEW.status <> 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SALE_CANCELLATION_FINAL';
    END IF;
    IF NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED' THEN
        IF OLD.status <> 'SENT_TO_CASHIER'
            OR EXISTS (SELECT 1 FROM cashier_payments WHERE sale_id = OLD.id)
            OR EXISTS (SELECT 1 FROM sale_payment_claims WHERE active_sale_id = OLD.id)
            OR EXISTS (SELECT 1 FROM inventory_movements WHERE sale_id = OLD.id)
            OR NOT EXISTS (SELECT 1 FROM sale_cancellations WHERE sale_id = OLD.id AND branch_id = OLD.branch_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SALE_CANCELLATION_UNSAFE';
        END IF;
    END IF;
END;
-- The previous guard rejected every linked order cancellation. Permit only
-- the paired, audited sale cancellation, preserving the paid-order safeguard.
CREATE OR REPLACE TRIGGER web_order_checkout_guard BEFORE UPDATE ON web_orders FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status AND NEW.status = 'CANCELLED'
        AND EXISTS (SELECT 1 FROM sales s WHERE s.web_order_id = NEW.id
            AND (s.status <> 'CANCELLED' OR NOT EXISTS
                (SELECT 1 FROM sale_cancellations c WHERE c.sale_id = s.id AND c.branch_id = NEW.branch_id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_ALREADY_IN_CASHIER';
    END IF;
    IF NEW.status <> OLD.status AND NEW.status = 'COMPLETED'
        AND NOT EXISTS (SELECT 1 FROM sales s JOIN cashier_payments p ON p.sale_id = s.id
            WHERE s.web_order_id = NEW.id AND s.status = 'PAID'
            AND NOT EXISTS (SELECT 1 FROM sale_refunds r WHERE r.sale_id = s.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_PAYMENT_REQUIRED';
    END IF;
END;
GRANT SELECT, INSERT ON vivero.sale_cancellations TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('032_pending_sale_cancellations');
