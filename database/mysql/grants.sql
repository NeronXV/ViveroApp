-- The image creates catalog_api. Narrow its initial database-wide privileges.
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'catalog_api'@'%';
GRANT SELECT, INSERT, UPDATE ON vivero.categories TO 'catalog_api'@'%';
GRANT SELECT, INSERT, UPDATE ON vivero.products TO 'catalog_api'@'%';
