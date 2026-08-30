begin;

create table public.inventory_counts (
    id pg_catalog.uuid primary key,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    product_id pg_catalog.uuid not null references public.products(id) on delete restrict,
    location_id pg_catalog.uuid references public.inventory_locations(id) on delete restrict,
    counted_quantity pg_catalog.numeric(14, 3) not null check (counted_quantity >= 0),
    previous_quantity pg_catalog.numeric(14, 3) not null check (previous_quantity >= 0),
    adjustment_quantity pg_catalog.numeric(14, 3) not null,
    reason pg_catalog.text not null check (pg_catalog.char_length(pg_catalog.btrim(reason)) between 3 and 240),
    counted_by pg_catalog.uuid not null references auth.users(id) on delete restrict,
    created_at pg_catalog.timestamptz not null default pg_catalog.now()
);

create index inventory_counts_branch_product_created_idx
on public.inventory_counts(branch_id, product_id, created_at desc, id desc);

create unique index inventory_manual_movement_idempotency_idx
on public.inventory_movements(reference_id)
where reference_id is not null
  and movement_type in ('RECEPTION', 'ADJUSTMENT_ADD', 'ADJUSTMENT_SUB');

alter table public.inventory_counts enable row level security;
revoke all on table public.inventory_counts from public, anon, authenticated;

create or replace function public.get_my_inventory_dashboard(
    p_limit pg_catalog.int4 default 100,
    p_after_product_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
begin
    if v_actor_id is null or not (
        public.has_permission('MANAGE_INVENTORY')
        or public.has_permission('VIEW_INVENTORY_ALERTS')
    ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 100 then
        raise exception using errcode = 'P0001', message = 'INVENTORY_QUERY_INVALID';
    end if;

    with candidates as materialized (
        select
            p.id as product_id,
            p.common_name as product_name,
            p.internal_code as product_code,
            p.unit as product_unit,
            coalesce(b.total_quantity, 0) as total_quantity,
            p.minimum_stock,
            coalesce(b.total_quantity, 0) <= p.minimum_stock as is_low_stock,
            b.updated_at as balance_updated_at
        from public.products p
        left join public.inventory_balances b
          on b.product_id = p.id and b.branch_id = v_branch_id
        where p.is_active
          and (p_after_product_id is null or p.id > p_after_product_id)
        order by p.id
        limit p_limit + 1
    ), page_rows as (
        select * from candidates order by product_id limit p_limit
    )
    select
        coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'productId', r.product_id,
                'productName', r.product_name,
                'productCode', r.product_code,
                'productUnit', r.product_unit,
                'totalQuantity', r.total_quantity,
                'minimumStock', r.minimum_stock,
                'isLowStock', r.is_low_stock,
                'balanceUpdatedAt', r.balance_updated_at
            ) order by r.product_id
        ), '[]'::pg_catalog.jsonb),
        (select pg_catalog.count(*) > p_limit from candidates)
    into v_items, v_has_more
    from page_rows r;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branchId', v_branch_id,
        'items', v_items,
        'hasMore', v_has_more,
        'nextProductId', case when v_has_more then v_items -> -1 ->> 'productId' else null end
    );
end;
$$;

create or replace function public.record_inventory_reception(
    p_product_id pg_catalog.uuid,
    p_quantity pg_catalog.numeric,
    p_notes pg_catalog.text,
    p_idempotency_key pg_catalog.uuid
) returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_notes pg_catalog.text := nullif(pg_catalog.btrim(p_notes), '');
    v_movement public.inventory_movements%rowtype;
    v_total pg_catalog.numeric;
    v_inserted pg_catalog.bool := false;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or p_product_id is null or p_idempotency_key is null
       or p_quantity is null or p_quantity <= 0
       or p_quantity <> pg_catalog.trunc(p_quantity)
       or pg_catalog.char_length(coalesce(v_notes, '')) > 240
       or not exists (select 1 from public.products p where p.id = p_product_id and p.is_active) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_RECEPTION_INVALID';
    end if;

    insert into public.inventory_movements (
        branch_id, product_id, movement_type, quantity, reference_id, notes, created_by
    ) values (
        v_branch_id, p_product_id, 'RECEPTION', p_quantity,
        p_idempotency_key, v_notes, v_actor_id
    )
    on conflict do nothing
    returning * into v_movement;

    v_inserted := found;
    if not v_inserted then
        select * into v_movement
        from public.inventory_movements m
        where m.reference_id = p_idempotency_key
          and m.movement_type = 'RECEPTION';

        if not found
           or v_movement.branch_id <> v_branch_id
           or v_movement.product_id <> p_product_id
           or v_movement.quantity <> p_quantity
           or v_movement.created_by <> v_actor_id
           or v_movement.notes is distinct from v_notes then
            raise exception using errcode = 'P0001', message = 'INVENTORY_IDEMPOTENCY_CONFLICT';
        end if;
    end if;

    select b.total_quantity into strict v_total
    from public.inventory_balances b
    where b.branch_id = v_branch_id and b.product_id = p_product_id;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'idempotentReplay', not v_inserted,
        'movementId', v_movement.id,
        'productId', p_product_id,
        'quantity', p_quantity,
        'totalQuantity', v_total
    );
