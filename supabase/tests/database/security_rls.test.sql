begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(66);

-- Explicit release allowlist. Keep signatures and client grants reviewed together.
create temporary table expected_public_functions (
    signature text primary key, anon_allowed boolean not null, authenticated_allowed boolean not null
) on commit drop;
insert into expected_public_functions values
    ('activate_my_inventory(p_initial_count_confirmed boolean)', false, true),
    ('apply_sale_discount(p_sale_id uuid, p_discount_cents bigint, p_reason text, p_promotion_id uuid)', false, true),
    ('assign_user_branch(p_user_id uuid, p_branch_id uuid)', false, true),
    ('assign_user_role(p_user_id uuid, p_role_name text)', false, true),
    ('bootstrap_first_owner(p_user_id uuid)', false, false),
    ('claim_sale_for_payment(p_sale_id uuid, p_claim_token uuid)', false, true),
    ('close_my_cashier(p_opening_cash_cents bigint, p_counted_cash_cents bigint, p_idempotency_key uuid)', false, true),
    ('confirm_newsletter_subscription(p_token uuid)', true, true),
    ('confirm_sale_payment(p_sale_id uuid, p_claim_token uuid, p_idempotency_key uuid, p_method payment_method, p_amount_received_cents bigint, p_reference text)', false, true),
    ('confirm_supplier_purchase(p_purchase_id uuid, p_confirmation_key uuid)', false, true),
    ('create_branch(p_code text, p_name text)', false, true),
    ('create_newsletter_campaign(p_id uuid, p_subject text, p_body text)', false, true),
    ('create_supplier_purchase_draft(p_supplier_id uuid, p_document_date date, p_external_reference text, p_payment_terms text, p_expected_total_cents bigint, p_source_file_name text, p_items jsonb, p_idempotency_key uuid)', false, true),
    ('enforce_product_price_permission()', false, false),
    ('get_admin_branches(p_limit integer, p_after_code text, p_after_id uuid, p_include_inactive boolean)', false, true),
    ('get_admin_inventory_balances(p_branch_id uuid, p_limit integer, p_after_product_id uuid, p_search text, p_include_zero_stock boolean)', false, true),
    ('get_admin_role_options()', false, true),
    ('get_admin_staff(p_limit integer, p_after_full_name text, p_after_id uuid, p_search text, p_branch_id uuid, p_include_inactive boolean)', false, true),
    ('get_admin_web_orders(p_limit integer, p_after_created_at timestamp with time zone, p_after_id uuid, p_status web_order_status)', false, true),
    ('get_cashier_payment_result(p_sale_id uuid, p_idempotency_key uuid)', false, true),
    ('get_cashier_sale_detail(p_sale_id uuid)', false, true),
    ('get_cashier_sales(p_limit integer, p_after_created_at timestamp with time zone, p_after_id uuid)', false, true),
    ('get_catalog_pricing()', false, true),
    ('get_low_inventory_alerts(p_branch_id uuid)', false, true),
    ('get_my_access_context()', false, true),
    ('get_my_branch_catalog_inventory()', false, true),
    ('get_my_cashier_closing_preview()', false, true),
    ('get_my_inventory_activation()', false, true),
    ('get_my_inventory_dashboard(p_limit integer, p_after_product_id uuid)', false, true),
    ('get_my_inventory_history(p_product_id uuid, p_limit integer, p_after_created_at timestamp with time zone, p_after_id uuid)', false, true),
    ('get_my_recent_sales(p_limit integer, p_after_created_at timestamp with time zone, p_after_id uuid)', false, true),
    ('get_my_supplier_purchases(p_status text, p_limit integer)', false, true),
    ('get_newsletter_campaigns()', false, true),
    ('get_product_by_scan_code(p_code text)', false, true),
    ('get_public_catalog_v2_base(p_search text, p_category_id uuid, p_limit integer, p_after_name text, p_after_id uuid)', false, false),
    ('get_public_catalog(p_search text, p_category_id uuid, p_limit integer, p_after_name text, p_after_id uuid)', true, true),
    ('get_public_web_order_options()', true, true),
    ('get_refundable_sale(p_folio text)', false, true),
    ('get_report_daily_sales(p_branch_id uuid, p_start_date date, p_end_date date)', false, true),
    ('get_report_top_products(p_branch_id uuid, p_limit integer)', false, true),
    ('get_supplier_purchase(p_purchase_id uuid)', false, true),
    ('guard_web_order_checkout()', false, false),
    ('handle_new_user()', false, false),
    ('has_permission(required_permission text)', false, true),
    ('prepare_newsletter_subscription(p_email text)', false, false),
    ('reconcile_inventory_count(p_product_id uuid, p_counted_quantity numeric, p_reason text, p_idempotency_key uuid, p_location_id uuid)', false, true),
    ('record_inventory_movement(p_product_id uuid, p_movement_type inventory_movement_type, p_quantity numeric, p_branch_id uuid, p_location_id uuid, p_notes text, p_reference_id uuid)', false, true),
    ('record_inventory_reception(p_product_id uuid, p_quantity numeric, p_notes text, p_idempotency_key uuid)', false, true),
    ('record_sale_inventory_movements()', false, false),
    ('refund_sale_in_person(p_folio text, p_reason text, p_method payment_method, p_restock boolean, p_money_returned boolean, p_idempotency_key uuid)', false, true),
    ('release_sale_payment_claim(p_sale_id uuid, p_claim_token uuid)', false, true),
    ('resolve_catalog_product_price(p_product_id uuid, p_at timestamp with time zone)', false, false),
    ('resolve_supplier_purchase_item(p_item_id uuid, p_resolution text, p_product_id uuid, p_normalized_name text, p_presentation text)', false, true),
    ('search_customers(p_query text, p_limit integer)', false, true),
    ('send_web_order_to_cashier(p_order_id uuid)', false, true),
    ('set_admin_staff_role(p_user_id uuid, p_role_name text)', false, true),
    ('set_admin_web_order_status(p_order_id uuid, p_status web_order_status, p_observation text)', false, true),
    ('set_branch_active(p_branch_id uuid, p_is_active boolean)', false, true),
    ('set_product_image_primary(p_image_id uuid)', false, true),
    ('set_supplier_presentation(p_supplier_id uuid, p_code text, p_display_name text, p_nominal_size numeric, p_size_unit text, p_notes text)', false, true),
    ('set_updated_at()', false, false),
    ('set_user_active(p_user_id uuid, p_is_active boolean)', false, true),
    ('submit_sale_to_cashier(p_sale_id uuid, p_folio text, p_items jsonb, p_customer_id uuid)', false, true),
    ('submit_web_order(p_order_id uuid, p_branch_id uuid, p_customer_name text, p_customer_phone text, p_customer_email text, p_notes text, p_items jsonb)', true, true),
    ('unsubscribe_newsletter(p_token uuid)', true, true),
    ('update_branch(p_branch_id uuid, p_code text, p_name text)', false, true),
    ('update_inventory_balance()', false, false),
    ('upsert_catalog_promotion(p_name text, p_description text, p_scope text, p_promo_type text, p_value numeric, p_min_purchase_cents bigint, p_max_discount_cents bigint, p_starts_at timestamp with time zone, p_ends_at timestamp with time zone, p_is_active boolean, p_product_ids uuid[], p_id uuid)', false, true),
    ('upsert_category(p_id uuid, p_name text, p_description text, p_is_active boolean)', false, true),
    ('upsert_customer(p_id uuid, p_full_name text, p_email text, p_phone text, p_is_active boolean)', false, true),
    ('upsert_product(p_id uuid, p_internal_code text, p_barcode text, p_common_name text, p_scientific_name text, p_description text, p_category_id uuid, p_price_cents bigint, p_wholesale_price_cents bigint, p_unit text, p_minimum_stock numeric, p_watering_advice text, p_light_type text, p_recommended_climate text, p_is_active boolean)', false, true),
    ('upsert_supplier(p_code text, p_name text, p_id uuid)', false, true);


