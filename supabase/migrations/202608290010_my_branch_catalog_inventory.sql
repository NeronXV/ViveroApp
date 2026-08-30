begin;

create or replace function public.get_my_branch_catalog_inventory()
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_items pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('VIEW_CATALOG') then
        raise exception using errcode = 'P0001', message = 'CATALOG_INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = 'P0001', message = 'CATALOG_INVENTORY_UNAUTHORIZED';
    end if;

    select coalesce(
        pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'productId', p.id,
                'totalQuantity', coalesce(b.total_quantity, 0),
                'updatedAt', b.updated_at
            ) order by p.id
        ),
        '[]'::pg_catalog.jsonb
    ) into v_items
    from public.products p
    left join public.inventory_balances b
      on b.product_id = p.id
     and b.branch_id = v_branch_id
    where p.is_active;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branchId', v_branch_id,
        'items', v_items
    );
end;
$$;

alter function public.get_my_branch_catalog_inventory() owner to postgres;

revoke all on function public.get_my_branch_catalog_inventory() from public;
revoke all on function public.get_my_branch_catalog_inventory() from anon;
revoke all on function public.get_my_branch_catalog_inventory() from authenticated;
grant execute on function public.get_my_branch_catalog_inventory() to authenticated;

comment on function public.get_my_branch_catalog_inventory() is
'Returns active-product inventory balances only for the authenticated user active branch.';

commit;
