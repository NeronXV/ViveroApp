begin;

-- Harden the MVP extensions introduced in migrations 202608290001-202608290008.

alter policy inventory_locations_read on public.inventory_locations
using (
    exists (
        select 1
        from public.profiles p
        where p.id = auth.uid()
          and p.is_active
          and (
              (
                  public.inventory_locations.branch_id = p.branch_id
                  and (
                      public.has_permission('MANAGE_INVENTORY')
                      or public.has_permission('VIEW_INVENTORY_ALERTS')
                      or public.has_permission('VIEW_REPORTS')
                  )
              )
              or public.has_permission('VIEW_ALL_SALES')
              or public.has_permission('MANAGE_BRANCHES')
          )
    )
);

alter policy inventory_movements_read on public.inventory_movements
using (
    exists (
        select 1
        from public.profiles p
        where p.id = auth.uid()
          and p.is_active
          and (
              (
                  public.inventory_movements.branch_id = p.branch_id
                  and (
                      public.has_permission('MANAGE_INVENTORY')
                      or public.has_permission('VIEW_INVENTORY_ALERTS')
                      or public.has_permission('VIEW_REPORTS')
                  )
              )
              or public.has_permission('VIEW_ALL_SALES')
              or public.has_permission('MANAGE_BRANCHES')
          )
    )
);

alter policy inventory_balances_read on public.inventory_balances
using (
    exists (
        select 1
        from public.profiles p
        where p.id = auth.uid()
          and p.is_active
          and (
              (
                  public.inventory_balances.branch_id = p.branch_id
                  and (
                      public.has_permission('MANAGE_INVENTORY')
                      or public.has_permission('VIEW_INVENTORY_ALERTS')
                      or public.has_permission('VIEW_REPORTS')
                  )
              )
              or public.has_permission('VIEW_ALL_SALES')
              or public.has_permission('MANAGE_BRANCHES')
          )
    )
);

create or replace function public.update_inventory_balance()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_total pg_catalog.numeric;
begin
    insert into public.inventory_balances (branch_id, product_id, total_quantity, updated_at)
    values (new.branch_id, new.product_id, new.quantity, new.created_at)
    on conflict (branch_id, product_id) do update
    set total_quantity = public.inventory_balances.total_quantity + excluded.total_quantity,
        updated_at = excluded.updated_at
    returning total_quantity into v_total;

    if v_total < 0 then
        raise exception using errcode = '23514', message = 'Inventory cannot become negative';
    end if;

    return new;
end;
$$;

create or replace function public.record_inventory_movement(
    p_product_id pg_catalog.uuid,
    p_movement_type public.inventory_movement_type,
    p_quantity pg_catalog.numeric,
    p_branch_id pg_catalog.uuid default null,
    p_location_id pg_catalog.uuid default null,
    p_notes pg_catalog.text default null,
    p_reference_id pg_catalog.uuid default null
)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_quantity pg_catalog.numeric := p_quantity;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'Inventory management is not allowed';
    end if;

    if p_product_id is null or p_movement_type is null or p_quantity is null or p_quantity = 0 then
        raise exception using errcode = '22023', message = 'Inventory movement is invalid';
    end if;

    if p_branch_id is not null and (
        public.has_permission('VIEW_ALL_SALES') or public.has_permission('MANAGE_BRANCHES')
    ) then
        v_branch_id := p_branch_id;
    else
        select p.branch_id into v_branch_id
        from public.profiles p
        where p.id = v_actor_id and p.is_active;
    end if;

    if v_branch_id is null or not exists (
        select 1 from public.branches b where b.id = v_branch_id and b.is_active
    ) then
        raise exception using errcode = '22023', message = 'Active branch is required';
    end if;

    if p_location_id is not null and not exists (
        select 1
        from public.inventory_locations l
        where l.id = p_location_id
          and l.branch_id = v_branch_id
          and l.is_active
    ) then
        raise exception using errcode = '22023', message = 'Inventory location is invalid';
    end if;

    if p_movement_type in ('RECEPTION', 'ADJUSTMENT_ADD', 'TRANSFER_IN') and v_quantity < 0 then
        v_quantity := -v_quantity;
    elsif p_movement_type in ('SALE', 'ADJUSTMENT_SUB', 'TRANSFER_OUT') and v_quantity > 0 then
        v_quantity := -v_quantity;
    end if;

    insert into public.inventory_movements (
        branch_id, location_id, product_id, movement_type, quantity,
        reference_id, notes, created_by
    ) values (
        v_branch_id, p_location_id, p_product_id, p_movement_type, v_quantity,
        p_reference_id, nullif(pg_catalog.btrim(p_notes), ''), v_actor_id
    );