insert into public.branches (id, code, name, is_active) values
    ('10000000-0000-0000-0000-000000000001', 'CENTRO', 'Sucursal Centro', true),
    ('10000000-0000-0000-0000-000000000002', 'NORTE', 'Sucursal Norte', true),
    ('10000000-0000-0000-0000-000000000003', 'PENDIENTE', 'Sucursal con venta pendiente', true),
    ('10000000-0000-0000-0000-000000000004', 'INACTIVA', 'Sucursal inactiva', false);

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
    ('50000000-0000-0000-0000-000000000004', 'VD-000004-DDDDDD', '10000000-0000-0000-0000-000000000002', 1500, 1500, 'SENT_TO_CASHIER', '20000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000004'),
    ('50000000-0000-0000-0000-000000000005', 'VD-000005-HHHHHH', '10000000-0000-0000-0000-000000000003', 1500, 1500, 'PAYMENT_PENDING', '20000000-0000-0000-0000-000000000007', '50000000-0000-0000-0000-000000000005');

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

select extensions.is(
    (select pg_catalog.count(*) from public.sale_status_history
     where sale_id = '60000000-0000-0000-0000-000000000001'),
    2::bigint,
    'initial sale submission creates exactly two legitimate history transitions'
);
select extensions.is(
    (select pg_catalog.count(*) from public.sale_status_history
     where sale_id = '60000000-0000-0000-0000-000000000001'
       and previous_status is null and new_status = 'DRAFT'),
    1::bigint,
    'initial sale history contains exactly one null to DRAFT transition'
);
select extensions.is(
    (select pg_catalog.count(*) from public.sale_status_history
     where sale_id = '60000000-0000-0000-0000-000000000001'
       and previous_status = 'DRAFT' and new_status = 'SENT_TO_CASHIER'),
    1::bigint,
    'initial sale history contains exactly one DRAFT to SENT_TO_CASHIER transition'
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

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.create_branch('VENTAS', 'Sucursal Ventas')$$,
    '42501',
    'Branch management is not allowed',
    'a user without MANAGE_BRANCHES cannot create branches'
);
select extensions.throws_ok(
    $$select public.update_branch('10000000-0000-0000-0000-000000000001', 'CENTRO', 'Centro cambiado')$$,
    '42501',
    'Branch management is not allowed',
    'a user without MANAGE_BRANCHES cannot modify branches'
);
select extensions.throws_ok(
    $$select public.assign_user_branch('20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000002')$$,
    '42501',
    'Branch assignment is not allowed',
    'a user without MANAGE_USERS cannot assign branches'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000006';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000006","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.assign_user_branch('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000002')$$,
    '42501',
    'Branch assignment is not allowed',
    'ADMIN cannot modify an OWNER branch assignment'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000007';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000007","role":"authenticated"}';
select extensions.lives_ok(
    $$select public.create_branch('  campo_01  ', '  Sucursal   Campo  ')$$,
    'OWNER can create a normalized branch'
);
select extensions.throws_ok(
    $$select public.create_branch('centro', 'Otra sucursal')$$,
    '23505',
    'Branch code is unavailable',
    'a duplicate normalized branch code is rejected'
);
select extensions.throws_ok(
    $$select public.create_branch('   ', 'Sucursal valida')$$,
    '22023',
    'Branch data is invalid',
    'an empty branch code is rejected'
);
select extensions.throws_ok(
    $$select public.create_branch('VALIDA', '   ')$$,
    '22023',
    'Branch data is invalid',
    'an empty branch name is rejected'
);
select extensions.lives_ok(
    $$select public.assign_user_branch(
        '20000000-0000-0000-0000-000000000007',
        (select id from public.branches where code = 'CAMPO_01')
    )$$,
    'OWNER can assign their own active branch'
);
select extensions.throws_ok(
    $$select public.assign_user_branch(
        '20000000-0000-0000-0000-000000000008',
        '10000000-0000-0000-0000-000000000004'
    )$$,
    '22023',
    'Target branch is unavailable',
    'an inactive branch cannot be assigned'
);
select extensions.throws_ok(
    $$select public.set_branch_active('10000000-0000-0000-0000-000000000001', false)$$,
    '55000',
    'Branch cannot be deactivated',
    'a branch with active personnel cannot be deactivated'
);
select extensions.throws_ok(
    $$select public.set_branch_active('10000000-0000-0000-0000-000000000003', false)$$,
    '55000',
    'Branch cannot be deactivated',
    'a branch with pending sales cannot be deactivated'
);
select extensions.lives_ok(
    $$select public.assign_user_branch(
        '20000000-0000-0000-0000-000000000007',
        (select id from public.branches where code = 'CAMPO_01')
    )$$,
    'an identical branch assignment is idempotent'
);
reset role;

select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_proc p
        cross join lateral pg_catalog.aclexplode(
            coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))
        ) acl
        where p.oid = any(array[
            'public.create_branch(text,text)'::regprocedure::oid,
            'public.update_branch(uuid,text,text)'::regprocedure::oid,
            'public.set_branch_active(uuid,boolean)'::regprocedure::oid,
            'public.assign_user_branch(uuid,uuid)'::regprocedure::oid
        ])
          and acl.grantee = 0
          and acl.privilege_type = 'EXECUTE'
    ),
    'PUBLIC cannot execute branch management RPCs'
);

