begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(20);

select extensions.ok(
    (
        select pg_catalog.pg_get_userbyid(p.proowner) = 'postgres'
           and p.prosecdef
           and p.provolatile = 's'
           and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.get_my_access_context()'::pg_catalog.regprocedure
    ),
    'get_my_access_context has the approved owner, return type, volatility, and definer search path'
);

select extensions.ok(
    (
        select pg_catalog.pg_get_userbyid(p.proowner) = 'postgres'
           and p.prosecdef
           and p.provolatile = 's'
           and p.prorettype = 'pg_catalog.jsonb'::pg_catalog.regtype
           and p.proconfig = array['search_path=""']::pg_catalog.text[]
        from pg_catalog.pg_proc p
        where p.oid = 'public.get_public_catalog(text,uuid,integer,text,uuid)'::pg_catalog.regprocedure
    ),
    'get_public_catalog has the approved owner, return type, volatility, and definer search path'
);

select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_proc p
        cross join lateral pg_catalog.aclexplode(
            coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))
        ) acl
        where p.oid = any(array[
            'public.get_my_access_context()'::pg_catalog.regprocedure::oid,
            'public.get_public_catalog(text,uuid,integer,text,uuid)'::pg_catalog.regprocedure::oid
        ])
          and acl.grantee = 0
          and acl.privilege_type = 'EXECUTE'
    ),
    'PUBLIC cannot execute either presentation RPC'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'anon', 'public.get_my_access_context()', 'EXECUTE'
    ) and pg_catalog.has_function_privilege(
        'anon', 'public.get_public_catalog(text,uuid,integer,text,uuid)', 'EXECUTE'
    ),
    'anon can execute only the public catalog presentation RPC'
);

select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated', 'public.get_my_access_context()', 'EXECUTE'
    ) and pg_catalog.has_function_privilege(
        'authenticated', 'public.get_public_catalog(text,uuid,integer,text,uuid)', 'EXECUTE'
    ),
    'authenticated can execute both presentation RPCs'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'service_role', 'public.get_my_access_context()', 'EXECUTE'
    ) and not pg_catalog.has_function_privilege(
        'service_role', 'public.get_public_catalog(text,uuid,integer,text,uuid)', 'EXECUTE'
    ),
    'service_role cannot execute either presentation RPC'
);

select extensions.ok(
    (
        select pg_catalog.bool_and(
            not pg_catalog.has_table_privilege('anon', target.table_name, privilege.name)
        )
        from (values
            ('public.categories'::pg_catalog.text),
            ('public.product_images'::pg_catalog.text),
            ('public.products'::pg_catalog.text)
        ) as target(table_name)
        cross join (values
            ('SELECT'::pg_catalog.text), ('INSERT'::pg_catalog.text),
            ('UPDATE'::pg_catalog.text), ('DELETE'::pg_catalog.text),
            ('TRUNCATE'::pg_catalog.text), ('REFERENCES'::pg_catalog.text),
            ('TRIGGER'::pg_catalog.text)
        ) as privilege(name)
    ),
    'anon has no direct privileges on catalog tables'
);

select extensions.throws_ok(
    $$select public.get_my_access_context()$$,
    '42501',
    'Authentication is required',
    'get_my_access_context rejects an unauthenticated caller'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '71000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"71000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.is(
    public.get_my_access_context()->>'accessState',
    'PROFILE_MISSING',
    'get_my_access_context returns the stable PROFILE_MISSING contract'
);
reset role;

select extensions.is(
    public.get_public_catalog(),
    pg_catalog.jsonb_build_object(
        'schemaVersion', 2,
        'items', '[]'::pg_catalog.jsonb,
        'categories', '[]'::pg_catalog.jsonb,
        'page', pg_catalog.jsonb_build_object(
            'limit', 24,
            'hasMore', false,
            'nextCursor', null
        )
    ),
    'public catalog returns the stable empty response'
);

select extensions.ok(
    public.get_public_catalog() ?& array['schemaVersion', 'items', 'categories', 'page']
    and public.get_public_catalog()->'page' ?& array['limit', 'hasMore', 'nextCursor'],
    'public catalog exposes the versioned top-level and page contract'
);

