\set ON_ERROR_STOP on

-- Synthetic fixtures committed only in the isolated validation container.
begin;

insert into public.branches (id, code, name, is_active) values
    ('12000000-0000-0000-0000-000000000001', 'CONC-LOCAL', 'Concurrency Local', true);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'conc-sales@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Concurrency Sales"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'conc-cashier-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Concurrency Cashier A"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'conc-cashier-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Concurrency Cashier B"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = '12000000-0000-0000-0000-000000000001'
where id in (
    '22000000-0000-0000-0000-000000000001',
    '22000000-0000-0000-0000-000000000002',
    '22000000-0000-0000-0000-000000000003'
);

insert into public.user_roles (user_id, role_id)
select assigned.user_id, r.id
from (values
    ('22000000-0000-0000-0000-000000000001'::pg_catalog.uuid, 'SALES'),
    ('22000000-0000-0000-0000-000000000002'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000003'::pg_catalog.uuid, 'CASHIER')
) as assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.sales (
    id, folio, branch_id, subtotal_cents, total_cents, status, created_by, idempotency_key
) values
    ('52000000-0000-0000-0000-000000000001', 'VD-900001-CCL001', '12000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000001'),
    ('52000000-0000-0000-0000-000000000002', 'VD-900002-CDK002', '12000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000002'),
    ('52000000-0000-0000-0000-000000000003', 'VD-900003-CSK003', '12000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000003'),
    ('52000000-0000-0000-0000-000000000004', 'VD-900004-CEX004', '12000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000004'),
    ('52000000-0000-0000-0000-000000000005', 'VD-900005-CBL005', '12000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000005');

insert into public.sale_payment_claims (
    sale_id, cashier_id, branch_id, claim_token, created_at, expires_at
) values (
    '52000000-0000-0000-0000-000000000004',
    '22000000-0000-0000-0000-000000000002',
    '12000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000004',
    pg_catalog.clock_timestamp() - pg_catalog.make_interval(mins => 10),
    pg_catalog.clock_timestamp() - pg_catalog.make_interval(mins => 5)
);

insert into public.categories(id, name) values
    ('32000000-0000-4000-8000-000000000001', 'Concurrency fixtures');
insert into public.products(id, internal_code, common_name, category_id, price_cents, unit) values
    ('42000000-0000-4000-8000-000000000001', 'CONC-01', 'Concurrency plant',
     '32000000-0000-4000-8000-000000000001', 10000, 'pieza');
insert into public.sale_items(sale_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents)
select id, '42000000-0000-4000-8000-000000000001', 'Concurrency plant', 'CONC-01', 1, 10000, 10000
from public.sales where branch_id = '12000000-0000-0000-0000-000000000001';
insert into public.inventory_movements(branch_id, product_id, movement_type, quantity, created_by) values
    ('12000000-0000-0000-0000-000000000001', '42000000-0000-4000-8000-000000000001',
     'RECEPTION', 50, '22000000-0000-0000-0000-000000000001');
insert into public.branch_inventory_activation(branch_id, activated_by) values
    ('12000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000001');

commit;
