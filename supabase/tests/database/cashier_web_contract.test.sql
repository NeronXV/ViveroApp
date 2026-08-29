begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(60);

insert into public.branches (id, code, name, is_active) values
    ('12000000-0000-0000-0000-000000000001', 'WEB-CENTRO', 'Web Centro', true),
    ('12000000-0000-0000-0000-000000000002', 'WEB-NORTE', 'Web Norte', true),
    ('12000000-0000-0000-0000-000000000003', 'WEB-INACT', 'Web Inactiva', false);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'web-sales@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Web"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'web-cashier-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Web A"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'web-cashier-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Web B"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'web-cashier-north@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Web Norte"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'web-inactive@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Inactiva"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'web-no-branch@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Sin Sucursal"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000007', 'authenticated', 'authenticated', 'web-no-capability@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Sin Caja"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-0000-0000-000000000008', 'authenticated', 'authenticated', 'web-inactive-branch@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Sucursal Inactiva"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = case
    when id = '22000000-0000-0000-0000-000000000004' then '12000000-0000-0000-0000-000000000002'::pg_catalog.uuid
    when id = '22000000-0000-0000-0000-000000000006' then null
    when id = '22000000-0000-0000-0000-000000000008' then '12000000-0000-0000-0000-000000000003'::pg_catalog.uuid
    else '12000000-0000-0000-0000-000000000001'::pg_catalog.uuid
end;

update public.profiles
set full_name = '  Ana   Ventas  '
where id = '22000000-0000-0000-0000-000000000001';

update public.profiles
set is_active = false
where id = '22000000-0000-0000-0000-000000000005';

insert into public.user_roles (user_id, role_id)
select assigned.user_id, r.id
from (values
    ('22000000-0000-0000-0000-000000000001'::pg_catalog.uuid, 'SALES'),
    ('22000000-0000-0000-0000-000000000002'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000003'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000004'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000005'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000006'::pg_catalog.uuid, 'CASHIER'),
    ('22000000-0000-0000-0000-000000000007'::pg_catalog.uuid, 'SALES'),
    ('22000000-0000-0000-0000-000000000008'::pg_catalog.uuid, 'CASHIER')
) as assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.categories (id, name) values
    ('32000000-0000-0000-0000-000000000001', 'Caja Web');

insert into public.products (
    id, internal_code, common_name, category_id, price_cents, unit, is_active
) values
    ('42000000-0000-0000-0000-000000000001', 'WEB-PRIVATE-1', 'Maceta pública', '32000000-0000-0000-0000-000000000001', 1000, 'maceta', true),
    ('42000000-0000-0000-0000-000000000002', 'WEB-PRIVATE-2', 'Bolsa pública', '32000000-0000-0000-0000-000000000001', 1000, 'bolsa', true);

