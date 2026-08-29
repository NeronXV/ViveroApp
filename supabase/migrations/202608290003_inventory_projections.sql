begin;

-- 1. Consulta de existencias administrativa
create or replace function public.get_admin_inventory_balances(
    p_branch_id pg_catalog.uuid default null,
    p_limit pg_catalog.int4 default 50,
    p_after_product_id pg_catalog.uuid default null,
    p_search pg_catalog.text default null,
    p_include_zero_stock pg_catalog.bool default false
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
    v_search pg_catalog.text := nullif(pg_catalog.lower(pg_catalog.btrim(p_search)), '');
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
begin
    if v_actor_id is null or not exists (select 1 from public.profiles where id = v_actor_id and is_active) then
        raise exception using errcode = 'P0001', message = 'ADMIN_UNAUTHORIZED';
    end if;

    select branch_id into v_actor_branch_id from public.profiles where id = v_actor_id;

    with candidate_rows as (
        select
            b.branch_id,
            b.product_id,
            p.common_name as product_name,
            p.internal_code as product_code,
            p.unit as product_unit,
            b.total_quantity,
            p.minimum_stock,
            b.updated_at
        from public.inventory_balances b
        join public.products p on p.id = b.product_id
        where (p_branch_id is null or b.branch_id = p_branch_id)
          -- Restriccion: si no es ADMIN/OWNER, solo puede ver su sucursal (o todas si tiene permiso expreso)
          and (
              public.has_permission('VIEW_REPORTS')
              or b.branch_id = v_actor_branch_id
          )
          and (p_include_zero_stock or b.total_quantity > 0)
          and (v_search is null or pg_catalog.lower(p.common_name) like '%' || v_search || '%' or pg_catalog.lower(p.internal_code) like '%' || v_search || '%')
          and (p_after_product_id is null or b.product_id > p_after_product_id)
        order by b.product_id asc
        limit p_limit + 1
    ),
    page_rows as (
        select * from candidate_rows limit p_limit
    )
    select
        coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'branchId', r.branch_id,
            'productId', r.product_id,
            'productName', r.product_name,
            'productCode', r.product_code,
            'productUnit', r.product_unit,
            'totalQuantity', r.total_quantity,
            'minimumStock', r.minimum_stock,
            'isLowStock', r.total_quantity <= r.minimum_stock,
            'updatedAt', r.updated_at
        )), '[]'::jsonb),
        (select pg_catalog.count(*) > p_limit from candidate_rows)
    into v_items, v_has_more;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', v_items,
        'hasMore', v_has_more
    );
end;
$$;

-- 2. Alertas de bajo inventario
create or replace function public.get_low_inventory_alerts(
    p_branch_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    return (
        select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'branchId', b.branch_id,
            'productId', b.product_id,
            'productName', p.common_name,
            'currentStock', b.total_quantity,
            'minimumStock', p.minimum_stock
        )), '[]'::jsonb)
        from public.inventory_balances b
        join public.products p on p.id = b.product_id
        where (p_branch_id is null or b.branch_id = p_branch_id)
          and b.total_quantity <= p.minimum_stock
          and p.is_active
    );
end;
$$;

-- Privilegios
revoke all on function public.get_admin_inventory_balances, public.get_low_inventory_alerts from public, anon, authenticated;
grant execute on function public.get_admin_inventory_balances, public.get_low_inventory_alerts to authenticated;

commit;