end;
$$;

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
    v_effective_branch_id pg_catalog.uuid;
    v_search pg_catalog.text := nullif(pg_catalog.lower(pg_catalog.btrim(p_search)), '');
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
begin
    if v_actor_id is null
       or not exists (select 1 from public.profiles p where p.id = v_actor_id and p.is_active)
       or not (
           public.has_permission('MANAGE_INVENTORY')
           or public.has_permission('VIEW_INVENTORY_ALERTS')
           or public.has_permission('VIEW_REPORTS')
       ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 100
       or pg_catalog.char_length(coalesce(v_search, '')) > 80 then
        raise exception using errcode = 'P0001', message = 'INVENTORY_QUERY_INVALID';
    end if;

    select p.branch_id into v_actor_branch_id
    from public.profiles p
    where p.id = v_actor_id;

    v_effective_branch_id := coalesce(p_branch_id, v_actor_branch_id);

    if v_effective_branch_id is null
       or (
           v_effective_branch_id is distinct from v_actor_branch_id
           and not public.has_permission('VIEW_ALL_SALES')
           and not public.has_permission('MANAGE_BRANCHES')
       ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_BRANCH_FORBIDDEN';
    end if;

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
        where b.branch_id = v_effective_branch_id
          and (coalesce(p_include_zero_stock, false) or b.total_quantity > 0)
          and (
              v_search is null
              or pg_catalog.lower(p.common_name) like '%' || v_search || '%'
              or pg_catalog.lower(p.internal_code) like '%' || v_search || '%'
          )
          and (p_after_product_id is null or b.product_id > p_after_product_id)
        order by b.product_id asc
        limit p_limit + 1
    ),
    page_rows as (
        select * from candidate_rows order by product_id asc limit p_limit
    )
    select
        coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'branchId', r.branch_id,
                'productId', r.product_id,
                'productName', r.product_name,
                'productCode', r.product_code,
                'productUnit', r.product_unit,
                'totalQuantity', r.total_quantity,
                'minimumStock', r.minimum_stock,
                'isLowStock', r.total_quantity <= r.minimum_stock,
                'updatedAt', r.updated_at
            ) order by r.product_id
        ), '[]'::pg_catalog.jsonb),
        (select pg_catalog.count(*) > p_limit from candidate_rows)
    into v_items, v_has_more
    from page_rows r;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branchId', v_effective_branch_id,
        'items', v_items,
        'hasMore', v_has_more
    );
end;
$$;

create or replace function public.get_low_inventory_alerts(
    p_branch_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
    v_effective_branch_id pg_catalog.uuid;
begin
    if v_actor_id is null
       or not exists (select 1 from public.profiles p where p.id = v_actor_id and p.is_active)
       or not (
           public.has_permission('VIEW_INVENTORY_ALERTS')
           or public.has_permission('MANAGE_INVENTORY')
           or public.has_permission('VIEW_REPORTS')
       ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_UNAUTHORIZED';
    end if;

    select p.branch_id into v_actor_branch_id
    from public.profiles p
    where p.id = v_actor_id;

    v_effective_branch_id := coalesce(p_branch_id, v_actor_branch_id);

    if v_effective_branch_id is null
       or (
           v_effective_branch_id is distinct from v_actor_branch_id
           and not public.has_permission('VIEW_ALL_SALES')
           and not public.has_permission('MANAGE_BRANCHES')
       ) then
        raise exception using errcode = 'P0001', message = 'INVENTORY_BRANCH_FORBIDDEN';
    end if;

    return (
        select coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'branchId', b.branch_id,
                'productId', b.product_id,
                'productName', p.common_name,
                'currentStock', b.total_quantity,
                'minimumStock', p.minimum_stock
            ) order by p.common_name, b.product_id
        ), '[]'::pg_catalog.jsonb)
        from public.inventory_balances b
        join public.products p on p.id = b.product_id
        where b.branch_id = v_effective_branch_id
          and b.total_quantity <= p.minimum_stock
          and p.is_active
    );
