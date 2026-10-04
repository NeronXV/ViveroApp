-- Synthetic local demo only; not an import or a production seed.
START TRANSACTION;
INSERT INTO branches (code, name) VALUES ('DEMO', 'Sucursal demostrativa');
INSERT INTO roles (name, display_name) VALUES
    ('SALES', 'Ventas'), ('CASHIER', 'Caja'), ('INVENTORY', 'Inventario'),
    ('MANAGER', 'Gerencia'), ('ADMIN', 'Administracion'), ('OWNER', 'Propietario');
INSERT INTO users (email, full_name, branch_id, role_id, is_active)
SELECT 'demo@example.invalid', 'Persona de demostracion', b.id, r.id, FALSE
FROM branches b CROSS JOIN roles r WHERE b.code = 'DEMO' AND r.name = 'ADMIN';
INSERT INTO categories (name, description) VALUES
    ('Plantas de interior', 'Catalogo demostrativo local'),
    ('Suculentas', 'Catalogo demostrativo local');
INSERT INTO products (internal_code, common_name, category_id, price_cents, unit)
SELECT 'DEMO-MONSTERA', 'Monstera demo', id, 25000, 'maceta'
FROM categories WHERE name = 'Plantas de interior';
INSERT INTO products (internal_code, common_name, category_id, price_cents, unit)
SELECT 'DEMO-ECHEVERIA', 'Echeveria demo', id, 8500, 'maceta'
FROM categories WHERE name = 'Suculentas';
INSERT INTO inventory (branch_id, product_id, quantity, minimum_stock)
SELECT b.id, p.id, 10, 2 FROM branches b CROSS JOIN products p WHERE b.code = 'DEMO';
-- Sales and payments intentionally empty: no operational migration yet.
COMMIT;