insert into public.categories (id, name, description, is_active) values
    ('72000000-0000-0000-0000-000000000001', 'Públicas', 'Visible', true),
    ('72000000-0000-0000-0000-000000000002', 'Ocultas', 'No visible', false);

insert into public.products (
    id, internal_code, barcode, common_name, scientific_name, description,
    category_id, price_cents, wholesale_price_cents, unit, minimum_stock,
    watering_advice, light_type, recommended_climate, is_active
) values
    ('73000000-0000-0000-0000-000000000001', 'PRIVATE-A', '90001', 'Aloe público', 'Aloe vera', 'Planta resistente', '72000000-0000-0000-0000-000000000001', 12500, 8000, 'maceta', 5, 'Semanal', 'Sol', 'Seco', true),
    ('73000000-0000-0000-0000-000000000002', 'PRIVATE-B', '90002', 'Aloe inactivo', null, 'No publicar', '72000000-0000-0000-0000-000000000001', 2500, 1000, 'pieza', 2, '', '', '', false),
    ('73000000-0000-0000-0000-000000000003', 'PRIVATE-C', '90003', 'Categoría oculta', null, 'No publicar', '72000000-0000-0000-0000-000000000002', 3500, 1500, 'pieza', 3, '', '', '', true);

select extensions.ok(
    pg_catalog.jsonb_array_length(public.get_public_catalog()->'items') = 1
    and pg_catalog.jsonb_array_length(public.get_public_catalog()->'categories') = 1
    and public.get_public_catalog()#>>'{items,0,id}' = '73000000-0000-0000-0000-000000000001'
    and public.get_public_catalog()#>>'{categories,0,id}' = '72000000-0000-0000-0000-000000000001',
    'public catalog includes active records and excludes inactive products or categories'
);

select extensions.is(
    public.get_public_catalog()#>'{items,0,image}',
    'null'::pg_catalog.jsonb,
    'public catalog returns a null image when no product image exists'
);

select extensions.ok(
    (
        select pg_catalog.count(*) = 9
           and pg_catalog.bool_and(public_key.name = any(array[
               'id', 'name', 'scientificName', 'description', 'category',
               'price', 'care', 'image', 'publicationStatus'
           ]::pg_catalog.text[]))
        from pg_catalog.jsonb_object_keys(
            public.get_public_catalog()#>'{items,0}'
        ) as public_key(name)
    )
    and public.get_public_catalog()::pg_catalog.text !~
        '(PRIVATE-A|90001|wholesale_price|minimum_stock|internal_code|barcode|created_at|updated_at)',
    'public catalog omits private product fields'
);

insert into public.product_images (
    id, product_id, storage_path, alt_text, sort_order, is_primary
) values (
    '77000000-0000-0000-0000-000000000001',
    '73000000-0000-0000-0000-000000000001',
    'products/aloe/main.webp',
    'Aloe en maceta',
    0,
    true
);

select extensions.is(
    public.get_public_catalog()#>'{items,0,image}',
    pg_catalog.jsonb_build_object(
        'bucketName', 'catalog-images',
        'storagePath', 'products/aloe/main.webp',
        'altText', 'Aloe en maceta'
    ),
    'public catalog returns the V2 relative image contract with its bucket name'
);

select extensions.is(
    pg_catalog.jsonb_array_length(public.get_public_catalog('vera')->'items'),
    1,
    'public catalog performs basic case-insensitive search'
);

select extensions.throws_ok(
    $$select public.get_public_catalog(null, null, 0)$$,
    '22023',
    'El límite debe estar entre 1 y 50.',
    'public catalog rejects an invalid limit'
);

select extensions.throws_ok(
    $$select public.get_public_catalog(null, null, 24, 'aloe público', null)$$,
    '22023',
    'El cursor debe incluir nombre e identificador.',
    'public catalog rejects a partial cursor'
);

set local role anon;
select extensions.is(
    pg_catalog.jsonb_array_length(public.get_public_catalog()->'items'),
    1,
    'anon can execute the public catalog without direct table access'
);
reset role;

set local role authenticated;
select extensions.is(
    pg_catalog.jsonb_array_length(public.get_public_catalog()->'items'),
    1,
    'authenticated can execute the public catalog'
);
reset role;

select * from extensions.finish();
rollback;