select extensions.ok(
    (
        select pg_catalog.bool_and(
            not pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE')
        )
        from pg_catalog.pg_proc p
        where p.oid = any(array[
            'public.create_branch(text,text)'::regprocedure::oid,
            'public.update_branch(uuid,text,text)'::regprocedure::oid,
            'public.set_branch_active(uuid,boolean)'::regprocedure::oid,
            'public.assign_user_branch(uuid,uuid)'::regprocedure::oid
        ])
    ),
    'anon cannot execute branch management RPCs'
);

select extensions.ok(
    not pg_catalog.has_table_privilege('authenticated', 'public.branches', 'INSERT'),
    'authenticated has no direct INSERT privilege on branches'
);
select extensions.ok(
    not pg_catalog.has_table_privilege('authenticated', 'public.branches', 'UPDATE'),
    'authenticated has no general UPDATE privilege on branches'
);
select extensions.ok(
    not pg_catalog.has_table_privilege('authenticated', 'public.branches', 'DELETE'),
    'authenticated has no direct DELETE privilege on branches'
);
select extensions.ok(
    not pg_catalog.has_table_privilege('authenticated', 'public.profiles', 'UPDATE'),
    'authenticated has no general UPDATE privilege on profiles'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            not pg_catalog.has_column_privilege(
                'authenticated', 'public.profiles', protected.column_name, 'UPDATE'
            )
        )
        from (values
            ('id'::pg_catalog.text),
            ('branch_id'::pg_catalog.text),
            ('is_active'::pg_catalog.text),
            ('created_at'::pg_catalog.text),
            ('updated_at'::pg_catalog.text)
        ) as protected(column_name)
    ),
    'authenticated cannot update any protected profile column'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE')
        )
        from pg_catalog.pg_proc p
        where p.oid = any(array[
            'public.create_branch(text,text)'::regprocedure::oid,
            'public.update_branch(uuid,text,text)'::regprocedure::oid,
            'public.set_branch_active(uuid,boolean)'::regprocedure::oid,
            'public.assign_user_branch(uuid,uuid)'::regprocedure::oid
        ])
    ),
    'authenticated retains EXECUTE on all branch RPCs'
);
select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated', 'public.submit_sale_to_cashier(uuid,text,jsonb,uuid)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.submit_sale_to_cashier(uuid,text,jsonb,uuid)', 'EXECUTE'
    ),
    'submit_sale_to_cashier remains executable only by authenticated clients'
);
select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated', 'public.assign_user_role(uuid,text)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.assign_user_role(uuid,text)', 'EXECUTE'
    ),
    'assign_user_role remains executable only by authenticated clients'
);
select extensions.ok(
    pg_catalog.has_function_privilege(
        'service_role', 'public.bootstrap_first_owner(uuid)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'authenticated', 'public.bootstrap_first_owner(uuid)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.bootstrap_first_owner(uuid)', 'EXECUTE'
    ),
    'bootstrap_first_owner remains reserved for service_role'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            pg_catalog.has_table_privilege('authenticated', required.table_name, 'SELECT')
        )
        from (values
            ('public.branches'::pg_catalog.text),
            ('public.categories'::pg_catalog.text),
            ('public.permissions'::pg_catalog.text),
            ('public.product_images'::pg_catalog.text),
            ('public.products'::pg_catalog.text),
            ('public.profiles'::pg_catalog.text),
            ('public.role_permissions'::pg_catalog.text),
            ('public.roles'::pg_catalog.text),
            ('public.sale_items'::pg_catalog.text),
            ('public.sale_status_history'::pg_catalog.text),
            ('public.sales'::pg_catalog.text),
            ('public.user_roles'::pg_catalog.text)
        ) as required(table_name)
    ),
    'authenticated retains SELECT on the twelve client-readable RLS tables'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            not pg_catalog.has_table_privilege(
                'authenticated', protected.table_name, privilege.name
            )
        )
        from (values
            ('public.branches'::pg_catalog.text),
            ('public.permissions'::pg_catalog.text),
            ('public.profiles'::pg_catalog.text),
            ('public.role_permissions'::pg_catalog.text),
            ('public.roles'::pg_catalog.text),
            ('public.sale_payment_claims'::pg_catalog.text),
            ('public.sale_payments'::pg_catalog.text),
            ('public.sale_items'::pg_catalog.text),
            ('public.sale_status_history'::pg_catalog.text),
            ('public.sales'::pg_catalog.text),
            ('public.user_roles'::pg_catalog.text)
        ) as protected(table_name)
        cross join (values
            ('INSERT'::pg_catalog.text), ('UPDATE'::pg_catalog.text),
            ('DELETE'::pg_catalog.text), ('TRUNCATE'::pg_catalog.text),
            ('REFERENCES'::pg_catalog.text), ('TRIGGER'::pg_catalog.text)
        ) as privilege(name)
    ),
    'authenticated has no table-level writes on non-catalog application tables'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            case
                when privilege.name in ('INSERT', 'UPDATE', 'DELETE') then
                    pg_catalog.has_table_privilege(
                        'authenticated', catalog.table_name, privilege.name
                    )
                else
                    not pg_catalog.has_table_privilege(
                        'authenticated', catalog.table_name, privilege.name
                    )
            end
        )
        from (values
            ('public.categories'::pg_catalog.text),
            ('public.product_images'::pg_catalog.text),
            ('public.products'::pg_catalog.text)
        ) as catalog(table_name)
        cross join (values
            ('INSERT'::pg_catalog.text), ('UPDATE'::pg_catalog.text),
            ('DELETE'::pg_catalog.text), ('TRUNCATE'::pg_catalog.text),
            ('REFERENCES'::pg_catalog.text), ('TRIGGER'::pg_catalog.text)
        ) as privilege(name)
    ),
    'catalog tables retain only RLS-governed client writes'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            not pg_catalog.has_table_privilege('anon', target.table_name, privilege.name)
        )
        from (values
            ('public.branches'::pg_catalog.text), ('public.categories'::pg_catalog.text),
            ('public.permissions'::pg_catalog.text), ('public.product_images'::pg_catalog.text),
            ('public.products'::pg_catalog.text), ('public.profiles'::pg_catalog.text),
            ('public.role_permissions'::pg_catalog.text), ('public.roles'::pg_catalog.text),
            ('public.sale_payment_claims'::pg_catalog.text), ('public.sale_payments'::pg_catalog.text),
            ('public.sale_items'::pg_catalog.text), ('public.sale_status_history'::pg_catalog.text),
            ('public.sales'::pg_catalog.text), ('public.user_roles'::pg_catalog.text)
        ) as target(table_name)
        cross join (values
            ('SELECT'::pg_catalog.text), ('INSERT'::pg_catalog.text),
            ('UPDATE'::pg_catalog.text), ('DELETE'::pg_catalog.text),
            ('TRUNCATE'::pg_catalog.text), ('REFERENCES'::pg_catalog.text),
            ('TRIGGER'::pg_catalog.text)
        ) as privilege(name)
    ),
    'anon has no table privileges during the authenticated-only phase'
);
select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        cross join lateral pg_catalog.aclexplode(
            coalesce(c.relacl, pg_catalog.acldefault('r', c.relowner))
        ) acl
        where n.nspname = 'public'
          and c.relkind = 'r'
          and acl.grantee = 0
    ),
    'PUBLIC has no privileges on application tables'
);
select extensions.ok(
    (
        select pg_catalog.bool_and(
            pg_catalog.has_table_privilege('service_role', target.table_name, privilege.name)
        )
        from (values
            ('public.sale_payment_claims'::pg_catalog.text),
            ('public.sale_payments'::pg_catalog.text)
        ) as target(table_name)
        cross join (values
            ('SELECT'::pg_catalog.text), ('INSERT'::pg_catalog.text),
            ('UPDATE'::pg_catalog.text), ('DELETE'::pg_catalog.text),
            ('TRUNCATE'::pg_catalog.text), ('REFERENCES'::pg_catalog.text),
            ('TRIGGER'::pg_catalog.text)
        ) as privilege(name)
    ),
    'service_role has administrative privileges on the new payment tables'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.is(
    (select pg_catalog.count(*) from public.products),
    1::bigint,
    'SALES retains RLS-filtered catalog reads'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '20000000-0000-0000-0000-000000000007';
set local "request.jwt.claims" = '{"sub":"20000000-0000-0000-0000-000000000007","role":"authenticated"}';
select extensions.lives_ok(
    $$update public.categories
      set description = 'Actualizada por OWNER'
      where id = '30000000-0000-0000-0000-000000000001'$$,
    'OWNER retains catalog administration through RLS'
);
reset role;

select extensions.results_eq(
    $$
    select (p.proname || '(' || pg_catalog.pg_get_function_identity_arguments(p.oid) || ')') collate "C"
    from pg_catalog.pg_proc p
    join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
    order by 1
    $$,
    $$select signature collate "C" from expected_public_functions order by 1$$,
    'the public function inventory matches the reviewed release allowlist'
);

select extensions.results_eq(
    $$
    select (p.proname || '(' || pg_catalog.pg_get_function_identity_arguments(p.oid) || ')') collate "C"
    from pg_catalog.pg_proc p
    join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE')
    order by 1
    $$,
    $$select signature collate "C" from expected_public_functions where anon_allowed order by 1$$,
    'anon can execute exactly the reviewed public entry points'
);

select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_proc p
        join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        cross join lateral pg_catalog.aclexplode(
            coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))
        ) acl
        where n.nspname = 'public'
          and acl.grantee = 0
          and acl.privilege_type = 'EXECUTE'
    ),
    'PUBLIC cannot execute any public function'
);

