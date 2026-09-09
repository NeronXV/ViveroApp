begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(25);

select extensions.has_table('public', 'web_orders', 'web orders table exists');
select extensions.has_table('public', 'web_order_items', 'web order items table exists');
select extensions.has_table('public', 'web_order_status_history', 'web order status history exists');
select extensions.has_type('public', 'web_order_status', 'web order status type exists');

select extensions.ok(
    (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.web_orders'::pg_catalog.regclass)
    and (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.web_order_items'::pg_catalog.regclass)
    and (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.web_order_status_history'::pg_catalog.regclass),
    'all web order tables have RLS enabled'
);

select extensions.ok(
    not pg_catalog.has_table_privilege('anon', 'public.web_orders', 'SELECT')
    and not pg_catalog.has_table_privilege('authenticated', 'public.web_orders', 'SELECT'),
    'clients have no direct web order table access'
);

select extensions.ok(
    pg_catalog.has_function_privilege('anon', 'public.get_public_web_order_options()', 'EXECUTE')
    and pg_catalog.has_function_privilege(
        'anon',
        'public.submit_web_order(uuid,uuid,text,text,text,text,jsonb)',
        'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon',
        'public.get_admin_web_orders(integer,timestamp with time zone,uuid,public.web_order_status)',
        'EXECUTE'
    ),
    'public and administrative RPC grants are separated'
);

select extensions.ok(
    (
        select p.prosecdef and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.submit_web_order(uuid,uuid,text,text,text,text,jsonb)'::pg_catalog.regprocedure
    ),
    'public submission RPC is security definer with a fixed search path'
);

insert into public.branches(id, code, name, is_active) values
    ('91000000-0000-4000-8000-000000000001', 'WEB-A', 'Sucursal Web A', true),
    ('91000000-0000-4000-8000-000000000002', 'WEB-B', 'Sucursal Web B', true);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'manager-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Gerencia Web A"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'manager-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Gerencia Web B"}', pg_catalog.now(), pg_catalog.now());

update public.profiles set branch_id = '91000000-0000-4000-8000-000000000001'
where id = '92000000-0000-4000-8000-000000000001';
update public.profiles set branch_id = '91000000-0000-4000-8000-000000000002'
where id = '92000000-0000-4000-8000-000000000002';

insert into public.user_roles(user_id, role_id)
select assigned.user_id, r.id
from (values
    ('92000000-0000-4000-8000-000000000001'::uuid, 'MANAGER'),
    ('92000000-0000-4000-8000-000000000002'::uuid, 'MANAGER')
) assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.categories(id, name, is_active) values
    ('93000000-0000-4000-8000-000000000001', 'Pedidos Web', true);

insert into public.products(
    id, internal_code, common_name, category_id, price_cents, unit, is_active
) values (
    '94000000-0000-4000-8000-000000000001', 'WEB-PLANT', 'Planta Web',
    '93000000-0000-4000-8000-000000000001', 5000, 'pieza', true
);

set local role anon;

select extensions.is(
    public.get_public_web_order_options()#>>'{schemaVersion}',
    '1',
    'public checkout options are available anonymously'
);

select extensions.throws_ok(
    $$
        select public.submit_web_order(
            '95000000-0000-4000-8000-000000000001',
            '91000000-0000-4000-8000-000000000001',
            'Cliente Web', '', '', '',
            '[{"product_id":"94000000-0000-4000-8000-000000000001","quantity":1}]'::jsonb
        )
    $$,
    '22023', 'WEB_ORDER_CUSTOMER_INVALID',
    'public order requires a valid contact method'
);

select extensions.lives_ok(
    $$
        select public.submit_web_order(
            '95000000-0000-4000-8000-000000000002',
            '91000000-0000-4000-8000-000000000001',
            'Cliente Web', '614 123 4567', '', 'Pedido sintético',
            '[{"product_id":"94000000-0000-4000-8000-000000000001","quantity":2}]'::jsonb
        )
    $$,
    'anonymous checkout accepts a valid synthetic order'
);

select extensions.is(
    (
        public.submit_web_order(
            '95000000-0000-4000-8000-000000000002',
            '91000000-0000-4000-8000-000000000001',
            'Cliente Web', '614 123 4567', '', 'Pedido sintético',
            '[{"product_id":"94000000-0000-4000-8000-000000000001","quantity":2}]'::jsonb
        )#>>'{idempotentReplay}'
    )::boolean,
    true,
    'repeating the same order key returns an idempotent confirmation'
);

reset role;

select extensions.is(
    (select pg_catalog.count(*) from public.web_orders where id = '95000000-0000-4000-8000-000000000002'),
    1::bigint,
    'idempotent submission persists one order'
);

select extensions.ok(
    (
        select o.status = 'PENDING' and o.subtotal_cents = 10000 and o.total_cents = 10000
        from public.web_orders o where o.id = '95000000-0000-4000-8000-000000000002'
    ),
    'order totals and initial status are server authoritative'
);

select extensions.ok(
    (
        select i.quantity = 2 and i.unit_price_cents = 5000 and i.line_total_cents = 10000
        from public.web_order_items i where i.order_id = '95000000-0000-4000-8000-000000000002'
    ),
    'order items snapshot authoritative product prices'
);

select extensions.is(
    (select pg_catalog.count(*) from public.web_order_status_history where order_id = '95000000-0000-4000-8000-000000000002'),
    1::bigint,
    'initial order status is audited'
);

select extensions.throws_ok(
    $$select public.get_admin_web_orders()$$,
    '42501', 'WEB_ORDER_ADMIN_UNAUTHORIZED',
    'unauthenticated callers cannot list customer orders'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '92000000-0000-4000-8000-000000000001';
set local "request.jwt.claims" = '{"sub":"92000000-0000-4000-8000-000000000001","role":"authenticated"}';

select extensions.is(
    pg_catalog.jsonb_array_length(public.get_admin_web_orders()->'items'),
    1,
    'branch manager lists orders from the assigned branch'
);

select extensions.lives_ok(
    $$
        select public.set_admin_web_order_status(
            '95000000-0000-4000-8000-000000000002', 'CONFIRMED', null
        )
    $$,
    'branch manager confirms an assigned order'
);

select extensions.throws_ok(
    $$
        select public.set_admin_web_order_status(
            '95000000-0000-4000-8000-000000000002', 'COMPLETED', null
        )
    $$,
    '22023', 'WEB_ORDER_STATUS_INVALID',
    'status transitions cannot skip operational steps'
);

reset role;

select extensions.ok(
    (
        select o.status = 'CONFIRMED' from public.web_orders o
        where o.id = '95000000-0000-4000-8000-000000000002'
    )
    and (
        select pg_catalog.count(*) = 2 from public.web_order_status_history h
        where h.order_id = '95000000-0000-4000-8000-000000000002'
    ),
    'authorized status change is persisted and audited'
);


set local role authenticated;
set local request.jwt.claims='{"sub":"92000000-0000-4000-8000-000000000001","role":"authenticated"}';
select extensions.is((public.send_web_order_to_cashier('95000000-0000-4000-8000-000000000002')->>'totalCents')::bigint,10000::bigint,'web order becomes cashier sale at accepted price');
select extensions.is((public.send_web_order_to_cashier('95000000-0000-4000-8000-000000000002')->>'idempotentReplay')::boolean,true,'sending order twice does not create a second sale');
select public.set_admin_web_order_status('95000000-0000-4000-8000-000000000002','READY',null);
select extensions.throws_ok(
 $$select public.set_admin_web_order_status('95000000-0000-4000-8000-000000000002','COMPLETED',null)$$,
 'P0001','WEB_ORDER_PAYMENT_REQUIRED','unpaid web order cannot be completed');
select extensions.lives_ok(
 $$select public.set_admin_web_order_status('95000000-0000-4000-8000-000000000002','CANCELLED',null)$$,
 'unclaimed unpaid checkout can be cancelled');
reset role;
select * from extensions.finish();

rollback;
