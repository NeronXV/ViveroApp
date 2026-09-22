begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(23);

-- Fixtures canónicos sintéticos
insert into public.branches (id, code, name, is_active) values
    ('b1111111-1111-4111-8111-111111111111', 'B1', 'Sucursal 1', true),
    ('b2222222-2222-4222-8222-222222222222', 'B2', 'Sucursal 2', true);

insert into auth.users (
    id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data
) values
    ('a1111111-1111-4111-8111-111111111111', 's1@test.inv', '', now(), '{}', '{"full_name":"Seller 1"}'),
    ('a2222222-2222-4222-8222-222222222222', 's2@test.inv', '', now(), '{}', '{"full_name":"Seller 2"}'),
    ('a3333333-3333-4333-8333-333333333333', 'in@test.inv', '', now(), '{}', '{"full_name":"Inactive"}'),
    ('a4444444-4444-4444-8444-444444444444', 'no@test.inv', '', now(), '{}', '{"full_name":"No Permission"}');

insert into public.profiles (id, full_name, branch_id, is_active) values
    ('a1111111-1111-4111-8111-111111111111', 'Seller 1', 'b1111111-1111-4111-8111-111111111111', true),
    ('a2222222-2222-4222-8222-222222222222', 'Seller 2', 'b2222222-2222-4222-8222-222222222222', true),
    ('a3333333-3333-4333-8333-333333333333', 'Inactive', 'b1111111-1111-4111-8111-111111111111', false),
    ('a4444444-4444-4444-8444-444444444444', 'No Permission', 'b1111111-1111-4111-8111-111111111111', true)
on conflict (id) do update set
    branch_id = excluded.branch_id,
    is_active = excluded.is_active;

insert into public.user_roles (user_id, role_id)
select u.id, r.id from auth.users u, public.roles r where u.email in ('s1@test.inv', 's2@test.inv') and r.name = 'SALES';

insert into public.categories (id, name) values ('c1111111-1111-4111-8111-111111111111', 'Cat 1');
insert into public.products (id, internal_code, common_name, category_id, price_cents, unit)
values ('d1111111-1111-4111-8111-111111111111', 'P1', 'Prod 1', 'c1111111-1111-4111-8111-111111111111', 1000, 'pieza');

insert into public.sales (
    id, folio, branch_id, subtotal_cents, discount_cents, total_cents, status, created_by, idempotency_key, created_at
) values
    ('e1111111-1111-4111-8111-111111111111', 'VD-260829-000001', 'b1111111-1111-4111-8111-111111111111', 1000, 0, 1000, 'PAID', 'a1111111-1111-4111-8111-111111111111', 'f1111111-1111-4111-8111-111111111111', now() - interval '2 minutes'),
    ('e2222222-2222-4222-8222-222222222222', 'VD-260829-000002', 'b1111111-1111-4111-8111-111111111111', 2000, 200, 1800, 'SENT_TO_CASHIER', 'a1111111-1111-4111-8111-111111111111', 'f2222222-2222-4222-8222-222222222222', now() - interval '1 minute'),
    ('e3333333-3333-4333-8333-333333333333', 'VD-260829-000003', 'b2222222-2222-4222-8222-222222222222', 3000, 0, 3000, 'SENT_TO_CASHIER', 'a2222222-2222-4222-8222-222222222222', 'f3333333-3333-4333-8333-333333333333', now());

insert into public.sale_items (sale_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents)
values ('e1111111-1111-4111-8111-111111111111', 'd1111111-1111-4111-8111-111111111111', 'Prod 1', 'P1', 1, 1000, 1000);

insert into public.sale_payment_claims (id, sale_id, cashier_id, branch_id, expires_at, consumed_at, closed_reason)
values ('c2222222-2222-4222-8222-222222222222', 'e1111111-1111-4111-8111-111111111111', 'a1111111-1111-4111-8111-111111111111', 'b1111111-1111-4111-8111-111111111111', now() + interval '5 minutes', now(), 'CONFIRMED');

insert into public.sale_payments (sale_id, branch_id, cashier_id, claim_id, idempotency_key, method, amount_due_cents, requested_amount_received_cents, amount_received_cents, change_cents, created_at)
values ('e1111111-1111-4111-8111-111111111111', 'b1111111-1111-4111-8111-111111111111', 'a1111111-1111-4111-8111-111111111111', 'c2222222-2222-4222-8222-222222222222', 'f4444444-4444-4444-8444-444444444444', 'CASH', 1000, 1000, 1000, 0, now());

-- 1. Firma
select extensions.has_function('public', 'get_my_recent_sales', array['integer', 'timestamptz', 'uuid'], 'function exists');

