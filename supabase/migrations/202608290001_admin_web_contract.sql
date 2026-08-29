begin;

create or replace function public.get_admin_branches(
    p_limit pg_catalog.int4 default 50,
    p_after_code pg_catalog.text default null,
    p_after_id pg_catalog.uuid default null,
    p_include_inactive pg_catalog.bool default false
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_after_code pg_catalog.text := nullif(pg_catalog.upper(pg_catalog.btrim(p_after_code)), '');
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
begin
    if v_actor_id is null
       or not exists (select 1 from public.profiles p where p.id = v_actor_id and p.is_active)
       or not (
           public.has_permission('MANAGE_BRANCHES')
           or public.has_permission('MANAGE_USERS')
       ) then
        raise exception using errcode = 'P0001', message = 'ADMIN_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 100
       or p_include_inactive is null
       or ((p_after_code is null) <> (p_after_id is null))
       or (p_after_code is not null and (
           v_after_code is null
           or pg_catalog.char_length(v_after_code) > 24
           or v_after_code !~ '^[A-Z0-9][A-Z0-9_-]{1,23}$'
       )) then
        raise exception using errcode = 'P0001', message = 'ADMIN_BRANCH_QUERY_INVALID';
    end if;

    with candidate_rows as (
        select
            b.id,
            b.code,
            b.name,
            b.is_active,
            b.updated_at,
            (
                select pg_catalog.count(*)
                from public.profiles staff
                where staff.branch_id = b.id and staff.is_active
            ) as active_staff_count,
            (
                select pg_catalog.count(*)
                from public.sales sale
                where sale.branch_id = b.id
                  and sale.status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')
            ) as pending_sale_count
        from public.branches b
        where (p_include_inactive or b.is_active)
          and (
              v_after_code is null
              or (b.code collate "C", b.id) > (v_after_code collate "C", p_after_id)
          )
        order by b.code collate "C" asc, b.id asc
        limit p_limit + 1
    ),
    page_rows as (
        select * from candidate_rows
        order by code collate "C" asc, id asc
        limit p_limit
    )
    select
        coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', row_data.id,
                        'code', row_data.code,
                        'name', row_data.name,
                        'isActive', row_data.is_active,
                        'activeStaffCount', row_data.active_staff_count,
                        'pendingSaleCount', row_data.pending_sale_count,
                        'updatedAt', row_data.updated_at
                    )
                    order by row_data.code collate "C" asc, row_data.id asc
                )
                from page_rows row_data
            ),
            '[]'::pg_catalog.jsonb
        ),
        (select pg_catalog.count(*) > p_limit from candidate_rows)
    into v_items, v_has_more;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', v_items,
        'page', pg_catalog.jsonb_build_object(
            'limit', p_limit,
            'hasMore', v_has_more,
            'nextCursor', case
                when v_has_more then pg_catalog.jsonb_build_object(
                    'code', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'code',
                    'id', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'id'
                )
                else null
            end
        ),
        'serverTime', v_now
    );
end;
$$;

