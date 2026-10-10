-- Add aliases without replacing any primary key, historical folio or order ID.
CREATE TABLE sale_folio_counter (
    id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    last_value BIGINT UNSIGNED NOT NULL CHECK (last_value <= 9007199254740991)
) ENGINE=InnoDB;
INSERT INTO sale_folio_counter VALUES (1, 0);
CREATE TABLE sale_folio_aliases (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sale_id INT UNSIGNED NOT NULL UNIQUE,
    ordinal BIGINT UNSIGNED NOT NULL UNIQUE CHECK (ordinal BETWEEN 1 AND 9007199254740991),
    short_folio VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin
      GENERATED ALWAYS AS (CONCAT('VD-', LPAD(CAST(ordinal AS CHAR), GREATEST(4, CHAR_LENGTH(ordinal)), '0'))) STORED UNIQUE,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE web_order_folio_aliases (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id INT UNSIGNED NOT NULL UNIQUE,
    short_folio VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin
      GENERATED ALWAYS AS (CONCAT('VW-', LPAD(CAST(order_id AS CHAR), GREATEST(4, CHAR_LENGTH(order_id)), '0'))) STORED UNIQUE,
    FOREIGN KEY (order_id) REFERENCES web_orders(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- A separate namespace allows current locking reads from triggers without
-- locking the sales table that invoked the trigger. Original and short names
-- must never identify different sales, including under REPEATABLE READ.
CREATE TABLE sale_folio_namespace (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    folio VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    sale_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
) ENGINE=InnoDB;
INSERT INTO sale_folio_namespace(folio, sale_id) SELECT folio, id FROM sales;

-- No runtime EXECUTE grant: this definer routine is called only by the trigger.
CREATE PROCEDURE allocate_sale_folio(IN target_id INT UNSIGNED)
BEGIN
    DECLARE sequence_value BIGINT UNSIGNED;
    DECLARE candidate VARCHAR(24);
    DECLARE reserved_id INT UNSIGNED;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET reserved_id = NULL;
    IF NOT EXISTS (SELECT 1 FROM sale_folio_aliases WHERE sale_id = target_id) THEN
        allocation: LOOP
            UPDATE sale_folio_counter SET last_value = last_value + 1 WHERE id = 1;
            SELECT last_value INTO sequence_value FROM sale_folio_counter WHERE id = 1 FOR UPDATE;
            SET candidate = CONCAT('VD-', LPAD(CAST(sequence_value AS CHAR), GREATEST(4, CHAR_LENGTH(sequence_value)), '0'));
            SET reserved_id = NULL;
            SELECT sale_id INTO reserved_id FROM sale_folio_namespace WHERE folio = BINARY candidate LIMIT 1 FOR UPDATE;
            IF (reserved_id IS NULL OR reserved_id = target_id)
                AND NOT EXISTS (SELECT 1 FROM sale_folio_aliases WHERE short_folio = candidate) THEN
                LEAVE allocation;
            END IF;
        END LOOP;
        INSERT INTO sale_folio_aliases(sale_id, ordinal) VALUES(target_id, sequence_value);
        IF reserved_id IS NULL THEN
            INSERT INTO sale_folio_namespace(folio, sale_id) VALUES(candidate, target_id);
        END IF;
    END IF;
END;

CREATE PROCEDURE backfill_sale_folios()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE target_id INT UNSIGNED;
    DECLARE historical_sales CURSOR FOR SELECT id FROM sales ORDER BY id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;
    OPEN historical_sales;
    backfill: LOOP
        FETCH historical_sales INTO target_id;
        IF finished THEN LEAVE backfill; END IF;
        CALL allocate_sale_folio(target_id);
    END LOOP;
    CLOSE historical_sales;
END;
CALL backfill_sale_folios();
DROP PROCEDURE backfill_sale_folios;
INSERT INTO web_order_folio_aliases(order_id) SELECT id FROM web_orders ORDER BY id;

CREATE TRIGGER sale_folio_alias_insert AFTER INSERT ON sales FOR EACH ROW
BEGIN
    INSERT INTO sale_folio_namespace(folio, sale_id) VALUES(NEW.folio, NEW.id);
    CALL allocate_sale_folio(NEW.id);
END;
CREATE TRIGGER sale_folio_namespace_insert BEFORE INSERT ON sales FOR EACH ROW
BEGIN
    DECLARE counter_lock BIGINT UNSIGNED;
    DECLARE conflicting_sale INT UNSIGNED;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET conflicting_sale = NULL;
    SELECT last_value INTO counter_lock FROM sale_folio_counter WHERE id = 1 FOR UPDATE;
    SELECT sale_id INTO conflicting_sale FROM sale_folio_aliases WHERE short_folio = BINARY NEW.folio LIMIT 1 FOR UPDATE;
    IF conflicting_sale IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SALE_FOLIO_ALIAS_CONFLICT';
    END IF;
END;
CREATE TRIGGER sale_folio_namespace_update BEFORE UPDATE ON sales FOR EACH ROW
BEGIN
    IF NOT (BINARY NEW.folio <=> BINARY OLD.folio) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SALE_FOLIO_IMMUTABLE';
    END IF;
END;
CREATE TRIGGER web_order_folio_alias_insert AFTER INSERT ON web_orders FOR EACH ROW
BEGIN
    INSERT INTO web_order_folio_aliases(order_id) VALUES(NEW.id);
END;
GRANT SELECT ON vivero.sale_folio_aliases TO 'catalog_api'@'%';
GRANT SELECT ON vivero.web_order_folio_aliases TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES ('031_short_folios');
