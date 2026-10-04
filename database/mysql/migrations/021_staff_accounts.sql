ALTER TABLE administration_changes DROP CONSTRAINT CONSTRAINT_1;
ALTER TABLE administration_changes ADD CONSTRAINT administration_action_check
 CHECK ((action IN ('USER_ROLE','USER_ACTIVE','USER_BRANCH','USER_CREATE','USER_PASSWORD') AND user_id IS NOT NULL AND branch_id IS NULL)
 OR (action IN ('BRANCH_CREATE','BRANCH_UPDATE','BRANCH_ACTIVE') AND user_id IS NULL AND branch_id IS NOT NULL));
GRANT INSERT(email,full_name,password_hash,branch_id,role_id,is_active) ON vivero.users TO 'catalog_api'@'%';
GRANT UPDATE(password_hash) ON vivero.users TO 'catalog_api'@'%';
INSERT INTO schema_migrations(version) VALUES('021_staff_accounts');
