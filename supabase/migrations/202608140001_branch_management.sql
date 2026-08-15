begin;

create or replace function public.create_branch(
    p_code pg_catalog.text,
    p_name pg_catalog.text
)
returns public.branches
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_code pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_code));
    v_name pg_catalog.text := pg_catalog.regexp_replace(pg_catalog.btrim(p_name), '\s+', ' ', 'g');
    v_branch public.branches%rowtype;
begin
    if v_actor_id is null
       or not exists (
           select 1 from public.profiles p
           where p.id = v_actor_id and p.is_active
       )
       or not public.has_permission('MANAGE_BRANCHES') then
        raise exception using errcode = '42501', message = 'Branch management is not allowed';
    end if;

    if v_code is null
       or pg_catalog.char_length(v_code) not between 2 and 24
       or v_code !~ '^[A-Z0-9][A-Z0-9_-]{1,23}$'
       or v_name is null
       or pg_catalog.char_length(v_name) not between 2 and 120 then
        raise exception using errcode = '22023', message = 'Branch data is invalid';
    end if;

    insert into public.branches (code, name)
    values (v_code, v_name)
    returning * into v_branch;

    return v_branch;
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'Branch code is unavailable';
end;
$$;

create or replace function public.update_branch(
    p_branch_id pg_catalog.uuid,
    p_code pg_catalog.text,
    p_name pg_catalog.text
)
returns public.branches
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_code pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_code));
    v_name pg_catalog.text := pg_catalog.regexp_replace(pg_catalog.btrim(p_name), '\s+', ' ', 'g');
    v_branch public.branches%rowtype;
begin
    if v_actor_id is null
       or not exists (
           select 1 from public.profiles p
           where p.id = v_actor_id and p.is_active
       )
       or not public.has_permission('MANAGE_BRANCHES') then
        raise exception using errcode = '42501', message = 'Branch management is not allowed';
    end if;

    if p_branch_id is null
       or v_code is null
       or pg_catalog.char_length(v_code) not between 2 and 24
       or v_code !~ '^[A-Z0-9][A-Z0-9_-]{1,23}$'
       or v_name is null
       or pg_catalog.char_length(v_name) not between 2 and 120 then
        raise exception using errcode = '22023', message = 'Branch data is invalid';
    end if;

    update public.branches b
    set code = v_code,
        name = v_name
    where b.id = p_branch_id
    returning b.* into v_branch;

    if not found then
        raise exception using errcode = '22023', message = 'Branch is unavailable';
    end if;

    return v_branch;
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'Branch code is unavailable';
end;
$$;

create or replace function public.set_branch_active(
    p_branch_id pg_catalog.uuid,
    p_is_active pg_catalog.bool
)
returns public.branches
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch public.branches%rowtype;
begin
    if v_actor_id is null
       or not exists (
           select 1 from public.profiles p
           where p.id = v_actor_id and p.is_active
       )
       or not public.has_permission('MANAGE_BRANCHES') then
        raise exception using errcode = '42501', message = 'Branch management is not allowed';
    end if;

    if p_branch_id is null or p_is_active is null then
        raise exception using errcode = '22023', message = 'Branch data is invalid';
    end if;

    select b.* into v_branch
    from public.branches b
    where b.id = p_branch_id
    for update;

    if not found then
        raise exception using errcode = '22023', message = 'Branch is unavailable';
    end if;

    if v_branch.is_active = p_is_active then
        return v_branch;
    end if;

    if not p_is_active and (
        exists (
            select 1 from public.profiles p
            where p.branch_id = p_branch_id and p.is_active
        )
        or exists (
            select 1 from public.sales s
            where s.branch_id = p_branch_id
              and s.status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')
        )
    ) then
        raise exception using errcode = '55000', message = 'Branch cannot be deactivated';
    end if;

    update public.branches b
    set is_active = p_is_active
    where b.id = p_branch_id
    returning b.* into v_branch;

    return v_branch;
end;
$$;

create or replace function public.assign_user_branch(
    p_user_id pg_catalog.uuid,
    p_branch_id pg_catalog.uuid
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
    v_current_branch_id pg_catalog.uuid;
begin
    if v_actor_id is null
       or not exists (
           select 1 from public.profiles p
           where p.id = v_actor_id and p.is_active
       )
       or not public.has_permission('MANAGE_USERS') then
        raise exception using errcode = '42501', message = 'Branch assignment is not allowed';
    end if;

    select r.name into v_actor_role
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = v_actor_id;

    select p.branch_id, r.name
    into v_current_branch_id, v_target_role
    from public.profiles p
    left join public.user_roles ur on ur.user_id = p.id
    left join public.roles r on r.id = ur.role_id
    where p.id = p_user_id and p.is_active
    for update of p;

    if not found then
        raise exception using errcode = '22023', message = 'Target profile is unavailable';
    end if;

    if v_actor_role = 'ADMIN' and v_target_role = 'OWNER' then
        raise exception using errcode = '42501', message = 'Branch assignment is not allowed';
    end if;

    perform 1
    from public.branches b
    where b.id = p_branch_id and b.is_active
    for share;

    if not found then
        raise exception using errcode = '22023', message = 'Target branch is unavailable';
    end if;

    if v_current_branch_id = p_branch_id then
        return;
    end if;

    update public.profiles p
    set branch_id = p_branch_id
    where p.id = p_user_id;
end;
$$;

comment on function public.create_branch(pg_catalog.text, pg_catalog.text)
is 'Creates an active branch after normalizing its code and name.';
comment on function public.update_branch(pg_catalog.uuid, pg_catalog.text, pg_catalog.text)
is 'Updates branch identity fields without changing its active state.';
comment on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool)
is 'Changes branch availability; reactivation preserves the existing branch and is idempotent.';
comment on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid)
is 'Assigns an active profile to an active branch; repeated assignment is idempotent.';

revoke all on function public.create_branch(pg_catalog.text, pg_catalog.text) from public;
revoke all on function public.create_branch(pg_catalog.text, pg_catalog.text) from anon;
grant execute on function public.create_branch(pg_catalog.text, pg_catalog.text) to authenticated;

revoke all on function public.update_branch(pg_catalog.uuid, pg_catalog.text, pg_catalog.text) from public;
revoke all on function public.update_branch(pg_catalog.uuid, pg_catalog.text, pg_catalog.text) from anon;
grant execute on function public.update_branch(pg_catalog.uuid, pg_catalog.text, pg_catalog.text) to authenticated;

revoke all on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool) from public;
revoke all on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool) from anon;
grant execute on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool) to authenticated;

revoke all on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid) from public;
revoke all on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid) from anon;
grant execute on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid) to authenticated;

commit;
