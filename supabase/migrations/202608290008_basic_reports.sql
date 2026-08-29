begin;

-- 1. Reporte de ventas diarias por sucursal
create or replace function public.get_report_daily_sales(
    p_branch_id pg_catalog.uuid default null,
    p_start_date pg_catalog.date default null,
    p_end_date pg_catalog.date default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
    v_start pg_catalog.timestamptz := pg_catalog.coalesce(p_start_date::pg_catalog.timestamptz, pg_catalog.now() - interval '30 days');
    v_end pg_catalog.timestamptz := pg_catalog.coalesce((p_end_date + 1)::pg_catalog.timestamptz, pg_catalog.now());
begin
    if v_actor_id is null or not public.has_permission('VIEW_REPORTS') then
        raise exception using errcode = '42501', message = 'Unauthorized to view reports';
    end if;

    select branch_id into v_actor_branch_id from public.profiles where id = v_actor_id;

    return (
        with daily_stats as (
            select
                s.branch_id,
                b.name as branch_name,
                pg_catalog.date_trunc('day', s.created_at) as sale_day,
                pg_catalog.count(*) as total_sales,
                pg_catalog.sum(s.total_cents) as revenue_cents,
                pg_catalog.sum(s.discount_cents) as total_discounts_cents
            from public.sales s
            join public.branches b on b.id = s.branch_id
            where s.status = 'PAID'
              and (p_branch_id is null or s.branch_id = p_branch_id)
              -- Restriccion: si no tiene VIEW_ALL_SALES, solo ve su sucursal
              and (public.has_permission('VIEW_ALL_SALES') or s.branch_id = v_actor_branch_id)
              and s.created_at >= v_start
              and s.created_at < v_end
            group by 1, 2, 3
        )
        select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'branchId', d.branch_id,
            'branchName', d.branch_name,
            'day', d.sale_day,
            'salesCount', d.total_sales,
            'revenueCents', d.revenue_cents,
            'discountCents', d.total_discounts_cents
        ) order by d.sale_day desc, d.branch_name asc), '[]'::jsonb)
        from daily_stats d
    );
end;
$$;

-- 2. Reporte de productos más vendidos
create or replace function public.get_report_top_products(
    p_branch_id pg_catalog.uuid default null,
    p_limit pg_catalog.int4 default 10
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
begin
    if v_actor_id is null or not public.has_permission('VIEW_REPORTS') then
        raise exception using errcode = '42501', message = 'Unauthorized to view reports';
    end if;

    select branch_id into v_actor_branch_id from public.profiles where id = v_actor_id;

    return (
        with product_stats as (
            select
                si.product_id,
                si.product_name,
                si.internal_code,
                pg_catalog.sum(si.quantity) as total_quantity,
                pg_catalog.sum(si.line_total_cents) as total_revenue_cents
            from public.sale_items si
            join public.sales s on s.id = si.sale_id
            where s.status = 'PAID'
              and (p_branch_id is null or s.branch_id = p_branch_id)
              and (public.has_permission('VIEW_ALL_SALES') or s.branch_id = v_actor_branch_id)
            group by 1, 2, 3
            order by total_quantity desc
            limit p_limit
        )
        select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'productId', p.product_id,
            'productName', p.product_name,
            'productCode', p.internal_code,
            'totalQuantity', p.total_quantity,
            'totalRevenueCents', p.total_revenue_cents
        )), '[]'::jsonb)
        from product_stats p
    );
end;
$$;

-- 3. Privilegios
revoke all on function public.get_report_daily_sales, public.get_report_top_products from public, anon, authenticated;
grant execute on function public.get_report_daily_sales, public.get_report_top_products to authenticated;

comment on function public.get_report_daily_sales is 'Summary of daily revenue and sales volume by branch within a date range.';
comment on function public.get_report_top_products is 'Listing of best selling products based on total quantity sold.';

commit;
