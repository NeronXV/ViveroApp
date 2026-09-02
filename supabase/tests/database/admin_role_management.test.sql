begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(36);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', 'a1000000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'admin-role@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Admin Roles"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', 'a1000000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'owner-role@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Owner Roles"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', 'a1000000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'target-role@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Target Roles"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', 'a1000000-0000-4000-8000-000000000004', 'authenticated', 'authenticated', 'inactive-role@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Inactive Roles"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', 'a1000000-0000-4000-8000-000000000005', 'authenticated', 'authenticated', 'sales-role@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Sales Roles"}', pg_catalog.now(), pg_catalog.now());

insert into public.user_roles (user_id, role_id)
select assigned.user_id, role_row.id
from (values
    ('a1000000-0000-4000-8000-000000000001'::pg_catalog.uuid, 'ADMIN'),
    ('a1000000-0000-4000-8000-000000000002'::pg_catalog.uuid, 'OWNER'),
    ('a1000000-0000-4000-8000-000000000003'::pg_catalog.uuid, 'SALES'),
    ('a1000000-0000-4000-8000-000000000004'::pg_catalog.uuid, 'SALES'),
    ('a1000000-0000-4000-8000-000000000005'::pg_catalog.uuid, 'SALES')
) as assigned(user_id, role_name)
join public.roles role_row on role_row.name = assigned.role_name;

update public.profiles
set is_active = false
where id = 'a1000000-0000-4000-8000-000000000004'::pg_catalog.uuid;

select extensions.has_function(
    'public', 'get_admin_role_options', array[]::text[],
    'admin role options function exists'
);
select extensions.has_function(
    'public', 'set_admin_staff_role', array['uuid', 'text'],
    'admin staff role mutation exists'
);
select extensions.function_returns(
    'public', 'get_admin_role_options', array[]::text[], 'jsonb',
    'admin role options return jsonb'
);
select extensions.function_returns(
    'public', 'set_admin_staff_role', array['uuid', 'text'], 'jsonb',
    'admin staff role mutation returns jsonb'
);
select extensions.is(
    (select pg_catalog.pg_get_userbyid(proc.proowner)
     from pg_catalog.pg_proc proc
     where proc.oid = 'public.get_admin_role_options()'::pg_catalog.regprocedure),
    'postgres', 'admin role options are owned by postgres'
);
select extensions.is(
    (select pg_catalog.pg_get_userbyid(proc.proowner)
     from pg_catalog.pg_proc proc
     where proc.oid = 'public.set_admin_staff_role(uuid,text)'::pg_catalog.regprocedure),
    'postgres', 'admin staff role mutation is owned by postgres'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.get_admin_role_options()', 'EXECUTE'),
    'authenticated can execute admin role options'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.set_admin_staff_role(uuid,text)', 'EXECUTE'),
    'authenticated can execute admin staff role mutation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.get_admin_role_options()', 'EXECUTE'),
    'anon cannot execute admin role options'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.set_admin_staff_role(uuid,text)', 'EXECUTE'),
    'anon cannot execute admin staff role mutation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('public', 'public.get_admin_role_options()', 'EXECUTE'),
    'PUBLIC cannot execute admin role options'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('public', 'public.set_admin_staff_role(uuid,text)', 'EXECUTE'),
    'PUBLIC cannot execute admin staff role mutation'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('service_role', 'public.get_admin_role_options()', 'EXECUTE'),
    'service_role cannot execute admin role options'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('service_role', 'public.set_admin_staff_role(uuid,text)', 'EXECUTE'),
    'service_role cannot execute admin staff role mutation'
);

set local role authenticated;
set local "request.jwt.claim.sub" = 'a1000000-0000-4000-8000-000000000005';
set local "request.jwt.claims" = '{"sub":"a1000000-0000-4000-8000-000000000005","role":"authenticated"}';
select extensions.throws_ok(
    $$select public.get_admin_role_options()$$,
    'P0001', 'ROLE_ASSIGNMENT_UNAUTHORIZED',
    'SALES cannot query assignable roles'
);
select extensions.throws_ok(
    $$select public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'CASHIER')$$,
    'P0001', 'ROLE_ASSIGNMENT_UNAUTHORIZED',
    'SALES cannot assign roles'
);

