begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(17);

select extensions.has_function(
    'public', 'get_product_by_scan_code', array['text'],
    'product scan lookup function exists'
);

select extensions.ok(
    (
        select p.prosecdef
           and p.provolatile = 's'
           and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.get_product_by_scan_code(text)'::pg_catalog.regprocedure
    ),
    'product scan lookup is stable and hardened'
);

select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated', 'public.get_product_by_scan_code(text)', 'EXECUTE'
    ),
    'authenticated can execute product scan lookup'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'anon', 'public.get_product_by_scan_code(text)', 'EXECUTE'
    ),
    'anon cannot execute product scan lookup'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'service_role', 'public.get_product_by_scan_code(text)', 'EXECUTE'
    ),
    'service role is not granted the client product scan lookup'
);

insert into public.branches (id, code, name) values
    ('91000000-0000-4000-8000-000000000001', 'SCAN', 'Sucursal escáner');

insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at
) values
    (
        '91000000-0000-4000-8000-000000000011',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'scan-branch@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    ),
    (
        '91000000-0000-4000-8000-000000000012',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'scan-no-branch@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    );

update public.profiles
set branch_id = '91000000-0000-4000-8000-000000000001'
where id = '91000000-0000-4000-8000-000000000011';

insert into public.user_roles (user_id, role_id)
select actor.id, role_data.id
from (values
    ('91000000-0000-4000-8000-000000000011'::pg_catalog.uuid),
    ('91000000-0000-4000-8000-000000000012'::pg_catalog.uuid)
) actor(id)
cross join lateral (
    select r.id from public.roles r where r.name = 'SALES'
) role_data;

insert into public.categories (id, name) values
    ('91000000-0000-4000-8000-000000000021', 'Categoría escáner');

insert into public.products (
    id, internal_code, barcode, common_name, category_id, price_cents, unit, is_active
) values
    (
        '91000000-0000-4000-8000-000000000031', 'SCAN-ALOE-004', '750100000031',
        'Aloe de prueba', '91000000-0000-4000-8000-000000000021', 12500, 'pieza', true
    ),
    (
        '91000000-0000-4000-8000-000000000032', 'SCAN-INACTIVE', '750100000032',
        'Producto inactivo', '91000000-0000-4000-8000-000000000021', 5000, 'pieza', false
    ),
    (
        '91000000-0000-4000-8000-000000000033', 'AMB-1', null,
        'Código ambiguo A', '91000000-0000-4000-8000-000000000021', 5000, 'pieza', true
    ),
    (
        '91000000-0000-4000-8000-000000000034', 'AMB-2', 'AMB-1',
        'Código ambiguo B', '91000000-0000-4000-8000-000000000021', 5000, 'pieza', true
    );

insert into public.product_images (
    id, product_id, storage_path, alt_text, sort_order, is_primary
) values (
    '91000000-0000-4000-8000-000000000041',
    '91000000-0000-4000-8000-000000000031',
    'products/scan-aloe.webp', 'Aloe de prueba', 0, true
);

insert into public.inventory_movements (
    branch_id, product_id, movement_type, quantity, created_by
) values (
    '91000000-0000-4000-8000-000000000001',
    '91000000-0000-4000-8000-000000000031',
    'RECEPTION', 7,
    '91000000-0000-4000-8000-000000000011'
);

select extensions.throws_ok(
    $$select public.get_product_by_scan_code('SCAN-ALOE-004')$$,
    '42501', 'PRODUCT_SCAN_UNAUTHORIZED',
    'unauthenticated product scan is rejected'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '91000000-0000-4000-8000-000000000011';
set local "request.jwt.claims" = '{"sub":"91000000-0000-4000-8000-000000000011","role":"authenticated"}';

select extensions.throws_ok(
    $$select public.get_product_by_scan_code(' ')$$,
    '22023', 'PRODUCT_SCAN_CODE_INVALID',
    'blank product scan code is rejected'
);

select extensions.is(
    public.get_product_by_scan_code(' scan-aloe-004 ') ->> 'schemaVersion', '1',
    'product scan contract is versioned'
);

select extensions.is(
    public.get_product_by_scan_code(' scan-aloe-004 ')
        #>> '{item,product,id}',
    '91000000-0000-4000-8000-000000000031',
    'internal code lookup is trimmed and case insensitive'
);

select extensions.is(
    public.get_product_by_scan_code('750100000031')
        #>> '{item,product,id}',
    '91000000-0000-4000-8000-000000000031',
    'barcode lookup resolves the same product'
);

select extensions.ok(
    (
        public.get_product_by_scan_code('SCAN-ALOE-004')
            #>> '{item,stockAvailable}'
    )::pg_catalog.numeric = 7
    and public.get_product_by_scan_code('SCAN-ALOE-004')
        #>> '{item,stockKnown}' = 'true',
    'product scan exposes inventory only from the actor branch'
);

select extensions.ok(
    public.get_product_by_scan_code('SCAN-ALOE-004')
        #>> '{item,pricing,listPriceCents}' = '12500'
    and public.get_product_by_scan_code('SCAN-ALOE-004')
        #>> '{item,pricing,effectivePriceCents}' = '12500',
    'product scan uses authoritative catalog pricing'
);

select extensions.is(
    public.get_product_by_scan_code('SCAN-ALOE-004')
        #>> '{item,product,images,0,storage_path}',
    'products/scan-aloe.webp',
    'product scan returns the product image contract'
);

select extensions.is(
    pg_catalog.jsonb_typeof(public.get_product_by_scan_code('UNKNOWN') -> 'item'),
    'null',
    'unknown product scan code returns an explicit null item'
);

select extensions.is(
    pg_catalog.jsonb_typeof(public.get_product_by_scan_code('SCAN-INACTIVE') -> 'item'),
    'null',
    'inactive products cannot be resolved by scan'
);

select extensions.throws_ok(
    $$select public.get_product_by_scan_code('AMB-1')$$,
    'P0001', 'PRODUCT_SCAN_CODE_AMBIGUOUS',
    'cross-field scan code collisions are rejected'
);

set local "request.jwt.claim.sub" = '91000000-0000-4000-8000-000000000012';
set local "request.jwt.claims" = '{"sub":"91000000-0000-4000-8000-000000000012","role":"authenticated"}';

select extensions.ok(
    public.get_product_by_scan_code('SCAN-ALOE-004')
        #>> '{item,stockKnown}' = 'false'
    and pg_catalog.jsonb_typeof(
        public.get_product_by_scan_code('SCAN-ALOE-004')
            #> '{item,stockAvailable}'
    ) = 'null',
    'an authorized actor without branch receives explicitly unknown stock'
);

reset role;

select * from extensions.finish();

rollback;
