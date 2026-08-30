begin;

create extension if not exists pgtap with schema extensions;
select extensions.plan(20);

insert into public.branches (id, code, name) values
    ('94000000-0000-4000-8000-000000000001', 'PILOT-A', 'Piloto A'),
    ('94000000-0000-4000-8000-000000000002', 'PILOT-B', 'Piloto B');

insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at
) values
    ('94000000-0000-4000-8000-000000000011', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'manager@example.test', '', pg_catalog.now(), pg_catalog.now(), pg_catalog.now()),
    ('94000000-0000-4000-8000-000000000012', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'sales@example.test', '', pg_catalog.now(), pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = '94000000-0000-4000-8000-000000000001', full_name = 'Gerente Piloto'
where id = '94000000-0000-4000-8000-000000000011';
update public.profiles
set branch_id = '94000000-0000-4000-8000-000000000002', full_name = 'Ventas Otra Sucursal'
where id = '94000000-0000-4000-8000-000000000012';

insert into public.user_roles (user_id, role_id)
select '94000000-0000-4000-8000-000000000011', r.id from public.roles r where r.name = 'MANAGER';
insert into public.user_roles (user_id, role_id)
select '94000000-0000-4000-8000-000000000012', r.id from public.roles r where r.name = 'SALES';

insert into public.categories (id, name) values
    ('94000000-0000-4000-8000-000000000021', 'Categoría piloto');
insert into public.products (
    id, internal_code, common_name, category_id, price_cents, unit, minimum_stock
) values
    ('94000000-0000-4000-8000-000000000031', 'PILOT-01', 'Planta piloto', '94000000-0000-4000-8000-000000000021', 10000, 'pieza', 3);

insert into public.inventory_movements (
    branch_id, product_id, movement_type, quantity, notes, created_by
) values (
    '94000000-0000-4000-8000-000000000002',
    '94000000-0000-4000-8000-000000000031',
    'RECEPTION', 99, 'Existencia de otra sucursal',
    '94000000-0000-4000-8000-000000000012'
);

select extensions.has_function('public', 'get_my_inventory_dashboard', array['integer', 'uuid'], 'inventory dashboard function exists');
select extensions.has_function('public', 'record_inventory_reception', array['uuid', 'numeric', 'text', 'uuid'], 'idempotent reception function exists');
select extensions.has_function('public', 'reconcile_inventory_count', array['uuid', 'numeric', 'text', 'uuid', 'uuid'], 'atomic count reconciliation function exists');
select extensions.has_function('public', 'get_my_inventory_history', array['uuid', 'integer', 'timestamp with time zone', 'uuid'], 'inventory history function exists');

select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.record_inventory_reception(uuid,numeric,text,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.reconcile_inventory_count(uuid,numeric,text,uuid,uuid)', 'EXECUTE'),
    'authenticated receives the inventory pilot entry points'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.record_inventory_reception(uuid,numeric,text,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.get_my_inventory_history(uuid,integer,timestamp with time zone,uuid)', 'EXECUTE'),
    'anon cannot execute inventory pilot functions'
);
select extensions.ok(
    (select relrowsecurity from pg_catalog.pg_class where oid = 'public.inventory_counts'::pg_catalog.regclass),
    'inventory counts have RLS enabled'
);
select extensions.ok(
    not pg_catalog.has_table_privilege('authenticated', 'public.inventory_counts', 'SELECT')
    and not pg_catalog.has_table_privilege('authenticated', 'public.inventory_counts', 'INSERT'),
    'clients receive no direct inventory count privileges'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '94000000-0000-4000-8000-000000000011';

select extensions.is(
    public.get_my_inventory_dashboard() #>> '{items,0,productId}',
    '94000000-0000-4000-8000-000000000031',
    'dashboard includes the active product in the manager branch'
);
select extensions.is(
    (public.get_my_inventory_dashboard() #>> '{items,0,totalQuantity}')::pg_catalog.numeric,
    0::pg_catalog.numeric,
    'dashboard returns zero without exposing the other branch balance'
);

select extensions.is(
    (public.record_inventory_reception(
        '94000000-0000-4000-8000-000000000031', 10, 'Recepción inicial',
        '94000000-0000-4000-8000-000000000041'
    ) ->> 'totalQuantity')::pg_catalog.numeric,
    10::pg_catalog.numeric,
    'reception increases the authoritative balance'
);
select extensions.is(
    public.record_inventory_reception(
        '94000000-0000-4000-8000-000000000031', 10, 'Recepción inicial',
        '94000000-0000-4000-8000-000000000041'
    ) ->> 'idempotentReplay',
    'true',
    'repeated reception returns an idempotent replay'
);
select extensions.is(
    (select pg_catalog.count(*) from public.inventory_movements where reference_id = '94000000-0000-4000-8000-000000000041'),
    1::pg_catalog.bigint,
    'repeated reception creates one movement'
);

select extensions.is(
    (public.reconcile_inventory_count(
        '94000000-0000-4000-8000-000000000031', 8, 'Dos plantas dañadas',
        '94000000-0000-4000-8000-000000000042'
    ) ->> 'adjustmentQuantity')::pg_catalog.numeric,
    (-2)::pg_catalog.numeric,
    'physical count calculates the adjustment atomically'
);
select extensions.is(
    (select total_quantity from public.inventory_balances
     where branch_id = '94000000-0000-4000-8000-000000000001'
       and product_id = '94000000-0000-4000-8000-000000000031'),
    8::pg_catalog.numeric,
    'physical count leaves the authoritative balance at the counted quantity'
);
select extensions.is(
    public.reconcile_inventory_count(
        '94000000-0000-4000-8000-000000000031', 8, 'Dos plantas dañadas',
        '94000000-0000-4000-8000-000000000042'
    ) ->> 'idempotentReplay',
    'true',
    'repeated physical count returns an idempotent replay'
);
select extensions.is(
    (select pg_catalog.count(*) from public.inventory_counts where id = '94000000-0000-4000-8000-000000000042'),
    1::pg_catalog.bigint,
    'repeated physical count creates one count audit row'
);

select extensions.is(
    pg_catalog.jsonb_array_length(public.get_my_inventory_history('94000000-0000-4000-8000-000000000031') -> 'items'),
    2,
    'history returns reception and count adjustment'
);
select extensions.is(
    public.get_my_inventory_history('94000000-0000-4000-8000-000000000031') #>> '{items,0,createdByLabel}',
    'Gerente Piloto',
    'history exposes only the current presentation label for the actor'
);

set local "request.jwt.claim.sub" = '94000000-0000-4000-8000-000000000012';
select extensions.throws_ok(
    $$select public.record_inventory_reception(
        '94000000-0000-4000-8000-000000000031', 1, 'No autorizado',
        '94000000-0000-4000-8000-000000000099'
    )$$,
    'P0001', 'INVENTORY_UNAUTHORIZED',
    'SALES cannot register inventory receptions'
);

select * from extensions.finish();
rollback;
