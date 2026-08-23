begin;

create or replace function public.get_my_access_context()
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id pg_catalog.uuid := auth.uid();
    v_profile public.profiles%rowtype;
    v_branch public.branches%rowtype;
    v_role public.roles%rowtype;
    v_access_state pg_catalog.text;
    v_capabilities pg_catalog.jsonb := '[]'::pg_catalog.jsonb;
begin
    if v_user_id is null then
        raise exception using
            errcode = '42501',
            message = 'Authentication is required';
    end if;

    select p.*
    into v_profile
    from public.profiles p
    where p.id = v_user_id;

    if not found then
        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'userId', v_user_id,
            'accessState', 'PROFILE_MISSING',
            'profile', null,
            'branch', null,
            'role', null,
            'capabilities', '[]'::pg_catalog.jsonb
        );
    end if;

    if v_profile.branch_id is not null then
        select b.*
        into v_branch
        from public.branches b
        where b.id = v_profile.branch_id;
    end if;

    select r.*
    into v_role
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = v_user_id;

    if not v_profile.is_active then
        v_access_state := 'PROFILE_INACTIVE';
    elsif v_role.id is null then
        v_access_state := 'NO_ROLE';
    else
        v_access_state := 'ACTIVE';

        select coalesce(
            pg_catalog.jsonb_agg(pm.name order by pm.name),
            '[]'::pg_catalog.jsonb
        )
        into v_capabilities
        from public.role_permissions rp
        join public.permissions pm on pm.name = rp.permission_name
        where rp.role_id = v_role.id;
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'userId', v_user_id,
        'accessState', v_access_state,
        'profile', pg_catalog.jsonb_build_object(
            'fullName', v_profile.full_name,
            'avatarPath', v_profile.avatar_path,
            'isActive', v_profile.is_active
        ),
        'branch', case
            when v_branch.id is null then null
            else pg_catalog.jsonb_build_object(
                'id', v_branch.id,
                'code', v_branch.code,
                'name', v_branch.name,
                'isActive', v_branch.is_active
            )
        end,
        'role', case
            when v_role.id is null then null
            else pg_catalog.jsonb_build_object(
                'name', v_role.name,
                'displayName', v_role.display_name
            )
        end,
        'capabilities', v_capabilities
    );
end;
$$;

revoke all on function public.get_my_access_context() from public;
revoke all on function public.get_my_access_context() from anon;
revoke all on function public.get_my_access_context() from authenticated;

grant execute on function public.get_my_access_context() to authenticated;

comment on function public.get_my_access_context() is
'Returns the effective context of the authenticated user for presentation and authorization decisions; it does not grant permissions.';

commit;
