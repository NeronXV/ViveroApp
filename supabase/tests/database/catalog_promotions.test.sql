begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(20);

select extensions.has_column(
    'public', 'promotions', 'scope',
    'promotions declares its application scope'
);

select extensions.has_table(
    'public', 'promotion_products',
    'catalog promotion product bindings exist'
);

select extensions.ok(
    (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.promotion_products'::pg_catalog.regclass),
    'promotion product bindings have RLS enabled'
);

select extensions.ok(
    (
        select p.prosecdef
           and p.provolatile = 's'
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.resolve_catalog_product_price(uuid,timestamptz)'::pg_catalog.regprocedure
    ),
    'authoritative product price resolver is stable and hardened'
);

select extensions.ok(
    (
        select p.prosecdef
           and p.provolatile = 's'
           and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.get_catalog_pricing()'::pg_catalog.regprocedure
    ),
    'authenticated catalog pricing RPC is stable and hardened'
);

select extensions.ok(
    (
        select p.prosecdef
           and p.provolatile = 'v'
           and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.upsert_catalog_promotion(text,text,text,text,numeric,bigint,bigint,timestamptz,timestamptz,boolean,uuid[],uuid)'::pg_catalog.regprocedure
    ),
    'catalog promotion administration RPC is hardened'
);

select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.get_catalog_pricing()', 'EXECUTE')
    and not pg_catalog.has_function_privilege(
        'anon',
        'public.upsert_catalog_promotion(text,text,text,text,numeric,bigint,bigint,timestamptz,timestamptz,boolean,uuid[],uuid)',
        'EXECUTE'
    )
    and pg_catalog.has_function_privilege('authenticated', 'public.get_catalog_pricing()', 'EXECUTE')
    and pg_catalog.has_function_privilege(
        'authenticated',
        'public.upsert_catalog_promotion(text,text,text,text,numeric,bigint,bigint,timestamptz,timestamptz,boolean,uuid[],uuid)',
        'EXECUTE'
    ),
    'catalog promotion RPC execution follows client role boundaries'
);

insert into public.branches(id, code, name, is_active) values
    ('81000000-0000-0000-0000-000000000001', 'PROMO', 'Sucursal promociones', true);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '82000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'sales-promos@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Promos"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '82000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'manager-promos@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Gerencia Promos"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = '81000000-0000-0000-0000-000000000001'
where id in (
    '82000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000002'
);

insert into public.user_roles(user_id, role_id)
select assigned.user_id, r.id
from (values
    ('82000000-0000-0000-0000-000000000001'::uuid, 'SALES'),
    ('82000000-0000-0000-0000-000000000002'::uuid, 'MANAGER')
) assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.categories(id, name, is_active) values
    ('83000000-0000-0000-0000-000000000001', 'Promociones', true);

insert into public.products(
    id, internal_code, common_name, category_id, price_cents, unit, is_active
) values
    ('84000000-0000-0000-0000-000000000001', 'PROMO-A', 'A producto seleccionado', '83000000-0000-0000-0000-000000000001', 10000, 'pieza', true),
    ('84000000-0000-0000-0000-000000000002', 'PROMO-B', 'B producto general', '83000000-0000-0000-0000-000000000001', 10000, 'pieza', true);

insert into public.promotions(
    id, name, is_active, promo_type, value, min_purchase_cents, scope
) values
    ('85000000-0000-0000-0000-000000000001', 'Diez por ciento general', true, 'PERCENTAGE', 10, 0, 'ALL_PRODUCTS'),
    ('85000000-0000-0000-0000-000000000002', 'Dos mil quinientos seleccionado', true, 'FIXED_AMOUNT', 2500, 0, 'SELECTED_PRODUCTS');

insert into public.promotion_products(promotion_id, product_id) values
    ('85000000-0000-0000-0000-000000000002', '84000000-0000-0000-0000-000000000001');

select extensions.results_eq(
    $$
        select list_price_cents, effective_price_cents, promotion_id, promotion_name, discount_percent
        from public.resolve_catalog_product_price('84000000-0000-0000-0000-000000000001')
    $$,
    $$
        values (
            10000::bigint,
            7500::bigint,
            '85000000-0000-0000-0000-000000000002'::uuid,
            'Dos mil quinientos seleccionado'::text,
            25.00::numeric
        )
    $$,
    'the promotion producing the lowest effective price wins for a selected product'
);

select extensions.results_eq(
    $$
        select list_price_cents, effective_price_cents, promotion_id, promotion_name, discount_percent
        from public.resolve_catalog_product_price('84000000-0000-0000-0000-000000000002')
    $$,
    $$
        values (
            10000::bigint,
            9000::bigint,
            '85000000-0000-0000-0000-000000000001'::uuid,
            'Diez por ciento general'::text,
            10.00::numeric
        )
    $$,
    'an all-products campaign applies to products without explicit bindings'
);

