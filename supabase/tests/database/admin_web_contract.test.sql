begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(36);

insert into public.branches (id, code, name, is_active) values
    ('91000000-0000-0000-0000-000000000001', 'CENTRO', 'Sucursal Centro', true),
    ('91000000-0000-0000-0000-000000000002', 'NORTE', 'Sucursal Norte', true),
    ('91000000-0000-0000-0000-000000000003', 'SUR', 'Sucursal Sur', false);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'sales@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ana Ventas"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'cashier@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Bruno Caja"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'admin@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Carla Admin"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'owner@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Dulce Owner"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '92000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'inactive@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Eva Inactiva"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = case
        when id in ('92000000-0000-0000-0000-000000000002', '92000000-0000-0000-0000-000000000005')
            then '91000000-0000-0000-0000-000000000002'::pg_catalog.uuid
        else '91000000-0000-0000-0000-000000000001'::pg_catalog.uuid
    end,
    is_active = id <> '92000000-0000-0000-0000-000000000005'::pg_catalog.uuid;

insert into public.user_roles (user_id, role_id)
select assigned.user_id, role_row.id
from (values
    ('92000000-0000-0000-0000-000000000001'::pg_catalog.uuid, 'SALES'),
    ('92000000-0000-0000-0000-000000000002'::pg_catalog.uuid, 'CASHIER'),
    ('92000000-0000-0000-0000-000000000003'::pg_catalog.uuid, 'ADMIN'),
    ('92000000-0000-0000-0000-000000000004'::pg_catalog.uuid, 'OWNER'),
    ('92000000-0000-0000-0000-000000000005'::pg_catalog.uuid, 'SALES')
) as assigned(user_id, role_name)
join public.roles role_row on role_row.name = assigned.role_name;

insert into public.sales (
    id, folio, branch_id, subtotal_cents, total_cents, status, created_by, idempotency_key
) values (
    '93000000-0000-0000-0000-000000000001', 'VD-ADMIN-000001',
    '91000000-0000-0000-0000-000000000001', 1000, 1000, 'SENT_TO_CASHIER',
    '92000000-0000-0000-0000-000000000001', '93000000-0000-0000-0000-000000000001'
);

select extensions.has_function(
    'public', 'get_admin_branches', array['integer', 'text', 'uuid', 'boolean'],
    'admin branch presentation function exists'
);
select extensions.has_function(
    'public', 'get_admin_staff', array['integer', 'text', 'uuid', 'text', 'uuid', 'boolean'],
    'admin staff presentation function exists'
);
select extensions.function_returns(
    'public', 'get_admin_branches', array['integer', 'text', 'uuid', 'boolean'], 'jsonb',
    'admin branch presentation returns jsonb'
);
select extensions.function_returns(
    'public', 'get_admin_staff', array['integer', 'text', 'uuid', 'text', 'uuid', 'boolean'], 'jsonb',
    'admin staff presentation returns jsonb'
);
select extensions.is(
    (select pg_catalog.pg_get_userbyid(p.proowner) from pg_catalog.pg_proc p
     where p.oid = 'public.get_admin_branches(integer,text,uuid,boolean)'::pg_catalog.regprocedure),
    'postgres', 'admin branch presentation is owned by postgres'
);
select extensions.is(
    (select pg_catalog.pg_get_userbyid(p.proowner) from pg_catalog.pg_proc p
     where p.oid = 'public.get_admin_staff(integer,text,uuid,text,uuid,boolean)'::pg_catalog.regprocedure),
    'postgres', 'admin staff presentation is owned by postgres'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.get_admin_branches(integer,text,uuid,boolean)', 'EXECUTE'),
    'authenticated can execute admin branch presentation'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.get_admin_staff(integer,text,uuid,text,uuid,boolean)', 'EXECUTE'),
    'authenticated can execute admin staff presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.get_admin_branches(integer,text,uuid,boolean)', 'EXECUTE'),
    'anon cannot execute admin branch presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.get_admin_staff(integer,text,uuid,text,uuid,boolean)', 'EXECUTE'),
    'anon cannot execute admin staff presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('public', 'public.get_admin_branches(integer,text,uuid,boolean)', 'EXECUTE'),
    'PUBLIC cannot execute admin branch presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('public', 'public.get_admin_staff(integer,text,uuid,text,uuid,boolean)', 'EXECUTE'),
    'PUBLIC cannot execute admin staff presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('service_role', 'public.get_admin_branches(integer,text,uuid,boolean)', 'EXECUTE'),
    'service_role cannot execute admin branch presentation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('service_role', 'public.get_admin_staff(integer,text,uuid,text,uuid,boolean)', 'EXECUTE'),
    'service_role cannot execute admin staff presentation'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '92000000-0000-0000-0000-000000000001';