create or replace function public.get_admin_staff(
    p_limit pg_catalog.int4 default 50,
    p_after_full_name pg_catalog.text default null,
    p_after_id pg_catalog.uuid default null,
    p_search pg_catalog.text default null,
    p_branch_id pg_catalog.uuid default null,
    p_include_inactive pg_catalog.bool default false
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_after_name pg_catalog.text := nullif(
        pg_catalog.lower(pg_catalog.regexp_replace(pg_catalog.btrim(p_after_full_name), '[[:space:]]+', ' ', 'g')),
        ''
    );
    v_search pg_catalog.text := nullif(
        pg_catalog.lower(pg_catalog.regexp_replace(pg_catalog.btrim(p_search), '[[:space:]]+', ' ', 'g')),
        ''
    );
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
begin
    if v_actor_id is null
       or not exists (select 1 from public.profiles p where p.id = v_actor_id and p.is_active)
       or not public.has_permission('MANAGE_USERS') then
        raise exception using errcode = 'P0001', message = 'ADMIN_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 100
       or p_include_inactive is null
       or ((p_after_full_name is null) <> (p_after_id is null))
       or (p_after_full_name is not null and (
           v_after_name is null or pg_catalog.char_length(v_after_name) > 160
       ))
       or (p_search is not null and (
           v_search is null or pg_catalog.char_length(v_search) > 80
       )) then
        raise exception using errcode = 'P0001', message = 'ADMIN_STAFF_QUERY_INVALID';
    end if;

    with staff_rows as (
        select
            p.id,
            pg_catalog.regexp_replace(pg_catalog.btrim(p.full_name), '[[:space:]]+', ' ', 'g') as full_name,
            p.is_active,
            p.updated_at,
            b.id as branch_id,
            b.code as branch_code,
            b.name as branch_name,
            b.is_active as branch_is_active,
            r.name as role_name,
            r.display_name as role_display_name
        from public.profiles p
        left join public.branches b on b.id = p.branch_id
        left join public.user_roles ur on ur.user_id = p.id
        left join public.roles r on r.id = ur.role_id
        where (p_include_inactive or p.is_active)
          and (p_branch_id is null or p.branch_id = p_branch_id)
    ),
    candidate_rows as (
        select *
        from staff_rows staff
        where (v_search is null or pg_catalog.lower(staff.full_name) like '%' || v_search || '%')
          and (
              v_after_name is null
              or (pg_catalog.lower(staff.full_name) collate "C", staff.id) >
                 (v_after_name collate "C", p_after_id)
          )
        order by pg_catalog.lower(staff.full_name) collate "C" asc, staff.id asc
        limit p_limit + 1
    ),
    page_rows as (
        select * from candidate_rows
        order by pg_catalog.lower(full_name) collate "C" asc, id asc
        limit p_limit
    )
    select
        coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', row_data.id,
                        'fullName', row_data.full_name,
                        'isActive', row_data.is_active,
                        'branch', case when row_data.branch_id is null then null else
                            pg_catalog.jsonb_build_object(
                                'id', row_data.branch_id,
                                'code', row_data.branch_code,
                                'name', row_data.branch_name,
                                'isActive', row_data.branch_is_active
                            )
                        end,
                        'role', case when row_data.role_name is null then null else
                            pg_catalog.jsonb_build_object(
                                'name', row_data.role_name,
                                'displayName', row_data.role_display_name
                            )
                        end,
                        'updatedAt', row_data.updated_at
                    )
                    order by pg_catalog.lower(row_data.full_name) collate "C" asc, row_data.id asc
                )
                from page_rows row_data
            ),
            '[]'::pg_catalog.jsonb
        ),
        (select pg_catalog.count(*) > p_limit from candidate_rows)
    into v_items, v_has_more;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', v_items,
        'page', pg_catalog.jsonb_build_object(
            'limit', p_limit,
            'hasMore', v_has_more,
            'nextCursor', case
                when v_has_more then pg_catalog.jsonb_build_object(
                    'fullName', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'fullName',
                    'id', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'id'
                )
                else null
            end
        ),
        'serverTime', v_now
    );
end;
$$;

alter function public.get_admin_branches(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.bool
) owner to postgres;
alter function public.get_admin_staff(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.text,
    pg_catalog.uuid, pg_catalog.bool
) owner to postgres;

revoke all on function public.get_admin_branches(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.bool
) from public, anon, authenticated, service_role;
revoke all on function public.get_admin_staff(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.text,
    pg_catalog.uuid, pg_catalog.bool
) from public, anon, authenticated, service_role;

grant execute on function public.get_admin_branches(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.bool
) to authenticated;
grant execute on function public.get_admin_staff(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.text,
    pg_catalog.uuid, pg_catalog.bool
) to authenticated;

comment on function public.get_admin_branches(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.bool
) is 'Versioned read-only branch presentation contract for authorized administration clients.';
comment on function public.get_admin_staff(
    pg_catalog.int4, pg_catalog.text, pg_catalog.uuid, pg_catalog.text,
    pg_catalog.uuid, pg_catalog.bool
) is 'Versioned read-only staff presentation contract. Omits auth identities, email and credentials.';

commit;