end;
$$;

alter policy customers_read on public.customers
using (
    public.has_permission('CREATE_SALES')
    or public.has_permission('MANAGE_USERS')
    or public.has_permission('VIEW_REPORTS')
);

create or replace function public.upsert_customer(
    p_id pg_catalog.uuid default null,
    p_full_name pg_catalog.text default null,
    p_email pg_catalog.text default null,
    p_phone pg_catalog.text default null,
    p_is_active pg_catalog.bool default true
)
returns public.customers
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_full_name pg_catalog.text := pg_catalog.btrim(p_full_name);
    v_email pg_catalog.text := pg_catalog.lower(pg_catalog.btrim(p_email));
    v_customer public.customers%rowtype;
begin
    if v_actor_id is null
       or not (public.has_permission('CREATE_SALES') or public.has_permission('MANAGE_USERS')) then
        raise exception using errcode = '42501', message = 'Customer management is not allowed';
    end if;

    if p_id is not null and not public.has_permission('MANAGE_USERS') then
        raise exception using errcode = '42501', message = 'Only user managers can update customers';
    end if;

    if v_full_name is null or pg_catalog.char_length(v_full_name) not between 2 and 160 then
        raise exception using errcode = '22023', message = 'Customer name is invalid';
    end if;

    insert into public.customers (id, full_name, email, phone, is_active)
    values (
        coalesce(p_id, pg_catalog.gen_random_uuid()),
        v_full_name,
        nullif(v_email, ''),
        nullif(pg_catalog.btrim(p_phone), ''),
        coalesce(p_is_active, true)
    )
    on conflict (id) do update
    set full_name = excluded.full_name,
        email = excluded.email,
        phone = excluded.phone,
        is_active = excluded.is_active,
        updated_at = pg_catalog.now()
    returning * into v_customer;

    return v_customer;
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'Email is already registered';
end;
$$;

create or replace function public.search_customers(
    p_query pg_catalog.text,
    p_limit pg_catalog.int4 default 10
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_query pg_catalog.text := pg_catalog.lower(pg_catalog.btrim(p_query));
begin
    if auth.uid() is null
       or not (public.has_permission('CREATE_SALES') or public.has_permission('MANAGE_USERS')) then
        raise exception using errcode = '42501', message = 'Customer search is not allowed';
    end if;

    if v_query is null
       or pg_catalog.char_length(v_query) < 2
       or pg_catalog.char_length(v_query) > 80
       or p_limit is null
       or p_limit < 1
       or p_limit > 50 then
        raise exception using errcode = '22023', message = 'Customer query is invalid';
    end if;

    return (
        select coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', c.id,
                'fullName', c.full_name,
                'email', c.email,
                'phone', c.phone
            ) order by pg_catalog.lower(c.full_name), c.id
        ), '[]'::pg_catalog.jsonb)
        from (
            select c.*
            from public.customers c
            where c.is_active
              and (
                  pg_catalog.lower(c.full_name) like '%' || v_query || '%'
                  or pg_catalog.lower(coalesce(c.email, '')) like '%' || v_query || '%'
                  or coalesce(c.phone, '') like '%' || v_query || '%'
              )
            order by pg_catalog.lower(c.full_name), c.id
            limit p_limit
        ) c
    );
end;
$$;

