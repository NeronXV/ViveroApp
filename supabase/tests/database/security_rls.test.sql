begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(25);

insert into public.branches (id, code, name) values
    ('10000000-0000-0000-0000-000000000001', 'CENTRO', 'Sucursal Centro'),
    ('10000000-0000-0000-0000-000000000002', 'NORTE', 'Sucursal Norte');

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'sales-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Centro"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'sales-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Norte"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'cashier-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Centro"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'cashier-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Norte"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'manager-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Gerencia Centro"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'admin@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Administracion"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000007', 'authenticated', 'authenticated', 'owner@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Propietario"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000008', 'authenticated', 'authenticated', 'extra@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Usuario Extra"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = case
    when id in (
        '20000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000004'
    ) then '10000000-0000-0000-0000-000000000002'::uuid
    else '10000000-0000-0000-0000-000000000001'::uuid
end;

insert into public.user_roles (user_id, role_id)
select assigned.user_id, r.id
from (values
    ('20000000-0000-0000-0000-000000000001'::uuid, 'SALES'),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'SALES'),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'CASHIER'),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'CASHIER'),
    ('20000000-0000-0000-0000-000000000005'::uuid, 'MANAGER'),
    ('20000000-0000-0000-0000-000000000006'::uuid, 'ADMIN'),
    ('20000000-0000-0000-0000-000000000007'::uuid, 'OWNER'),
    ('20000000-0000-0000-0000-000000000008'::uuid, 'SALES')
) as assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.categories (id, name) values
    ('30000000-0000-0000-0000-000000000001', 'Pruebas');

insert into public.products (
    id, internal_code, common_name, category_id, price_cents, unit, is_active
) values
    ('40000000-0000-0000-0000-000000000001', 'TEST-001', 'Producto activo', '30000000-0000-0000-0000-000000000001', 1500, 'pieza', true),
    ('40000000-0000-0000-0000-000000000002', 'TEST-002', 'Producto inactivo', '30000000-0000-0000-0000-000000000001', 2000, 'pieza', false);

insert into public.sales (
    id, folio, branch_id, subtotal_cents, total_cents, status, created_by, idempotency_key
) values
    ('50000000-0000-0000-0000-000000000001', 'VD-000001-AAAAAA', '10000000-0000-0000-0000-000000000001', 1500, 1500, 'SENT_TO_CASHIER', '20000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001'),
    ('50000000-0000-0000-0000-000000000002', 'VD-000002-BBBBBB', '10000000-0000-0000-0000-000000000001', 1500, 1500, 'PAYMENT_PENDING', '20000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002'),
    ('50000000-0000-0000-0000-000000000003', 'VD-000003-CCCCCC', '10000000-0000-0000-0000-000000000001', 1500, 1500, 'PAID', '20000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000003'),
    ('50000000-0000-0000-0000-000000000004', 'VD-000004-DDDDDD', '10000000-0000-0000-0000-000000000002', 1500, 1500, 'SENT_TO_CASHIER', '20000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000004');

select extensions.results_eq(
    $$select name from public.roles order by id$$,
    $$values ('SALES'::text), ('CASHIER'), ('INVENTORY'), ('MANAGER'), ('ADMIN'), ('OWNER')$$,
    'exactly six roles are seeded in the expected order'
);

