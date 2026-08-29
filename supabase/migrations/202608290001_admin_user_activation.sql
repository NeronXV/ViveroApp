begin;

create or replace function public.set_user_active(
    p_user_id pg_catalog.uuid,
    p_is_active pg_catalog.bool
)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_role pg_catalog.text;
    v_target_role pg_catalog.text;
begin
    if v_actor_id is null
       or not exists (
           select 1 from public.profiles p
           where p.id = v_actor_id and p.is_active
       )
       or not public.has_permission('MANAGE_USERS') then
        raise exception using errcode = '42501', message = 'User management is not allowed';
    end if;

    if p_user_id is null or p_is_active is null then
        raise exception using errcode = '22023', message = 'User data is invalid';
    end if;

    select r.name into v_actor_role
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = v_actor_id;

    select r.name into v_target_role
    from public.profiles p
    left join public.user_roles ur on ur.user_id = p.id
    left join public.roles r on r.id = ur.role_id
    where p.id = p_user_id
    for update of p;

    if not found then
        raise exception using errcode = '22023', message = 'Target profile is unavailable';
    end if;

    if v_actor_role = 'ADMIN' and v_target_role = 'OWNER' then
        raise exception using errcode = '42501', message = 'ADMIN cannot modify OWNER status';
    end if;

    if v_target_role = 'OWNER' and not p_is_active and (
        select pg_catalog.count(*)
        from public.user_roles ur
        join public.roles r on r.id = ur.role_id
        join public.profiles p on p.id = ur.user_id
        where r.name = 'OWNER' and p.is_active
    ) <= 1 then
        raise exception using errcode = '42501', message = 'The last active OWNER cannot be deactivated';
    end if;

    update public.profiles
    set is_active = p_is_active
    where id = p_user_id;
end;
$$;

comment on function public.set_user_active(pg_catalog.uuid, pg_catalog.bool)
is 'Changes user activation status. ADMIN cannot modify OWNERs. The last active OWNER cannot be deactivated.';

revoke all on function public.set_user_active(pg_catalog.uuid, pg_catalog.bool) from public, anon, authenticated;
grant execute on function public.set_user_active(pg_catalog.uuid, pg_catalog.bool) to authenticated;

commit;