end;
$$;

create or replace function public.reconcile_inventory_count(
    p_product_id pg_catalog.uuid,
    p_counted_quantity pg_catalog.numeric,
    p_reason pg_catalog.text,
    p_idempotency_key pg_catalog.uuid,
    p_location_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_reason pg_catalog.text := pg_catalog.btrim(p_reason);
    v_previous pg_catalog.numeric;
    v_adjustment pg_catalog.numeric;
    v_count public.inventory_counts%rowtype;
    v_inserted pg_catalog.bool := false;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or p_product_id is null or p_idempotency_key is null
       or p_counted_quantity is null or p_counted_quantity < 0
       or p_counted_quantity <> pg_catalog.trunc(p_counted_quantity)
       or v_reason is null or pg_catalog.char_length(v_reason) not between 3 and 240
       or not exists (select 1 from public.products p where p.id = p_product_id and p.is_active)
       or (p_location_id is not null and not exists (
           select 1 from public.inventory_locations l
           where l.id = p_location_id and l.branch_id = v_branch_id and l.is_active
       )) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_COUNT_INVALID';
    end if;

    select * into v_count from public.inventory_counts c where c.id = p_idempotency_key;
    if found then
        if v_count.branch_id <> v_branch_id
           or v_count.product_id <> p_product_id
           or v_count.location_id is distinct from p_location_id
           or v_count.counted_quantity <> p_counted_quantity
           or v_count.reason <> v_reason
           or v_count.counted_by <> v_actor_id then
            raise exception using errcode = 'P0001', message = 'INVENTORY_IDEMPOTENCY_CONFLICT';
        end if;
        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'idempotentReplay', true,
            'countId', v_count.id,
            'productId', v_count.product_id,
            'previousQuantity', v_count.previous_quantity,
            'countedQuantity', v_count.counted_quantity,
            'adjustmentQuantity', v_count.adjustment_quantity,
            'totalQuantity', v_count.counted_quantity
        );
    end if;

    insert into public.inventory_balances (branch_id, product_id, total_quantity)
    values (v_branch_id, p_product_id, 0)
    on conflict (branch_id, product_id) do nothing;

    select b.total_quantity into strict v_previous
    from public.inventory_balances b
    where b.branch_id = v_branch_id and b.product_id = p_product_id
    for update;

    v_adjustment := p_counted_quantity - v_previous;

    insert into public.inventory_counts (
        id, branch_id, product_id, location_id, counted_quantity,
        previous_quantity, adjustment_quantity, reason, counted_by
    ) values (
        p_idempotency_key, v_branch_id, p_product_id, p_location_id,
        p_counted_quantity, v_previous, v_adjustment, v_reason, v_actor_id
    )
    on conflict (id) do nothing
    returning * into v_count;

    v_inserted := found;
    if not v_inserted then
        select * into strict v_count from public.inventory_counts c where c.id = p_idempotency_key;
        if v_count.branch_id <> v_branch_id
           or v_count.product_id <> p_product_id
           or v_count.location_id is distinct from p_location_id
           or v_count.counted_quantity <> p_counted_quantity
           or v_count.reason <> v_reason
           or v_count.counted_by <> v_actor_id then
            raise exception using errcode = 'P0001', message = 'INVENTORY_IDEMPOTENCY_CONFLICT';
        end if;
    elsif v_adjustment <> 0 then
        insert into public.inventory_movements (
            branch_id, location_id, product_id, movement_type, quantity,
            reference_id, notes, created_by
        ) values (
            v_branch_id, p_location_id, p_product_id,
            case when v_adjustment > 0 then 'ADJUSTMENT_ADD'::public.inventory_movement_type
                 else 'ADJUSTMENT_SUB'::public.inventory_movement_type end,
            v_adjustment, p_idempotency_key, v_reason, v_actor_id
        );
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'idempotentReplay', not v_inserted,
        'countId', v_count.id,
        'productId', v_count.product_id,
        'previousQuantity', v_count.previous_quantity,
        'countedQuantity', v_count.counted_quantity,
        'adjustmentQuantity', v_count.adjustment_quantity,
        'totalQuantity', v_count.counted_quantity
    );
