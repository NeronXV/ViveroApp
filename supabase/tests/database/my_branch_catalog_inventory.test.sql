begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(12);

insert into public.branches (id, code, name) values
    ('93000000-0000-4000-8000-000000000001', 'TEST-A', 'Sucursal de prueba A'),
    ('93000000-0000-4000-8000-000000000002', 'TEST-B', 'Sucursal de prueba B');

insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at
) values
    (
        '93000000-0000-4000-8000-000000000011',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'sales-a@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    ),
    (
        '93000000-0000-4000-8000-000000000012',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'sales-b@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    );

update public.profiles set branch_id = '93000000-0000-4000-8000-000000000001'
where id = '93000000-0000-4000-8000-000000000011';
update public.profiles set branch_id = '93000000-0000-4000-8000-000000000002'
where id = '93000000-0000-4000-8000-000000000012';

insert into public.user_roles (user_id, role_id)
select actor.id, role_data.id
from (values
    ('93000000-0000-4000-8000-000000000011'::pg_catalog.uuid),
    ('93000000-0000-4000-8000-000000000012'::pg_catalog.uuid)
) actor(id)
cross join lateral (
    select r.id from public.roles r where r.name = 'SALES'
) role_data;

insert into public.categories (id, name) values
    ('93000000-0000-4000-8000-000000000021', 'Categoría inventario prueba');

insert into public.products (
    id, internal_code, common_name, category_id, price_cents, unit
) values
    (
        '93000000-0000-4000-8000-000000000031', 'INV-TEST-1', 'Producto con saldo',
        '93000000-0000-4000-8000-000000000021', 10000, 'pieza'
    ),
    (
        '93000000-0000-4000-8000-000000000032', 'INV-TEST-2', 'Producto sin saldo',
        '93000000-0000-4000-8000-000000000021', 20000, 'pieza'
    );

insert into public.inventory_movements (
    branch_id, product_id, movement_type, quantity, created_by
) values
    (
        '93000000-0000-4000-8000-000000000001',
        '93000000-0000-4000-8000-000000000031',
        'RECEPTION', 7,
        '93000000-0000-4000-8000-000000000011'
    ),
    (
        '93000000-0000-4000-8000-000000000002',
        '93000000-0000-4000-8000-000000000031',
        'RECEPTION', 19,
        '93000000-0000-4000-8000-000000000012'
    );

select extensions.has_function(
    'public', 'get_my_branch_catalog_inventory', array[]::pg_catalog.text[],
    'branch catalog inventory function exists with no client-selected scope'
);

select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated', 'public.get_my_branch_catalog_inventory()', 'EXECUTE'
    ),
    'authenticated can execute the branch catalog inventory projection'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'anon', 'public.get_my_branch_catalog_inventory()', 'EXECUTE'
    ),
    'anon cannot execute the branch catalog inventory projection'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'service_role', 'public.get_my_branch_catalog_inventory()', 'EXECUTE'
    ),
    'service_role is not granted the client presentation function'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '93000000-0000-4000-8000-000000000011';

select extensions.is(
    public.get_my_branch_catalog_inventory() ->> 'schemaVersion', '1',
    'inventory projection is versioned'
);

select extensions.is(
    public.get_my_branch_catalog_inventory() ->> 'branchId',
    '93000000-0000-4000-8000-000000000001',
    'inventory projection is fixed to the actor branch'
);

select extensions.is(
    pg_catalog.jsonb_array_length(public.get_my_branch_catalog_inventory() -> 'items'), 2,
    'inventory projection includes every active product'
);

select extensions.is(
    public.get_my_branch_catalog_inventory() #>> '{items,0,productId}',
    '93000000-0000-4000-8000-000000000031',
    'inventory products use stable UUID ordering'
);

select extensions.is(
    (public.get_my_branch_catalog_inventory() #>> '{items,0,totalQuantity}')::pg_catalog.numeric,
    7::pg_catalog.numeric,
    'inventory projection exposes the actor branch balance'
);

select extensions.is(
    (public.get_my_branch_catalog_inventory() #>> '{items,1,totalQuantity}')::pg_catalog.numeric,
    0::pg_catalog.numeric,
    'a product without movements is returned with zero balance'
);

select extensions.ok(
    not pg_catalog.jsonb_path_exists(
        public.get_my_branch_catalog_inventory(),
        '$.items[*] ? (@.totalQuantity == 19)'::pg_catalog.jsonpath
    ),
    'inventory projection does not expose another branch balance'
);

reset role;
set local "request.jwt.claim.sub" = '';

select extensions.throws_ok(
    $$select public.get_my_branch_catalog_inventory()$$,
    'P0001', 'CATALOG_INVENTORY_UNAUTHORIZED',
    'an unauthenticated caller is rejected'
);

select * from extensions.finish();

rollback;
