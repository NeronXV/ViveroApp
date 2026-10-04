ALTER TABLE sales ADD COLUMN web_order_id INT UNSIGNED NULL UNIQUE,
    ADD FOREIGN KEY (web_order_id) REFERENCES web_orders(id) ON DELETE RESTRICT;

ALTER TABLE web_orders MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'CONFIRMED', 'READY', 'CANCELLED', 'COMPLETED'));
ALTER TABLE web_order_status_history DROP CONSTRAINT CONSTRAINT_1;
ALTER TABLE web_order_status_history ADD CONSTRAINT web_order_history_transition_check
    CHECK ((revision = 0 AND previous_status IS NULL AND new_status = 'PENDING' AND changed_by IS NULL)
        OR (revision > 0 AND changed_by IS NOT NULL AND previous_status IS NOT NULL AND
          ((previous_status = 'PENDING' AND new_status IN ('CONFIRMED', 'CANCELLED'))
          OR (previous_status = 'CONFIRMED' AND new_status IN ('READY', 'CANCELLED'))
          OR (previous_status = 'READY' AND new_status IN ('COMPLETED', 'CANCELLED')))));

-- DELIMITER is a client command; protocol queries accept this compound trigger.
CREATE TRIGGER web_order_checkout_guard BEFORE UPDATE ON web_orders FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status AND NEW.status = 'CANCELLED'
        AND EXISTS (SELECT 1 FROM sales WHERE web_order_id = NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_ALREADY_IN_CASHIER';
    END IF;
    IF NEW.status <> OLD.status AND NEW.status = 'COMPLETED'
        AND NOT EXISTS (SELECT 1 FROM sales s JOIN cashier_payments p ON p.sale_id = s.id
            WHERE s.web_order_id = NEW.id AND s.status = 'PAID') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WEB_ORDER_PAYMENT_REQUIRED';
    END IF;
END;

INSERT INTO schema_migrations (version) VALUES ('014_web_order_checkout');