end;
$$;

create or replace function public.get_my_inventory_history(
    p_product_id pg_catalog.uuid default null,
    p_limit pg_catalog.int4 default 50,
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
    v_branch_id pg_catalog.uuid;
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
begin
    if v_actor_id is null or not (
        public.has_permission('MANAGE_INVENTORY')
        or public.has_permission('VIEW_INVENTORY_ALERTS')
    ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or p_limit is null or p_limit < 1 or p_limit > 100
       or ((p_after_created_at is null) <> (p_after_id is null)) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_QUERY_INVALID';
    end if;

    with candidates as materialized (
        select
            m.id,
            m.product_id,
            p.common_name as product_name,
            p.internal_code as product_code,
            m.movement_type,
            m.quantity,
            m.notes,
            m.created_at,
            nullif(pg_catalog.btrim(actor.full_name), '') as created_by_label
        from public.inventory_movements m
        join public.products p on p.id = m.product_id
        left join public.profiles actor on actor.id = m.created_by
        where m.branch_id = v_branch_id
          and (p_product_id is null or m.product_id = p_product_id)
          and (
              p_after_created_at is null
              or m.created_at < p_after_created_at
              or (m.created_at = p_after_created_at and m.id < p_after_id)
          )
        order by m.created_at desc, m.id desc
        limit p_limit + 1
    ), page_rows as (
        select * from candidates order by created_at desc, id desc limit p_limit
    )
    select
        coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', r.id,
                'productId', r.product_id,
                'productName', r.product_name,
                'productCode', r.product_code,
                'movementType', r.movement_type::pg_catalog.text,
                'quantity', r.quantity,
                'notes', r.notes,
                'createdAt', r.created_at,
                'createdByLabel', r.created_by_label
            ) order by r.created_at desc, r.id desc
        ), '[]'::pg_catalog.jsonb),
        (select pg_catalog.count(*) > p_limit from candidates)
    into v_items, v_has_more
    from page_rows r;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branchId', v_branch_id,
        'items', v_items,
        'hasMore', v_has_more,
        'nextCursor', case when v_has_more then pg_catalog.jsonb_build_object(
            'createdAt', v_items -> -1 -> 'createdAt',
            'id', v_items -> -1 -> 'id'
        ) else null end
    );
end;
$$;

alter function public.get_my_inventory_dashboard(pg_catalog.int4, pg_catalog.uuid) owner to postgres;
alter function public.record_inventory_reception(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid) owner to postgres;
alter function public.reconcile_inventory_count(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid, pg_catalog.uuid) owner to postgres;
alter function public.get_my_inventory_history(pg_catalog.uuid, pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) owner to postgres;

revoke all on function public.get_my_inventory_dashboard(pg_catalog.int4, pg_catalog.uuid) from public, anon, authenticated;
revoke all on function public.record_inventory_reception(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid) from public, anon, authenticated;
revoke all on function public.reconcile_inventory_count(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid, pg_catalog.uuid) from public, anon, authenticated;
revoke all on function public.get_my_inventory_history(pg_catalog.uuid, pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) from public, anon, authenticated;

grant execute on function public.get_my_inventory_dashboard(pg_catalog.int4, pg_catalog.uuid) to authenticated;
grant execute on function public.record_inventory_reception(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid) to authenticated;
grant execute on function public.reconcile_inventory_count(pg_catalog.uuid, pg_catalog.numeric, pg_catalog.text, pg_catalog.uuid, pg_catalog.uuid) to authenticated;
grant execute on function public.get_my_inventory_history(pg_catalog.uuid, pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid) to authenticated;

commit;
