begin;

create or replace function public.get_my_recent_sales(
    p_limit pg_catalog.int4 default 20,
    p_after_created_at pg_catalog.timestamptz default null,
    p_after_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
begin
    if v_actor_id is null
       or not exists (select 1 from public.profiles p where p.id = v_actor_id and p.is_active)
       or not public.has_permission('VIEW_OWN_SALES') then
        raise exception using errcode = 'P0001', message = 'MY_SALES_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 50
       or ((p_after_created_at is null) <> (p_after_id is null)) then
        raise exception using errcode = 'P0001', message = 'MY_SALES_QUERY_INVALID';
    end if;

    with candidate_rows as (
        select
            s.id,
            s.folio,
            s.status,
            s.created_at,
            s.updated_at,
            s.subtotal_cents,
            s.discount_cents,
            s.total_cents,
            (
                select pg_catalog.count(*)::pg_catalog.int4
                from public.sale_items si
                where si.sale_id = s.id
            ) as item_count,
            (
                select coalesce(pg_catalog.sum(si.quantity), 0)::pg_catalog.int4
                from public.sale_items si
                where si.sale_id = s.id
            ) as total_quantity,
            (
                select sp.created_at
                from public.sale_payments sp
                where sp.sale_id = s.id
                limit 1
            ) as paid_at
        from public.sales s
        where s.created_by = v_actor_id
          and (
              p_after_created_at is null
              or (s.created_at, s.id) < (p_after_created_at, p_after_id)
          )
        order by s.created_at desc, s.id desc
        limit p_limit + 1
    ),
    page_rows as (
        select * from candidate_rows
        order by created_at desc, id desc
        limit p_limit
    )
    select
        coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', row_data.id,
                        'folio', row_data.folio,
                        'status', row_data.status::pg_catalog.text,
                        'createdAt', row_data.created_at,
                        'updatedAt', row_data.updated_at,
                        'subtotalCents', row_data.subtotal_cents,
                        'discountCents', row_data.discount_cents,
                        'totalCents', row_data.total_cents,
                        'itemCount', row_data.item_count,
                        'totalQuantity', row_data.total_quantity,
                        'paidAt', row_data.paid_at
                    )
                    order by row_data.created_at desc, row_data.id desc
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
                    'createdAt', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'createdAt',
                    'id', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'id'
                )
                else null
            end
        ),
        'serverTime', v_now
    );
end;
$$;

alter function public.get_my_recent_sales(pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) owner to postgres;

revoke all on function public.get_my_recent_sales(pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) from public, anon, service_role;
grant execute on function public.get_my_recent_sales(pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) to authenticated;

comment on function public.get_my_recent_sales(pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) is 'Versioned contract for sellers to query their own recent sales with authoritative server state.';

commit;