select extensions.ok(
    public.get_public_catalog()->>'schemaVersion' = '3'
    and public.get_public_catalog()#>>'{items,0,price,amountCents}' = '7500'
    and public.get_public_catalog()#>>'{items,0,price,originalAmountCents}' = '10000'
    and public.get_public_catalog()#>>'{items,0,activePromotion,id}' = '85000000-0000-0000-0000-000000000002',
    'public catalog exposes the authoritative V3 promotion contract'
);

select extensions.throws_ok(
    $$select public.get_catalog_pricing()$$,
    '42501',
    'Catalog pricing is not allowed',
    'catalog pricing rejects unauthenticated callers'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '82000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"82000000-0000-0000-0000-000000000001","role":"authenticated"}';

select extensions.ok(
    public.get_catalog_pricing()#>>'{items,0,effectivePriceCents}' = '7500'
    and public.get_catalog_pricing()#>>'{items,0,activePromotion,name}' = 'Dos mil quinientos seleccionado',
    'authorized Android catalog pricing uses the same server calculation'
);

select extensions.throws_ok(
    $$
        select public.upsert_catalog_promotion(
            'No autorizada', null, 'ALL_PRODUCTS', 'PERCENTAGE', 5,
            0, null, null, null, true, array[]::uuid[]
        )
    $$,
    '42501',
    'Promotion management is not allowed',
    'a sales user cannot manage catalog promotions'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '82000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"82000000-0000-0000-0000-000000000002","role":"authenticated"}';

select extensions.lives_ok(
    $$
        select public.upsert_catalog_promotion(
            'Campaña administrada', 'Creada mediante RPC', 'SELECTED_PRODUCTS',
            'PERCENTAGE', 5, 0, null, null, null, false,
            array['84000000-0000-0000-0000-000000000002'::uuid],
            '85000000-0000-0000-0000-000000000003'
        )
    $$,
    'a discount manager can create a selected-products campaign'
);
reset role;

select extensions.is(
    (
        select pg_catalog.count(*)
        from public.promotion_products pp
        where pp.promotion_id = '85000000-0000-0000-0000-000000000003'
          and pp.product_id = '84000000-0000-0000-0000-000000000002'
          and pp.is_active
    ),
    1::bigint,
    'administrative RPC persists the selected product binding'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '82000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"82000000-0000-0000-0000-000000000001","role":"authenticated"}';

select extensions.lives_ok(
    $$
        select public.submit_sale_to_cashier(
            '86000000-0000-0000-0000-000000000001',
            'VD-PROMO-000001',
            '[{"product_id":"84000000-0000-0000-0000-000000000001","quantity":2}]'::jsonb
        )
    $$,
    'sale submission accepts product IDs and prices the campaign on the server'
);
reset role;

insert into public.sale_payment_claims(
    id, sale_id, cashier_id, branch_id, expires_at, consumed_at, closed_reason
) values (
    '87000000-0000-0000-0000-000000000001',
    '86000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000001',
    pg_catalog.now() + interval '5 minutes',
    pg_catalog.now(),
    'CONFIRMED'
);

insert into public.sale_payments(
    sale_id, branch_id, cashier_id, claim_id, idempotency_key, method,
    amount_due_cents, amount_received_cents, change_cents
) values (
    '86000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000002',
    '87000000-0000-0000-0000-000000000001',
    '88000000-0000-0000-0000-000000000001',
    'CARD',
    15000,
    15000,
    0
);

select extensions.ok(
    (
        select s.subtotal_cents = 15000
           and s.discount_cents = 0
           and s.total_cents = 15000
        from public.sales s
        where s.id = '86000000-0000-0000-0000-000000000001'
    )
    and (
        select si.list_price_cents = 10000
           and si.unit_price_cents = 7500
           and si.discount_cents = 2500
           and si.promotion_id = '85000000-0000-0000-0000-000000000002'
           and si.promotion_name = 'Dos mil quinientos seleccionado'
        from public.sale_items si
        where si.sale_id = '86000000-0000-0000-0000-000000000001'
    ),
    'submitted sale snapshots authoritative list, effective, discount, and campaign values'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '82000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"82000000-0000-0000-0000-000000000002","role":"authenticated"}';

select extensions.is(
    (
        public.get_report_daily_sales(
            '81000000-0000-0000-0000-000000000001',
            pg_catalog.current_date,
            pg_catalog.current_date
        )#>>'{0,discountCents}'
    )::bigint,
    5000::bigint,
    'daily sales reports include authoritative product promotion discounts'
);

select extensions.lives_ok(
    $$select public.get_cashier_sale_detail('86000000-0000-0000-0000-000000000001')$$,
    'cashier Web detail accepts a sale containing authoritative product prices'
);

select extensions.throws_ok(
    $$
        select public.apply_sale_discount(
            '86000000-0000-0000-0000-000000000001',
            1500,
            'No reutilizar campaña de producto',
            '85000000-0000-0000-0000-000000000002'
        )
    $$,
    '22023',
    'Promotion is not applicable',
    'a product campaign cannot be reused as a whole-sale discount'
);
reset role;

select * from extensions.finish();

rollback;
