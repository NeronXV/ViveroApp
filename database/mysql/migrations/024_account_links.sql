CREATE TABLE account_links (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 user_id INT UNSIGNED NOT NULL,
 token_hash BINARY(32) NOT NULL UNIQUE,
 purpose VARCHAR(12) NOT NULL CHECK (purpose IN ('RESET','INVITE')),
 expires_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
GRANT SELECT, INSERT, DELETE ON vivero.account_links TO 'catalog_api'@'%';
CREATE TRIGGER users_revoke_account_links AFTER UPDATE ON users FOR EACH ROW
DELETE FROM account_links WHERE user_id=NEW.id
 AND (NOT (BINARY OLD.password_hash <=> BINARY NEW.password_hash) OR OLD.is_active <> NEW.is_active);
INSERT INTO schema_migrations(version) VALUES('024_account_links');
