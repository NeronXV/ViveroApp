begin;

create or replace function public.get_admin_role_options()
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_role pg_catalog.text;
    v_items pg_catalog.jsonb;
begin
    if v_actor_id is null
       or not exists (
           select 1
           from public.profiles profile
           where profile.id = v_actor_id and profile.is_active
       )
       or not public.has_permission('ASSIGN_ROLES') then
        raise exception using errcode = 'P0001', message = 'ROLE_ASSIGNMENT_UNAUTHORIZED';
    end if;

    select role_row.name
    into strict v_actor_role
    from public.user_roles assignment
    join public.roles role_row on role_row.id = assignment.role_id
    where assignment.user_id = v_actor_id;

    if v_actor_role not in ('ADMIN', 'OWNER') then
        raise exception using errcode = 'P0001', message = 'ROLE_ASSIGNMENT_UNAUTHORIZED';
    end if;

    select coalesce(
        pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'name', role_row.name,
                'displayName', role_row.display_name,
                'capabilities', coalesce(
                    (
                        select pg_catalog.jsonb_agg(
                            permission.permission_name
                            order by permission.permission_name collate "C" asc
                        )
                        from public.role_permissions permission
                        where permission.role_id = role_row.id
                    ),
                    '[]'::pg_catalog.jsonb
                )
            )
            order by case role_row.name
                when 'SALES' then 1
                when 'CASHIER' then 2
                when 'INVENTORY' then 3
                when 'MANAGER' then 4
                when 'ADMIN' then 5
                when 'OWNER' then 6
            end
        ),
        '[]'::pg_catalog.jsonb
    )
    into v_items
    from public.roles role_row
    where v_actor_role = 'OWNER' or role_row.name <> 'OWNER';

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'actorRole', v_actor_role,
        'items', v_items,
        'serverTime', pg_catalog.statement_timestamp()
    );
end;
$$;

create or replace function public.set_admin_staff_role(
    p_user_id pg_catalog.uuid,
    p_role_name pg_catalog.text
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_role_name pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_role_name));
    v_result pg_catalog.jsonb;
begin
    if p_user_id is null
       or p_role_name is null
       or v_role_name not in ('SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER') then
        raise exception using errcode = 'P0001', message = 'ROLE_ASSIGNMENT_INVALID';
    end if;

    begin
        perform public.assign_user_role(p_user_id, v_role_name);
    exception
        when others then
            case sqlerrm
                when 'Role assignment is not allowed' then
                    raise exception using errcode = 'P0001', message = 'ROLE_ASSIGNMENT_UNAUTHORIZED';
                when 'Target profile is unavailable' then
                    raise exception using errcode = 'P0001', message = 'ROLE_TARGET_UNAVAILABLE';
                when 'ADMIN cannot grant or modify OWNER' then
                    raise exception using errcode = 'P0001', message = 'ROLE_OWNER_RESTRICTED';
                when 'The last OWNER cannot be reassigned' then
                    raise exception using errcode = 'P0001', message = 'ROLE_LAST_OWNER_REQUIRED';
                else
                    raise;
            end case;
    end;

    select pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'userId', assignment.user_id,
        'role', pg_catalog.jsonb_build_object(
            'name', role_row.name,
            'displayName', role_row.display_name
        ),
        'updatedAt', assignment.updated_at,
        'serverTime', pg_catalog.statement_timestamp()
    )
    into strict v_result
    from public.user_roles assignment
    join public.roles role_row on role_row.id = assignment.role_id
    where assignment.user_id = p_user_id;

    return v_result;
end;
$$;

alter function public.get_admin_role_options() owner to postgres;
alter function public.set_admin_staff_role(pg_catalog.uuid, pg_catalog.text) owner to postgres;

revoke all on function public.get_admin_role_options()
from public, anon, authenticated, service_role;
revoke all on function public.set_admin_staff_role(pg_catalog.uuid, pg_catalog.text)
from public, anon, authenticated, service_role;

grant execute on function public.get_admin_role_options() to authenticated;
grant execute on function public.set_admin_staff_role(pg_catalog.uuid, pg_catalog.text) to authenticated;

comment on function public.get_admin_role_options() is
    'Versioned actor-aware role options for authorized administration clients.';
comment on function public.set_admin_staff_role(pg_catalog.uuid, pg_catalog.text) is
    'Versioned staff role mutation that preserves assign_user_role hierarchy rules and exposes stable application errors.';

commit;