insert into public.sales (
    id, folio, branch_id, subtotal_cents, total_cents, status, created_by,
    idempotency_key, created_at
) values
    ('52000000-0000-0000-0000-000000000001', 'VD-200001-WEB001', '12000000-0000-0000-0000-000000000001', 3000, 3000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000001', '2026-08-28 09:00:00+00'),
    ('52000000-0000-0000-0000-000000000002', 'VD-200002-WEB002', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000002', '2026-08-28 09:00:00+00'),
    ('52000000-0000-0000-0000-000000000003', 'VD-200003-WEB003', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000003', '2026-08-28 09:01:00+00'),
    ('52000000-0000-0000-0000-000000000004', 'VD-200004-WEB004', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000004', '2026-08-28 09:02:00+00'),
    ('52000000-0000-0000-0000-000000000005', 'VD-200005-WEB005', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000005', '2026-08-28 09:03:00+00'),
    ('52000000-0000-0000-0000-000000000006', 'VD-200006-WEB006', '12000000-0000-0000-0000-000000000002', 1000, 1000, 'SENT_TO_CASHIER', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000006', '2026-08-28 09:04:00+00'),
    ('52000000-0000-0000-0000-000000000007', 'VD-200007-WEB007', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'PAID', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000007', '2026-08-28 09:05:00+00'),
    ('52000000-0000-0000-0000-000000000008', 'VD-200008-WEB008', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'PAID', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000008', '2026-08-28 09:06:00+00'),
    ('52000000-0000-0000-0000-000000000009', 'VD-200009-WEB009', '12000000-0000-0000-0000-000000000001', 1000, 1000, 'DRAFT', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000009', '2026-08-28 09:07:00+00'),
    ('52000000-0000-0000-0000-000000000010', 'VD-200010-WEB010', '12000000-0000-0000-0000-000000000002', 1000, 1000, 'PAID', '22000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000010', '2026-08-28 09:08:00+00');

insert into public.sale_items (
    id, sale_id, product_id, product_name, internal_code, quantity,
    list_price_cents, unit_price_cents, created_at
) values
    ('53000000-0000-0000-0000-000000000001', '52000000-0000-0000-0000-000000000001', '42000000-0000-0000-0000-000000000001', 'Maceta capturada', 'SECRET-1', 1, 1000, 1000, '2026-08-28 09:00:01+00'),
    ('53000000-0000-0000-0000-000000000002', '52000000-0000-0000-0000-000000000001', '42000000-0000-0000-0000-000000000002', 'Bolsa capturada', 'SECRET-2', 2, 1000, 1000, '2026-08-28 09:00:01+00');

insert into public.sale_items (
    id, sale_id, product_id, product_name, internal_code, quantity,
    list_price_cents, unit_price_cents, created_at
)
select
    ('53100000-0000-0000-0000-' || pg_catalog.lpad(sequence.number::pg_catalog.text, 12, '0'))::pg_catalog.uuid,
    ('52000000-0000-0000-0000-' || pg_catalog.lpad(sequence.number::pg_catalog.text, 12, '0'))::pg_catalog.uuid,
    '42000000-0000-0000-0000-000000000001'::pg_catalog.uuid,
    'Producto capturado', 'SECRET-' || sequence.number, 1, 1000, 1000,
    '2026-08-28 09:10:00+00'::pg_catalog.timestamptz + sequence.number * interval '1 second'
from pg_catalog.generate_series(2, 10) as sequence(number);

insert into public.sale_payment_claims (
    id, sale_id, cashier_id, branch_id, claim_token, created_at, expires_at
) values
    ('82000000-0000-0000-0000-000000000003', '52000000-0000-0000-0000-000000000003', '22000000-0000-0000-0000-000000000002', '12000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000003', pg_catalog.now(), pg_catalog.now() + interval '5 minutes'),
    ('82000000-0000-0000-0000-000000000004', '52000000-0000-0000-0000-000000000004', '22000000-0000-0000-0000-000000000003', '12000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000004', pg_catalog.now(), pg_catalog.now() + interval '5 minutes'),
    ('82000000-0000-0000-0000-000000000005', '52000000-0000-0000-0000-000000000005', '22000000-0000-0000-0000-000000000003', '12000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000005', pg_catalog.now() - interval '10 minutes', pg_catalog.now() - interval '5 minutes');

insert into public.sale_payment_claims (
    id, sale_id, cashier_id, branch_id, claim_token, created_at, expires_at,
    consumed_at, closed_reason
) values
    ('82000000-0000-0000-0000-000000000007', '52000000-0000-0000-0000-000000000007', '22000000-0000-0000-0000-000000000002', '12000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000007', pg_catalog.now() - interval '2 minutes', pg_catalog.now() + interval '3 minutes', pg_catalog.now() - interval '1 minute', 'CONFIRMED'),
    ('82000000-0000-0000-0000-000000000008', '52000000-0000-0000-0000-000000000008', '22000000-0000-0000-0000-000000000003', '12000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000008', pg_catalog.now() - interval '2 minutes', pg_catalog.now() + interval '3 minutes', pg_catalog.now() - interval '1 minute', 'CONFIRMED'),
    ('82000000-0000-0000-0000-000000000010', '52000000-0000-0000-0000-000000000010', '22000000-0000-0000-0000-000000000004', '12000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000010', pg_catalog.now() - interval '2 minutes', pg_catalog.now() + interval '3 minutes', pg_catalog.now() - interval '1 minute', 'CONFIRMED');

insert into public.sale_payments (
    id, sale_id, branch_id, cashier_id, claim_id, idempotency_key, method,
    amount_due_cents, requested_amount_received_cents, amount_received_cents,
    change_cents, reference, created_at
) values
    ('72000000-0000-0000-0000-000000000007', '52000000-0000-0000-0000-000000000007', '12000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007', 'CASH', 1000, 1200, 1200, 200, null, '2026-08-28 09:20:00+00'),
    ('72000000-0000-0000-0000-000000000008', '52000000-0000-0000-0000-000000000008', '12000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000008', '73000000-0000-0000-0000-000000000008', 'TRANSFER', 1000, null, 1000, 0, 'TRX-WEB-08', '2026-08-28 09:21:00+00'),
    ('72000000-0000-0000-0000-000000000010', '52000000-0000-0000-0000-000000000010', '12000000-0000-0000-0000-000000000002', '22000000-0000-0000-0000-000000000004', '82000000-0000-0000-0000-000000000010', '73000000-0000-0000-0000-000000000010', 'CARD', 1000, null, 1000, 0, 'OP-WEB-10', '2026-08-28 09:22:00+00');

update public.products
set unit = case
    when id = '42000000-0000-0000-0000-000000000001' then 'pieza'
    when id = '42000000-0000-0000-0000-000000000002' then 'charola'
    else unit
end
where id in (
    '42000000-0000-0000-0000-000000000001',
    '42000000-0000-0000-0000-000000000002'
);

select extensions.ok(
    (select pg_catalog.count(*) = 3 and pg_catalog.bool_and(
        pg_catalog.pg_get_userbyid(p.proowner) = 'postgres'
        and p.prosecdef
        and p.provolatile = 's'
        and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
        and p.proconfig = array['search_path=""']::pg_catalog.text[]
    ) from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname in (
        'get_cashier_sales', 'get_cashier_sale_detail', 'get_cashier_payment_result'
      )),
    'cashier Web RPCs have approved metadata and explicit postgres ownership'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.get_cashier_sales(integer,timestamp with time zone,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.get_cashier_sale_detail(uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.get_cashier_payment_result(uuid,uuid)', 'EXECUTE'),
    'authenticated can execute all cashier Web RPCs'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.get_cashier_sales(integer,timestamp with time zone,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.get_cashier_sale_detail(uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.get_cashier_payment_result(uuid,uuid)', 'EXECUTE'),
    'anon cannot execute cashier Web RPCs'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('service_role', 'public.get_cashier_sales(integer,timestamp with time zone,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('service_role', 'public.get_cashier_sale_detail(uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('service_role', 'public.get_cashier_payment_result(uuid,uuid)', 'EXECUTE'),
    'service_role cannot execute cashier Web RPCs'
);
select extensions.ok(
    not exists (
        select 1 from pg_catalog.pg_proc p
        cross join lateral pg_catalog.aclexplode(coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))) acl
        where p.oid = any(array[
            'public.get_cashier_sales(integer,timestamp with time zone,uuid)'::pg_catalog.regprocedure::pg_catalog.oid,
            'public.get_cashier_sale_detail(uuid)'::pg_catalog.regprocedure::pg_catalog.oid,
            'public.get_cashier_payment_result(uuid,uuid)'::pg_catalog.regprocedure::pg_catalog.oid
        ]) and acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
    ),
    'PUBLIC cannot execute cashier Web RPCs'
);
select extensions.ok(
    (select pg_catalog.count(*) = 3 and pg_catalog.bool_and(pg_catalog.pg_get_userbyid(p.proowner) = 'postgres')
     from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname in (
        'claim_sale_for_payment', 'release_sale_payment_claim', 'confirm_sale_payment'
     )),
    'existing payment RPCs have explicit postgres ownership'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"22000000-0000-0000-0000-000000000002","role":"authenticated"}';

select extensions.is((public.get_cashier_sales()->'page'->>'limit')::pg_catalog.int4, 25, 'queue defaults to limit 25');
select extensions.throws_ok($$select public.get_cashier_sales(null, null, null)$$, 'P0001', 'CASHIER_PAGE_LIMIT_INVALID', 'null queue limit is rejected');
select extensions.throws_ok($$select public.get_cashier_sales(0, null, null)$$, 'P0001', 'CASHIER_PAGE_LIMIT_INVALID', 'zero queue limit is rejected');
select extensions.throws_ok($$select public.get_cashier_sales(51, null, null)$$, 'P0001', 'CASHIER_PAGE_LIMIT_INVALID', 'queue limit 51 is rejected');
select extensions.throws_ok($$select public.get_cashier_sales(25, '2026-08-28 09:00:00+00', null)$$, 'P0001', 'CASHIER_CURSOR_INVALID', 'cursor timestamp without id is rejected');
select extensions.throws_ok($$select public.get_cashier_sales(25, null, '52000000-0000-0000-0000-000000000001')$$, 'P0001', 'CASHIER_CURSOR_INVALID', 'cursor id without timestamp is rejected');
select extensions.throws_ok($$select public.get_cashier_sale_detail('not-a-uuid')$$, '22P02', null, 'PostgreSQL rejects an invalid UUID before RPC execution');

select extensions.is(
    (select pg_catalog.string_agg(item->>'id', ',' order by ordinal)
     from pg_catalog.jsonb_array_elements(public.get_cashier_sales()->'items') with ordinality row_data(item, ordinal)),
    '52000000-0000-0000-0000-000000000001,52000000-0000-0000-0000-000000000002,52000000-0000-0000-0000-000000000003,52000000-0000-0000-0000-000000000004,52000000-0000-0000-0000-000000000005',
    'queue uses FIFO with UUID as stable tie breaker'
);
select extensions.is(pg_catalog.jsonb_array_length(public.get_cashier_sales(2)->'items'), 2, 'queue applies requested page size');
select extensions.is((public.get_cashier_sales(2)#>>'{page,hasMore}')::pg_catalog.bool, true, 'queue reports another page');
select extensions.is(public.get_cashier_sales(2)#>>'{page,nextCursor,id}', '52000000-0000-0000-0000-000000000002', 'queue returns the last visible row as cursor');
select extensions.is(public.get_cashier_sales(25, '2026-08-28 09:00:00+00', '52000000-0000-0000-0000-000000000002')#>>'{items,0,id}', '52000000-0000-0000-0000-000000000003', 'complete cursor starts after its composite position');
select extensions.is(pg_catalog.jsonb_array_length(public.get_cashier_sales()->'items'), 5, 'queue contains only collectible sales from active branch');
reset role;
select extensions.ok(
    (select pg_catalog.count(*) = 5 from public.sales where branch_id = '12000000-0000-0000-0000-000000000001' and status = 'SENT_TO_CASHIER')
    and (select pg_catalog.count(*) = 3 from public.sale_payment_claims where released_at is null and consumed_at is null),
    'queue reads do not change sale or claim state'
);
set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"22000000-0000-0000-0000-000000000002","role":"authenticated"}';
select extensions.ok(
    (select pg_catalog.count(*) = 3 and pg_catalog.bool_and(key in ('schemaVersion', 'items', 'page'))
     from pg_catalog.jsonb_object_keys(public.get_cashier_sales()) keys(key)),
    'queue response has exact versioned top-level fields'
);
select extensions.ok(
    (public.get_cashier_sales()#>'{items,0}') ?& array[
        'id','folio','createdAt','totalCents','itemCount','status','createdByLabel',
        'claimState','claimExpiresAt','serverTime'
    ] and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_sales()#>'{items,0}')) = 10,
    'queue row exposes exactly the approved fields'
);
select extensions.ok(
    public.get_cashier_sales()::pg_catalog.text !~ '(customer_id|customerId|idempotency|created_by|createdById|email|phone|observation|reference|internal_code|SECRET-)',
    'queue omits PII and internal fields'
);
select extensions.is(public.get_cashier_sales()#>>'{items,0,createdByLabel}', 'Ana Ventas', 'queue normalizes creator label');
select extensions.is(public.get_cashier_sales()#>>'{items,0,claimState}', 'AVAILABLE', 'sale without claim is available');
select extensions.is(public.get_cashier_sales()#>>'{items,2,claimState}', 'CLAIMED_BY_ME', 'own active claim is visible');
select extensions.is(public.get_cashier_sales()#>>'{items,3,claimState}', 'CLAIMED_BY_OTHER', 'other active claim hides cashier identity');
select extensions.is(public.get_cashier_sales()#>>'{items,4,claimState}', 'AVAILABLE', 'expired claim is visible as available');
select extensions.ok(public.get_cashier_sales()#>'{items,4,claimExpiresAt}' <> 'null'::pg_catalog.jsonb, 'expired claim retains visible expiration');
select extensions.ok(public.get_cashier_sales()#>'{items,0,serverTime}' <> 'null'::pg_catalog.jsonb, 'queue exposes server time');

select extensions.ok(
    (select pg_catalog.count(*) = 3 and pg_catalog.bool_and(key in ('schemaVersion','sale','items'))
     from pg_catalog.jsonb_object_keys(public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')) keys(key)),
    'detail response has exact top-level fields'
);
select extensions.ok(
    (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')->'sale') ?& array[
        'id','folio','createdAt','status','totalCents','itemCount','createdByLabel',
        'claimState','claimExpiresAt','serverTime'
    ] and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')->'sale')) = 10,
    'detail sale has exact approved fields'
);
select extensions.is(pg_catalog.jsonb_array_length(public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')->'items'), 2, 'detail returns authoritative item count');
select extensions.is(
    public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,0,id}',
    '53000000-0000-0000-0000-000000000001',
    'detail orders lines by creation and stable line UUID'
);
select extensions.ok(
    (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>'{items,0}') ?& array[
        'id','productName','quantity','unitPriceCents','lineTotalCents'
    ] and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>'{items,0}')) = 5
    and not (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>'{items,0}' ? 'unit'),
    'detail line has exact approved fields and omits unit'
);
select extensions.ok(
    (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{sale,totalCents}')::pg_catalog.int8 = 3000
    and public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,0,productName}' = 'Maceta capturada'
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,0,quantity}')::pg_catalog.int4 = 1
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,0,unitPriceCents}')::pg_catalog.int8 = 1000
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,0,lineTotalCents}')::pg_catalog.int8 = 1000
    and public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,1,productName}' = 'Bolsa capturada'
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,1,quantity}')::pg_catalog.int4 = 2
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,1,unitPriceCents}')::pg_catalog.int8 = 1000
    and (public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')#>>'{items,1,lineTotalCents}')::pg_catalog.int8 = 2000,
    'detail preserves captured names, quantities, prices and totals'
);
select extensions.ok(
    (select unit = 'pieza' from public.products where id = '42000000-0000-0000-0000-000000000001')
    and (select unit = 'charola' from public.products where id = '42000000-0000-0000-0000-000000000002')
    and public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')::pg_catalog.text !~ '"unit"',
    'changing current product unit does not alter historical detail contract'
);
select extensions.ok(
    public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000001')::pg_catalog.text !~ '(customer_id|idempotency|created_by|email|phone|observation|reference|internal_code|SECRET-|claim_token|cashier_id)',
    'detail omits PII, internal codes, history, references and claim token'
);
select extensions.is(public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000004')#>>'{sale,claimState}', 'CLAIMED_BY_OTHER', 'detail exposes claim state without other cashier identity');
select extensions.throws_ok($$select public.get_cashier_sale_detail('52000000-0000-0000-0000-000000009999')$$, 'P0001', 'SALE_UNAVAILABLE', 'missing sale is hidden with safe error');
select extensions.throws_ok($$select public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000006')$$, 'P0001', 'SALE_UNAVAILABLE', 'other branch sale is hidden with same safe error');
select extensions.throws_ok($$select public.get_cashier_sale_detail('52000000-0000-0000-0000-000000000007')$$, 'P0001', 'SALE_UNAVAILABLE', 'paid sale is not collectible through detail');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000009999';
select extensions.throws_ok($$select public.get_cashier_sales()$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'user without profile is rejected');
reset role;
set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000005';
select extensions.throws_ok($$select public.get_cashier_sales()$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'inactive profile is rejected');
reset role;
set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000006';
select extensions.throws_ok($$select public.get_cashier_sales()$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'user without branch is rejected');
reset role;
set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000007';
select extensions.throws_ok($$select public.get_cashier_sales()$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'user without cashier capability is rejected');
reset role;
set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000008';
select extensions.throws_ok($$select public.get_cashier_sales()$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'user with inactive branch is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '22000000-0000-0000-0000-000000000002';
select extensions.is(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->>'status', 'SUCCEEDED', 'cashier recovers own successful payment');
select extensions.ok(
    public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007') ?& array['schemaVersion','status','sale','items','branch','payment','serverTime']
    and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007'))) = 7,
    'successful result has exact versioned top-level fields'
);
select extensions.ok(
    (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'sale') ?& array['id','folio','createdAt','totalCents','createdByLabel']
    and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'sale')) = 5,
    'payment result sale has exact receipt fields'
);
select extensions.ok(
    (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'payment') ?& array['method','amountReceivedCents','changeCents','reference','createdAt']
    and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'payment')) = 5,
    'payment result has exact canonical payment fields'
);
select extensions.ok(
    public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{payment,method}' = 'CASH'
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{payment,amountReceivedCents}')::pg_catalog.int8 = 1200
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{payment,changeCents}')::pg_catalog.int8 = 200,
    'payment result returns canonical CASH amounts'
);
select extensions.ok(
    pg_catalog.jsonb_array_length(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'items') = 1
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>'{items,0}') ?& array[
        'id','productName','quantity','unitPriceCents','lineTotalCents'
    ]
    and (select pg_catalog.count(*) from pg_catalog.jsonb_object_keys(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>'{items,0}')) = 5
    and not (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>'{items,0}' ? 'unit')
    and public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{items,0,productName}' = 'Producto capturado'
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{items,0,quantity}')::pg_catalog.int4 = 1
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{items,0,unitPriceCents}')::pg_catalog.int8 = 1000
    and (public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{items,0,lineTotalCents}')::pg_catalog.int8 = 1000,
    'payment result lines omit unit and preserve captured values'
);
select extensions.ok(
    public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')::pg_catalog.text !~ '(cashier_id|created_by|customer_id|idempotency|email|phone|internal_code|SECRET-|claim_token|wholesale|barcode|"unit")',
    'payment result omits PII, actor UUIDs and administrative fields'
);
select extensions.is(public.get_cashier_payment_result('52000000-0000-0000-0000-000000009999', '73000000-0000-0000-0000-000000009999')->>'status', 'NOT_FOUND', 'missing payment attempt returns NOT_FOUND');
select extensions.is(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000009999')->>'status', 'NOT_FOUND', 'wrong idempotency key returns NOT_FOUND');
select extensions.is(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000008', '73000000-0000-0000-0000-000000000008')->>'status', 'NOT_FOUND', 'attempt processed by another cashier remains hidden');
select extensions.is(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000010', '73000000-0000-0000-0000-000000000010')->>'status', 'NOT_FOUND', 'attempt from another branch remains hidden');
select extensions.ok(public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')->'serverTime' <> 'null'::pg_catalog.jsonb, 'payment recovery exposes server time');
select extensions.ok(
    public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{sale,createdByLabel}' = 'Ana Ventas'
    and public.get_cashier_payment_result('52000000-0000-0000-0000-000000000007', '73000000-0000-0000-0000-000000000007')#>>'{branch,name}' = 'Web Centro',
    'receipt exposes current creator label and branch name as presentation metadata'
);
reset role;

select * from extensions.finish();
rollback;