create or replace function public.apply_sale_discount(
    p_sale_id pg_catalog.uuid,
    p_discount_cents pg_catalog.int8,
    p_reason pg_catalog.text,
    p_promotion_id pg_catalog.uuid default null
)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_sale public.sales%rowtype;
    v_promotion public.promotions%rowtype;
    v_reason pg_catalog.text := pg_catalog.btrim(p_reason);
    v_expected_discount pg_catalog.int8;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_DISCOUNTS') then
        raise exception using errcode = '42501', message = 'Discount management is not allowed';
    end if;

    if p_sale_id is null or p_discount_cents is null then
        raise exception using errcode = '22023', message = 'Discount request is invalid';
    end if;

    select * into v_sale from public.sales where id = p_sale_id for update;

    if not found or v_sale.status <> 'SENT_TO_CASHIER' then
        raise exception using errcode = 'P0001', message = 'Sale unavailable for discount';
    end if;

    if p_promotion_id is not null then
        select * into v_promotion
        from public.promotions p
        where p.id = p_promotion_id
          and p.is_active
          and (p.starts_at is null or p.starts_at <= pg_catalog.now())
          and (p.ends_at is null or p.ends_at > pg_catalog.now())
        for share;

        if not found or v_sale.subtotal_cents < v_promotion.min_purchase_cents then
            raise exception using errcode = '22023', message = 'Promotion is not applicable';
        end if;

        if v_promotion.promo_type = 'PERCENTAGE' then
            if v_promotion.value > 100 then
                raise exception using errcode = '22023', message = 'Promotion percentage is invalid';
            end if;
            v_expected_discount := pg_catalog.round(
                v_sale.subtotal_cents * v_promotion.value / 100
            )::pg_catalog.int8;
        else
            -- FIXED_AMOUNT values are stored as integer cents in the generic value column.
            v_expected_discount := pg_catalog.round(v_promotion.value)::pg_catalog.int8;
        end if;

        if v_promotion.max_discount_cents is not null then
            v_expected_discount := least(
                v_expected_discount,
                v_promotion.max_discount_cents
            );
        end if;

        v_expected_discount := least(v_expected_discount, v_sale.subtotal_cents);

        if p_discount_cents <> v_expected_discount then
            raise exception using errcode = '22023', message = 'Discount does not match promotion rules';
        end if;
    end if;

    if p_discount_cents < 0 or p_discount_cents > v_sale.subtotal_cents then
        raise exception using errcode = '22023', message = 'Invalid discount amount';
    end if;

    if v_reason is null or pg_catalog.char_length(v_reason) not between 3 and 240 then
        raise exception using errcode = '22023', message = 'Discount reason is invalid';
    end if;

    insert into public.sale_discounts (sale_id, promotion_id, discount_cents, reason, applied_by)
    values (p_sale_id, p_promotion_id, p_discount_cents, v_reason, v_actor_id)
    on conflict (sale_id) do update
    set promotion_id = excluded.promotion_id,
        discount_cents = excluded.discount_cents,
        reason = excluded.reason,
        applied_by = excluded.applied_by,
        created_at = excluded.created_at;

    update public.sales
    set discount_cents = p_discount_cents,
        total_cents = subtotal_cents - p_discount_cents,
        updated_at = pg_catalog.now()
    where id = p_sale_id;
end;
$$;

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
    v_start pg_catalog.timestamptz := coalesce(
        p_start_date::pg_catalog.timestamptz,
        pg_catalog.now() - pg_catalog.make_interval(days => 30)
    );
    v_end pg_catalog.timestamptz := coalesce(
        (p_end_date + 1)::pg_catalog.timestamptz,
        pg_catalog.now()
    );