set local "request.jwt.claims" = '{"sub":"92000000-0000-0000-0000-000000000001","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.get_admin_branches()$$, 'P0001', 'ADMIN_UNAUTHORIZED',
    'SALES cannot query administrative branches'
);

set local "request.jwt.claim.sub" = '92000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"92000000-0000-0000-0000-000000000002","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.get_admin_staff()$$, 'P0001', 'ADMIN_UNAUTHORIZED',
    'CASHIER cannot query administrative staff'
);

set local "request.jwt.claim.sub" = '92000000-0000-0000-0000-000000000003';
set local "request.jwt.claims" = '{"sub":"92000000-0000-0000-0000-000000000003","role":"authenticated"}';
select extensions.is((public.get_admin_branches() ->> 'schemaVersion')::pg_catalog.int4, 1, 'branch response uses schemaVersion 1');
select extensions.results_eq(
    $$select key from pg_catalog.jsonb_object_keys(public.get_admin_branches()) key order by key$$,
    $$values ('items'::text), ('page'::text), ('schemaVersion'::text), ('serverTime'::text)$$,
    'branch response exposes exact top-level keys'
);
select extensions.is(pg_catalog.jsonb_array_length(public.get_admin_branches() -> 'items'), 2, 'branches exclude inactive rows by default');
select extensions.is(pg_catalog.jsonb_array_length(public.get_admin_branches(50, null, null, true) -> 'items'), 3, 'authorized branch query can include inactive rows');
select extensions.is(
    (public.get_admin_branches() #>> '{items,0,activeStaffCount}')::pg_catalog.int4,
    3, 'branch projection counts active staff'
);
select extensions.is(
    (public.get_admin_branches() #>> '{items,0,pendingSaleCount}')::pg_catalog.int4,
    1, 'branch projection counts pending sales'
);
select extensions.ok((public.get_admin_branches(1) #>> '{page,hasMore}')::pg_catalog.bool, 'branch pagination reports a following page');
select extensions.is(
    (
        public.get_admin_branches(
            1,
            public.get_admin_branches(1) #>> '{page,nextCursor,code}',
            (public.get_admin_branches(1) #>> '{page,nextCursor,id}')::pg_catalog.uuid,
            false
        ) #>> '{items,0,code}'
    ),
    'NORTE', 'branch cursor advances without repeating the previous row'
);
select extensions.throws_ok(
    $$select public.get_admin_branches(0)$$, 'P0001', 'ADMIN_BRANCH_QUERY_INVALID',
    'branch query rejects an invalid limit'
);
select extensions.throws_ok(
    $$select public.get_admin_branches(50, 'CENTRO', null, false)$$, 'P0001', 'ADMIN_BRANCH_QUERY_INVALID',
    'branch query rejects a partial cursor'
);

select extensions.is((public.get_admin_staff() ->> 'schemaVersion')::pg_catalog.int4, 1, 'staff response uses schemaVersion 1');
select extensions.results_eq(
    $$select key from pg_catalog.jsonb_object_keys(public.get_admin_staff()) key order by key$$,
    $$values ('items'::text), ('page'::text), ('schemaVersion'::text), ('serverTime'::text)$$,
    'staff response exposes exact top-level keys'
);
select extensions.is(pg_catalog.jsonb_array_length(public.get_admin_staff() -> 'items'), 4, 'staff excludes inactive profiles by default');
select extensions.ok(
    public.get_admin_staff()::pg_catalog.text !~* '(email|@test|token|claim)',
    'staff projection omits auth email, tokens and claims'
);
select extensions.is(
    pg_catalog.jsonb_array_length(public.get_admin_staff(50, null, null, 'dulce', null, false) -> 'items'),
    1, 'staff search is normalized and case insensitive'
);
select extensions.is(
    public.get_admin_staff(50, null, null, null, '91000000-0000-0000-0000-000000000002', false) #>> '{items,0,fullName}',
    'Bruno Caja', 'staff can be filtered by branch'
);
select extensions.is(pg_catalog.jsonb_array_length(public.get_admin_staff(50, null, null, null, null, true) -> 'items'), 5, 'authorized staff query can include inactive profiles');
select extensions.throws_ok(
    $$select public.get_admin_staff(101)$$, 'P0001', 'ADMIN_STAFF_QUERY_INVALID',
    'staff query rejects an invalid limit'
);
select extensions.throws_ok(
    $$select public.get_admin_staff(50, 'Ana Ventas', null)$$, 'P0001', 'ADMIN_STAFF_QUERY_INVALID',
    'staff query rejects a partial cursor'
);
select extensions.throws_ok(
    $$select public.get_admin_staff(50, null, null, '   ')$$, 'P0001', 'ADMIN_STAFF_QUERY_INVALID',
    'staff query rejects an empty explicit search'
);

select extensions.finish();
rollback;
