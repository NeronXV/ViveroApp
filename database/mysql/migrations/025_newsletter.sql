CREATE TABLE newsletter_subscribers (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 confirmation_hash BINARY(32) NULL UNIQUE,
 requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 confirmed_at DATETIME(6) NULL,
 unsubscribed_at DATETIME(6) NULL,
 consent_version INT UNSIGNED NOT NULL DEFAULT 0,
 unsubscribe_hash BINARY(32) NULL UNIQUE,
 unsubscribe_cipher VARBINARY(128) NULL
) ENGINE=InnoDB;
CREATE TABLE newsletter_campaigns (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 created_by INT UNSIGNED NOT NULL,
 request_key BINARY(32) NOT NULL,
 subject VARCHAR(150) NOT NULL,
 body TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(created_by,request_key),
 FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(CHAR_LENGTH(TRIM(subject)) BETWEEN 3 AND 150),
 CHECK(CHAR_LENGTH(TRIM(body)) BETWEEN 10 AND 10000)
) ENGINE=InnoDB;
CREATE TABLE newsletter_deliveries (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 campaign_id INT UNSIGNED NOT NULL,
 subscriber_id INT UNSIGNED NOT NULL,
 consent_version INT UNSIGNED NOT NULL,
 mail_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_cipher MEDIUMBLOB NOT NULL,
 first_attempt_at DATETIME(6) NULL,
 sent_at DATETIME(6) NULL,
 skipped_at DATETIME(6) NULL,
 UNIQUE(campaign_id,subscriber_id),
 FOREIGN KEY(campaign_id) REFERENCES newsletter_campaigns(id) ON DELETE RESTRICT,
 FOREIGN KEY(subscriber_id) REFERENCES newsletter_subscribers(id) ON DELETE RESTRICT,
 CHECK(sent_at IS NULL OR skipped_at IS NULL)
) ENGINE=InnoDB;
GRANT SELECT,INSERT ON vivero.newsletter_subscribers TO 'catalog_api'@'%';
GRANT UPDATE(confirmation_hash,requested_at,confirmed_at,unsubscribed_at,consent_version,unsubscribe_hash,unsubscribe_cipher) ON vivero.newsletter_subscribers TO 'catalog_api'@'%';
GRANT SELECT,INSERT ON vivero.newsletter_campaigns TO 'catalog_api'@'%';
GRANT SELECT,INSERT ON vivero.newsletter_deliveries TO 'catalog_api'@'%';
GRANT UPDATE(first_attempt_at,sent_at,skipped_at) ON vivero.newsletter_deliveries TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES('025_newsletter');