begin
    if v_actor_id is null or not public.has_permission('VIEW_REPORTS') then
        raise exception using errcode = '42501', message = 'Unauthorized to view reports';
    end if;

    if v_start >= v_end then
        raise exception using errcode = '22023', message = 'Report date range is invalid';
    end if;

    select p.branch_id into v_actor_branch_id
    from public.profiles p
    where p.id = v_actor_id;

    return (
        with daily_stats as (
            select
                s.branch_id,
                b.name as branch_name,
                pg_catalog.date_trunc('day', pay.created_at) as sale_day,
                pg_catalog.count(*) as total_sales,
                pg_catalog.sum(pay.amount_due_cents) as revenue_cents,
                pg_catalog.sum(s.discount_cents) as total_discounts_cents
            from public.sale_payments pay
            join public.sales s on s.id = pay.sale_id
            join public.branches b on b.id = s.branch_id
            where (p_branch_id is null or s.branch_id = p_branch_id)
              and (public.has_permission('VIEW_ALL_SALES') or s.branch_id = v_actor_branch_id)
              and pay.created_at >= v_start
              and pay.created_at < v_end
            group by s.branch_id, b.name, pg_catalog.date_trunc('day', pay.created_at)
        )
        select coalesce(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'branchId', d.branch_id,
                'branchName', d.branch_name,
                'day', d.sale_day,
                'salesCount', d.total_sales,
                'revenueCents', d.revenue_cents,
                'discountCents', d.total_discounts_cents
            ) order by d.sale_day desc, d.branch_name asc
        ), '[]'::pg_catalog.jsonb)
        from daily_stats d
    );
end;
$$;

alter function public.update_inventory_balance() owner to postgres;
alter function public.record_inventory_movement(
    pg_catalog.uuid, public.inventory_movement_type, pg_catalog.numeric,
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid
) owner to postgres;
alter function public.get_admin_inventory_balances(
    pg_catalog.uuid, pg_catalog.int4, pg_catalog.uuid, pg_catalog.text, pg_catalog.bool
) owner to postgres;
alter function public.get_low_inventory_alerts(pg_catalog.uuid) owner to postgres;
alter function public.upsert_customer(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.text, pg_catalog.bool
) owner to postgres;
alter function public.search_customers(pg_catalog.text, pg_catalog.int4) owner to postgres;
alter function public.apply_sale_discount(
    pg_catalog.uuid, pg_catalog.int8, pg_catalog.text, pg_catalog.uuid
) owner to postgres;
alter function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
) owner to postgres;

revoke all on function public.update_inventory_balance() from public, anon, authenticated;
revoke all on function public.record_inventory_movement(
    pg_catalog.uuid, public.inventory_movement_type, pg_catalog.numeric,
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid
) from public, anon, authenticated;
revoke all on function public.get_admin_inventory_balances(
    pg_catalog.uuid, pg_catalog.int4, pg_catalog.uuid, pg_catalog.text, pg_catalog.bool
) from public, anon, authenticated;
revoke all on function public.get_low_inventory_alerts(pg_catalog.uuid) from public, anon, authenticated;
revoke all on function public.upsert_customer(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.text, pg_catalog.bool
) from public, anon, authenticated;
revoke all on function public.search_customers(pg_catalog.text, pg_catalog.int4) from public, anon, authenticated;
revoke all on function public.apply_sale_discount(
    pg_catalog.uuid, pg_catalog.int8, pg_catalog.text, pg_catalog.uuid
) from public, anon, authenticated;
revoke all on function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
) from public, anon, authenticated;

grant execute on function public.record_inventory_movement(
    pg_catalog.uuid, public.inventory_movement_type, pg_catalog.numeric,
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid
) to authenticated;
grant execute on function public.get_admin_inventory_balances(
    pg_catalog.uuid, pg_catalog.int4, pg_catalog.uuid, pg_catalog.text, pg_catalog.bool
) to authenticated;
grant execute on function public.get_low_inventory_alerts(pg_catalog.uuid) to authenticated;
grant execute on function public.upsert_customer(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.text, pg_catalog.bool
) to authenticated;
grant execute on function public.search_customers(pg_catalog.text, pg_catalog.int4) to authenticated;
grant execute on function public.apply_sale_discount(
    pg_catalog.uuid, pg_catalog.int8, pg_catalog.text, pg_catalog.uuid
) to authenticated;
grant execute on function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
) to authenticated;

comment on function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
)
is 'Daily paid-sales report grouped by the canonical payment timestamp.';

commit;
