-- Private maintenance mappings; runtime API receives no additional privileges.
CREATE TABLE identity_branch_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  branch_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, branch_id),
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
CREATE TABLE identity_user_sources (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  source_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  source_hash BINARY(32) NOT NULL,
  target_hash BINARY(32) NOT NULL,
  UNIQUE (source_key, source_id),
  UNIQUE (source_key, user_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
INSERT INTO schema_migrations(version) VALUES ('027_identity_imports');