select extensions.results_eq(
    $$
    select (p.proname || '(' || pg_catalog.pg_get_function_identity_arguments(p.oid) || ')') collate "C"
    from pg_catalog.pg_proc p
    join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE')
    order by 1
    $$,
    $$select signature collate "C" from expected_public_functions where authenticated_allowed order by 1$$,
    'authenticated can execute exactly the reviewed operational entry points'
);

select extensions.ok(
    pg_catalog.has_function_privilege(
        'service_role', 'public.claim_sale_for_payment(uuid,uuid)', 'EXECUTE'
    ) and pg_catalog.has_function_privilege(
        'service_role', 'public.release_sale_payment_claim(uuid,uuid)', 'EXECUTE'
    ) and pg_catalog.has_function_privilege(
        'service_role',
        'public.confirm_sale_payment(uuid,uuid,uuid,public.payment_method,bigint,text)',
        'EXECUTE'
    ),
    'service_role can execute the three payment RPCs'
);

select extensions.ok(
    (
        select pg_catalog.count(*) = 6
           and pg_catalog.bool_and(
               not pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE')
               and not pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE')
               and exists (
                   select 1 from pg_catalog.pg_trigger t where t.tgfoid = p.oid
               )
           )
        from pg_catalog.pg_proc p
        join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and p.prorettype = 'pg_catalog.trigger'::pg_catalog.regtype
    ),
    'trigger functions remain bound to triggers but are not client-invocable'
);

select * from extensions.finish();
rollback;