set local "request.jwt.claim.sub" = 'a1000000-0000-4000-8000-000000000001';
set local "request.jwt.claims" = '{"sub":"a1000000-0000-4000-8000-000000000001","role":"authenticated"}';
select extensions.is(
    (public.get_admin_role_options() ->> 'schemaVersion')::pg_catalog.int4,
    1, 'role options use schemaVersion 1'
);
select extensions.is(
    public.get_admin_role_options() ->> 'actorRole',
    'ADMIN', 'role options identify the ADMIN actor'
);
select extensions.is(
    pg_catalog.jsonb_array_length(public.get_admin_role_options() -> 'items'),
    5, 'ADMIN receives five assignable role options'
);
select extensions.is(
    (
        select pg_catalog.count(*)::pg_catalog.int4
        from pg_catalog.jsonb_array_elements(public.get_admin_role_options() -> 'items') item
        where item ->> 'name' = 'OWNER'
    ),
    0, 'ADMIN role options exclude OWNER'
);
select extensions.results_eq(
    $$select key from pg_catalog.jsonb_object_keys(public.get_admin_role_options()) key order by key$$,
    $$values ('actorRole'::text), ('items'::text), ('schemaVersion'::text), ('serverTime'::text)$$,
    'role options expose exact top-level keys'
);
select extensions.is(
    (
        select pg_catalog.jsonb_array_length(item -> 'capabilities')
        from pg_catalog.jsonb_array_elements(public.get_admin_role_options() -> 'items') item
        where item ->> 'name' = 'SALES'
    ),
    4, 'role options expose the authoritative SALES capabilities'
);
select extensions.throws_ok(
    $$select public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'UNKNOWN')$$,
    'P0001', 'ROLE_ASSIGNMENT_INVALID',
    'role mutation rejects an unknown role with a stable error'
);
select extensions.throws_ok(
    $$select public.set_admin_staff_role('a1000000-0000-4000-8000-000000000004', 'CASHIER')$$,
    'P0001', 'ROLE_TARGET_UNAVAILABLE',
    'role mutation rejects an inactive target with a stable error'
);
select extensions.is(
    (public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'MANAGER') ->> 'schemaVersion')::pg_catalog.int4,
    1, 'role mutation uses schemaVersion 1'
);
select extensions.is(
    public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'MANAGER') #>> '{role,name}',
    'MANAGER', 'role mutation returns the assigned role'
);
select extensions.is(
    (
        select role_row.name
        from public.user_roles assignment
        join public.roles role_row on role_row.id = assignment.role_id
        where assignment.user_id = 'a1000000-0000-4000-8000-000000000003'::pg_catalog.uuid
    ),
    'MANAGER', 'role mutation persists the assignment'
);
select extensions.throws_ok(
    $$select public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'OWNER')$$,
    'P0001', 'ROLE_OWNER_RESTRICTED',
    'ADMIN cannot promote a target to OWNER'
);

set local "request.jwt.claim.sub" = 'a1000000-0000-4000-8000-000000000002';
set local "request.jwt.claims" = '{"sub":"a1000000-0000-4000-8000-000000000002","role":"authenticated"}';
select extensions.is(
    pg_catalog.jsonb_array_length(public.get_admin_role_options() -> 'items'),
    6, 'OWNER receives all six role options'
);
select extensions.is(
    (
        select pg_catalog.count(*)::pg_catalog.int4
        from pg_catalog.jsonb_array_elements(public.get_admin_role_options() -> 'items') item
        where item ->> 'name' = 'OWNER'
    ),
    1, 'OWNER role options include OWNER'
);
select extensions.throws_ok(
    $$select public.set_admin_staff_role('a1000000-0000-4000-8000-000000000002', 'SALES')$$,
    'P0001', 'ROLE_LAST_OWNER_REQUIRED',
    'the last OWNER cannot be reassigned'
);
select extensions.is(
    public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'OWNER') #>> '{role,name}',
    'OWNER', 'OWNER can promote an active target to OWNER'
);
select extensions.is(
    (
        select role_row.name
        from public.user_roles assignment
        join public.roles role_row on role_row.id = assignment.role_id
        where assignment.user_id = 'a1000000-0000-4000-8000-000000000003'::pg_catalog.uuid
    ),
    'OWNER', 'OWNER promotion is persisted'
);
select extensions.results_eq(
    $$select key from pg_catalog.jsonb_object_keys(public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', 'OWNER')) key order by key$$,
    $$values ('role'::text), ('schemaVersion'::text), ('serverTime'::text), ('updatedAt'::text), ('userId'::text)$$,
    'role mutation exposes exact top-level keys'
);
select extensions.is(
    public.set_admin_staff_role('a1000000-0000-4000-8000-000000000003', ' manager ') #>> '{role,name}',
    'MANAGER', 'role mutation normalizes surrounding spaces and case'
);
select extensions.is(
    public.get_admin_role_options() #>> '{items,5,name}',
    'OWNER', 'role options preserve the documented hierarchy order'
);

select * from extensions.finish();
rollback;