select extensions.is(
    (select pg_catalog.count(*) from public.permissions),
    18::bigint,
    'the shared capability catalog has 18 entries'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.ok(public.has_permission('CREATE_SALES'), 'SALES can create sales');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000003';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000003","role":"authenticated"}';
select extensions.ok(not public.has_permission('ASSIGN_ROLES'), 'CASHIER cannot assign roles');
reset role;

select extensions.ok(
    not exists (
        select 1
        from public.role_permissions rp
        join public.roles r on r.id = rp.role_id
        where r.name = 'INVENTORY' and rp.permission_name = 'MANAGE_PRICES'
    ),
    'INVENTORY cannot manage prices'
);

select extensions.ok(
    not exists (
        select 1
        from public.role_permissions rp
        join public.roles r on r.id = rp.role_id
        where r.name = 'MANAGER' and rp.permission_name = 'VIEW_ALL_SALES'
    ),
    'MANAGER is not granted cross-branch sales access'
);

select extensions.is(
    (select pg_catalog.count(*) from public.role_permissions rp join public.roles r on r.id = rp.role_id where r.name = 'ADMIN'),
    18::bigint,
    'ADMIN has every declared capability'
);

select extensions.is(
    (select pg_catalog.count(*) from public.role_permissions rp join public.roles r on r.id = rp.role_id where r.name = 'OWNER'),
    18::bigint,
    'OWNER has every declared capability'
);

select extensions.ok(
    pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'full_name', 'UPDATE'),
    'authenticated can update full_name'
);
select extensions.ok(
    pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'avatar_path', 'UPDATE'),
    'authenticated can update avatar_path'
);
select extensions.ok(
    not pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'branch_id', 'UPDATE'),
    'authenticated cannot update branch_id'
);
select extensions.ok(
    not pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'is_active', 'UPDATE'),
    'authenticated cannot update is_active'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.throws_ok(
    $$update public.profiles set branch_id = '10000000-0000-0000-0000-000000000002' where id = auth.uid()$$,
    '42501',
    null,
    'a user cannot move their own profile to another branch'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000003';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000003","role":"authenticated"}';
select extensions.is(
    (select pg_catalog.count(*) from public.sales),
    2::bigint,
    'cashier sees only pending states from their branch'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000004';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000004","role":"authenticated"}';
select extensions.is(
    (select pg_catalog.count(*) from public.sales),
    1::bigint,
    'cashier cannot see pending sales from another branch'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.is(
    (select pg_catalog.count(*) from public.sales),
    3::bigint,
    'SALES sees every sale created by the same user and no others'
);

select extensions.lives_ok(
    $$select public.submit_sale_to_cashier(
        '60000000-0000-0000-0000-000000000001',
        'VD-000005-EEEEEE',
        '[{"product_id":"40000000-0000-0000-0000-000000000001","quantity":2}]'::jsonb
    )$$,
    'a valid complete ticket is submitted'
);

select extensions.lives_ok(
    $$select public.submit_sale_to_cashier(
        '60000000-0000-0000-0000-000000000001',
        'VD-999999-ZZZZZZ',
        '[]'::jsonb
    )$$,
    'the same caller receives the original idempotent result'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000002","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.submit_sale_to_cashier(
        '60000000-0000-0000-0000-000000000001',
        'VD-000006-FFFFFF',
        '[{"product_id":"40000000-0000-0000-0000-000000000001","quantity":1}]'::jsonb
    )$$,
    '42501',
    'Idempotency key is unavailable',
    'an idempotency collision from another user is rejected without sale data'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.submit_sale_to_cashier(
        '60000000-0000-0000-0000-000000000002',
        'VD-000007-GGGGGG',
        '[{"product_id":"40000000-0000-0000-0000-000000000001","quantity":1},{"product_id":"40000000-0000-0000-0000-000000000002","quantity":1}]'::jsonb
    )$$,
    '22023',
    'Sale items are invalid',
    'one invalid item rejects the complete ticket'
);
reset role;

select extensions.is(
    (select pg_catalog.count(*) from public.sales where id = '60000000-0000-0000-0000-000000000002'),
    0::bigint,
    'a partially invalid ticket leaves no sale row'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000006';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000006","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.assign_user_role('20000000-0000-0000-0000-000000000008', 'OWNER')$$,
    '42501',
    'ADMIN cannot grant or modify OWNER',
    'ADMIN cannot promote another user to OWNER'
);
select extensions.throws_ok(
    $$select public.assign_user_role('20000000-0000-0000-0000-000000000007', 'MANAGER')$$,
    '42501',
    'ADMIN cannot grant or modify OWNER',
    'ADMIN cannot modify an existing OWNER'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000007';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000007","role":"authenticated"}';
select extensions.lives_ok(
    $$select public.assign_user_role('20000000-0000-0000-0000-000000000008', 'ADMIN')$$,
    'OWNER can delegate an allowed role'
);
select extensions.throws_ok(
    $$select public.assign_user_role('20000000-0000-0000-0000-000000000007', 'MANAGER')$$,
    '42501',
    'The last OWNER cannot be reassigned',
    'the final OWNER cannot be removed accidentally'
);
reset role;

select * from extensions.finish();
rollback;