-- 2. Propietario
select extensions.is(
    (select pg_catalog.pg_get_userbyid(p.proowner) from pg_catalog.pg_proc p where p.oid = 'public.get_my_recent_sales(integer,timestamptz,uuid)'::regprocedure),
    'postgres', 'owned by postgres'
);

-- 3. Search path
select extensions.is(
    (select p.proconfig from pg_catalog.pg_proc p where p.oid = 'public.get_my_recent_sales(integer,timestamptz,uuid)'::regprocedure),
    array['search_path=""'], 'empty search_path'
);

-- 4. Grants
select extensions.ok(pg_catalog.has_function_privilege('authenticated', 'public.get_my_recent_sales(integer,timestamptz,uuid)', 'EXECUTE'), 'authenticated can execute');
select extensions.ok(not pg_catalog.has_function_privilege('anon', 'public.get_my_recent_sales(integer,timestamptz,uuid)', 'EXECUTE'), 'anon cannot execute');
select extensions.ok(not pg_catalog.has_function_privilege('service_role', 'public.get_my_recent_sales(integer,timestamptz,uuid)', 'EXECUTE'), 'service_role cannot execute');

-- 5. Autorizacion
set local role authenticated;
set local "request.jwt.claim.sub" = 'a3333333-3333-4333-8333-333333333333'; -- Inactive
select extensions.throws_ok($$select public.get_my_recent_sales()$$, 'P0001', 'MY_SALES_UNAUTHORIZED', 'inactive profile rejected');

set local "request.jwt.claim.sub" = 'a4444444-4444-4444-8444-444444444444'; -- No permission
select extensions.throws_ok($$select public.get_my_recent_sales()$$, 'P0001', 'MY_SALES_UNAUTHORIZED', 'profile without VIEW_OWN_SALES rejected');

-- 6. Aislamiento
set local "request.jwt.claim.sub" = 'a1111111-1111-4111-8111-111111111111';
select extensions.is(pg_catalog.jsonb_array_length(public.get_my_recent_sales() -> 'items'), 2, 'only own sales returned');

-- 7. Limites
select extensions.is((public.get_my_recent_sales(1) -> 'page' ->> 'limit')::int, 1, 'limit respected');
select extensions.throws_ok($$select public.get_my_recent_sales(0)$$, 'P0001', 'MY_SALES_QUERY_INVALID', 'limit 0 rejected');
select extensions.throws_ok($$select public.get_my_recent_sales(51)$$, 'P0001', 'MY_SALES_QUERY_INVALID', 'limit 51 rejected');

-- 8. Cursor parcial
select extensions.throws_ok($$select public.get_my_recent_sales(20, now())$$, 'P0001', 'MY_SALES_QUERY_INVALID', 'partial cursor rejected');

-- 9. Orden estable (DESC)
select extensions.is(
    (public.get_my_recent_sales() -> 'items' -> 0 ->> 'folio'),
    'VD-260829-000002',
    'newest sale first'
);

-- 10. Paginacion
select extensions.ok((public.get_my_recent_sales(1) -> 'page' ->> 'hasMore')::bool, 'reports hasMore when applicable');
select extensions.is(
    (public.get_my_recent_sales(
        1,
        (public.get_my_recent_sales(1) -> 'page' -> 'nextCursor' ->> 'createdAt')::timestamptz,
        (public.get_my_recent_sales(1) -> 'page' -> 'nextCursor' ->> 'id')::uuid
    ) -> 'items' -> 0 ->> 'folio'),
    'VD-260829-000001',
    'pagination advances correctly'
);

-- 11. Estructura y datos
select extensions.is((public.get_my_recent_sales() ->> 'schemaVersion')::int, 1, 'schemaVersion 1');
select extensions.is((public.get_my_recent_sales() -> 'items' -> 1 ->> 'status'), 'PAID', 'status text');
select extensions.ok((public.get_my_recent_sales() -> 'items' -> 1 ->> 'paidAt') is not null, 'paidAt present for paid sale');
select extensions.is((public.get_my_recent_sales() -> 'items' -> 0 ->> 'paidAt'), null, 'paidAt is null for unpaid sale');

-- 12. Ausencia de datos sensibles
select extensions.ok(public.get_my_recent_sales()::text !~* '(email|phone|member|reference|method|unit)', 'sensitive fields absent');

-- 13. Totales historicos
select extensions.is((public.get_my_recent_sales() -> 'items' -> 1 ->> 'totalCents')::bigint, 1000::bigint, 'totalCents preserved');
select extensions.is((public.get_my_recent_sales() -> 'items' -> 1 ->> 'itemCount')::int, 1, 'itemCount calculated');

select extensions.finish();
rollback;
