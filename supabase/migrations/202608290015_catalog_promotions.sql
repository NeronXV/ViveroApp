begin;

alter table public.promotions
add column scope text not null default 'SALE'
check (scope in ('SALE', 'ALL_PRODUCTS', 'SELECTED_PRODUCTS'));

alter table public.promotions
add constraint promotions_percentage_value_check
check (promo_type <> 'PERCENTAGE' or value <= 100) not valid;

alter table public.promotions
add constraint promotions_fixed_amount_integer_cents_check
check (promo_type <> 'FIXED_AMOUNT' or value = pg_catalog.trunc(value)) not valid;

create table public.promotion_products (
    promotion_id uuid not null references public.promotions(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete cascade,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (promotion_id, product_id)
);

create index promotion_products_product_idx
on public.promotion_products(product_id, promotion_id);

create index promotions_catalog_candidates_idx
on public.promotions(scope, starts_at, ends_at, created_at, id)
where is_active and scope in ('ALL_PRODUCTS', 'SELECTED_PRODUCTS');

alter table public.promotion_products enable row level security;

create policy promotion_products_read on public.promotion_products
for select to authenticated
using (
    public.has_permission('VIEW_CATALOG')
    or public.has_permission('MANAGE_DISCOUNTS')
);

revoke all on table public.promotion_products from public, anon, authenticated;
grant select on table public.promotion_products to authenticated;
grant all on table public.promotion_products to service_role;

alter table public.sale_items
add column promotion_id uuid references public.promotions(id) on delete set null,
add column promotion_name text;

create or replace function public.resolve_catalog_product_price(
    p_product_id pg_catalog.uuid,
    p_at pg_catalog.timestamptz default pg_catalog.now()
)
returns table (
    list_price_cents pg_catalog.int8,
    effective_price_cents pg_catalog.int8,
    promotion_id pg_catalog.uuid,
    promotion_name pg_catalog.text,
    discount_percent pg_catalog.numeric
)
language sql
stable
security definer
set search_path = ''
as $$
    with product_row as (
        select p.id, p.price_cents
        from public.products p
        where p.id = p_product_id and p.is_active
    ), eligible as (
        select
            promo.id,
            promo.name,
            promo.created_at,
            pr.price_cents,
            least(
                pr.price_cents,
                case promo.promo_type
                    when 'PERCENTAGE' then
                        pg_catalog.round(pr.price_cents * promo.value / 100)::pg_catalog.int8
                    else pg_catalog.round(promo.value)::pg_catalog.int8
                end,
                coalesce(promo.max_discount_cents, pr.price_cents)
            ) as discount_cents
        from product_row pr
        join public.promotions promo
          on promo.is_active
         and promo.scope in ('ALL_PRODUCTS', 'SELECTED_PRODUCTS')
         and (promo.starts_at is null or promo.starts_at <= p_at)
         and (promo.ends_at is null or promo.ends_at > p_at)
         and promo.min_purchase_cents <= pr.price_cents
         and (promo.promo_type <> 'PERCENTAGE' or promo.value <= 100)
         and (
             promo.scope = 'ALL_PRODUCTS'
             or exists (
                 select 1
                 from public.promotion_products pp
                 where pp.promotion_id = promo.id
                   and pp.product_id = pr.id
                   and pp.is_active
             )
         )
    ), selected as (
        select e.*
        from eligible e
        where e.discount_cents > 0
        order by
            (e.price_cents - e.discount_cents),
            e.created_at,
            e.id
        limit 1
    )
    select
        pr.price_cents,
        pr.price_cents - coalesce(s.discount_cents, 0),
        s.id,
        s.name,
        case
            when s.id is null or pr.price_cents = 0 then null
            else pg_catalog.round(
                s.discount_cents::pg_catalog.numeric * 100 / pr.price_cents,
                2
            )
        end
    from product_row pr
    left join selected s on true;
$$;

revoke all on function public.resolve_catalog_product_price(
    pg_catalog.uuid, pg_catalog.timestamptz
) from public, anon, authenticated, service_role;

alter function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) rename to get_public_catalog_v2_base;

revoke all on function public.get_public_catalog_v2_base(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) from public, anon, authenticated, service_role;

create or replace function public.get_public_catalog(
    p_search pg_catalog.text default null,
    p_category_id pg_catalog.uuid default null,
    p_limit pg_catalog.int4 default 24,
    p_after_name pg_catalog.text default null,
    p_after_id pg_catalog.uuid default null
)
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_base pg_catalog.jsonb;
    v_items pg_catalog.jsonb;
begin
    v_base := public.get_public_catalog_v2_base(
        p_search,
        p_category_id,
        p_limit,
        p_after_name,
        p_after_id
    );

    select coalesce(
        pg_catalog.jsonb_agg(
            (entry.item - 'price' - 'activePromotion')
            || pg_catalog.jsonb_build_object(
                'price', pg_catalog.jsonb_build_object(
                    'amountCents', pricing.effective_price_cents,
                    'originalAmountCents', case
                        when pricing.promotion_id is null then null
                        else pricing.list_price_cents
                    end,
                    'discountPercent', pricing.discount_percent,
                    'currency', entry.item#>>'{price,currency}',
                    'unit', entry.item#>>'{price,unit}'
                ),
                'activePromotion', case
                    when pricing.promotion_id is null then null
                    else pg_catalog.jsonb_build_object(
                        'id', pricing.promotion_id,
                        'name', pricing.promotion_name
                    )
                end
            )
            order by entry.ordinality
        ),
        '[]'::pg_catalog.jsonb
    )
    into v_items
    from pg_catalog.jsonb_array_elements(v_base->'items')
        with ordinality as entry(item, ordinality)
    cross join lateral public.resolve_catalog_product_price(
        (entry.item->>'id')::pg_catalog.uuid
    ) pricing;

    return pg_catalog.jsonb_set(
        pg_catalog.jsonb_set(v_base, '{schemaVersion}', '3'::pg_catalog.jsonb),
        '{items}',
        v_items
    );
end;
$$;

revoke all on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) from public, anon, authenticated, service_role;

grant execute on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) to anon, authenticated;

comment on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) is 'Devuelve el catálogo público V3 con precios de lista y promocionales calculados de forma autoritativa por PostgreSQL.';

create or replace function public.get_catalog_pricing()
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
begin
    if v_actor_id is null or not public.has_permission('VIEW_CATALOG') then
        raise exception using errcode = '42501', message = 'Catalog pricing is not allowed';
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'productId', p.id,
                        'listPriceCents', pricing.list_price_cents,
                        'effectivePriceCents', pricing.effective_price_cents,
                        'activePromotion', case
                            when pricing.promotion_id is null then null
                            else pg_catalog.jsonb_build_object(
                                'id', pricing.promotion_id,
                                'name', pricing.promotion_name,
                                'discountPercent', pricing.discount_percent
                            )
                        end
                    )
                    order by p.id
                )
                from public.products p
                cross join lateral public.resolve_catalog_product_price(p.id) pricing
                where p.is_active
            ),
            '[]'::pg_catalog.jsonb
        )
    );
end;
$$;

revoke all on function public.get_catalog_pricing()
from public, anon, authenticated, service_role;
grant execute on function public.get_catalog_pricing() to authenticated;

create or replace function public.upsert_catalog_promotion(
    p_name pg_catalog.text,
    p_description pg_catalog.text,
    p_scope pg_catalog.text,
    p_promo_type pg_catalog.text,
    p_value pg_catalog.numeric,
    p_min_purchase_cents pg_catalog.int8,
    p_max_discount_cents pg_catalog.int8,
    p_starts_at pg_catalog.timestamptz,
    p_ends_at pg_catalog.timestamptz,
    p_is_active pg_catalog.bool,
    p_product_ids pg_catalog.uuid[],
    p_id pg_catalog.uuid default null
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_id pg_catalog.uuid := coalesce(p_id, pg_catalog.gen_random_uuid());
    v_name pg_catalog.text := pg_catalog.btrim(p_name);
    v_description pg_catalog.text := nullif(pg_catalog.btrim(p_description), '');
    v_product_count pg_catalog.int8;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_DISCOUNTS') then
        raise exception using errcode = '42501', message = 'Promotion management is not allowed';
    end if;

    if v_name is null
       or pg_catalog.char_length(v_name) not between 2 and 100
       or pg_catalog.char_length(coalesce(v_description, '')) > 500
       or p_scope is null
       or p_scope not in ('ALL_PRODUCTS', 'SELECTED_PRODUCTS')
       or p_promo_type is null
       or p_promo_type not in ('PERCENTAGE', 'FIXED_AMOUNT')
       or p_value is null
       or p_value <= 0
       or (p_promo_type = 'PERCENTAGE' and p_value > 100)
       or (p_promo_type = 'FIXED_AMOUNT' and p_value <> pg_catalog.trunc(p_value))
       or p_min_purchase_cents is null
       or p_min_purchase_cents < 0
       or (p_max_discount_cents is not null and p_max_discount_cents <= 0)
       or (p_starts_at is not null and p_ends_at is not null and p_starts_at >= p_ends_at)
       or p_is_active is null
       or p_product_ids is null
       or pg_catalog.cardinality(p_product_ids) > 1000 then
        raise exception using errcode = '22023', message = 'Promotion data is invalid';
    end if;

    if p_scope = 'ALL_PRODUCTS' and pg_catalog.cardinality(p_product_ids) <> 0 then
        raise exception using errcode = '22023', message = 'All-products promotion cannot list products';
    end if;

    if p_scope = 'SELECTED_PRODUCTS' and pg_catalog.cardinality(p_product_ids) = 0 then
        raise exception using errcode = '22023', message = 'Selected-products promotion requires products';
    end if;

    select pg_catalog.count(distinct requested.product_id)
    into v_product_count
    from pg_catalog.unnest(p_product_ids) requested(product_id)
    join public.products p on p.id = requested.product_id;

    if v_product_count <> pg_catalog.cardinality(p_product_ids) then
        raise exception using errcode = '22023', message = 'Promotion products are invalid';
    end if;

    if exists (
        select 1 from public.promotions p
        where p.id = v_id and p.scope = 'SALE'
    ) then
        raise exception using errcode = '22023', message = 'Sale promotion cannot become a catalog promotion';
    end if;

    insert into public.promotions (
        id, name, description, is_active, starts_at, ends_at, promo_type,
        value, min_purchase_cents, max_discount_cents, scope
    ) values (
        v_id, v_name, v_description, p_is_active, p_starts_at, p_ends_at,
        p_promo_type, p_value, p_min_purchase_cents, p_max_discount_cents, p_scope
    )
    on conflict (id) do update
    set name = excluded.name,
        description = excluded.description,
        is_active = excluded.is_active,
        starts_at = excluded.starts_at,
        ends_at = excluded.ends_at,
        promo_type = excluded.promo_type,
        value = excluded.value,
        min_purchase_cents = excluded.min_purchase_cents,
        max_discount_cents = excluded.max_discount_cents,
        scope = excluded.scope,
        updated_at = pg_catalog.now();

    update public.promotion_products pp
    set is_active = false
    where pp.promotion_id = v_id and pp.is_active;

    if p_scope = 'SELECTED_PRODUCTS' then
        insert into public.promotion_products(promotion_id, product_id, is_active)
        select v_id, requested.product_id, true
        from pg_catalog.unnest(p_product_ids) requested(product_id)
        on conflict (promotion_id, product_id) do update
        set is_active = true;
    end if;

    return pg_catalog.jsonb_build_object(
        'id', v_id,
        'scope', p_scope,
        'productIds', pg_catalog.to_jsonb(p_product_ids)
    );
end;
$$;

revoke all on function public.upsert_catalog_promotion(
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.numeric,
    pg_catalog.int8,
    pg_catalog.int8,
    pg_catalog.timestamptz,
    pg_catalog.timestamptz,
    pg_catalog.bool,
    pg_catalog.uuid[],
    pg_catalog.uuid
) from public, anon, authenticated, service_role;

grant execute on function public.upsert_catalog_promotion(
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.text,
    pg_catalog.numeric,
    pg_catalog.int8,
    pg_catalog.int8,
    pg_catalog.timestamptz,
    pg_catalog.timestamptz,
    pg_catalog.bool,
    pg_catalog.uuid[],
    pg_catalog.uuid
) to authenticated;

create or replace function public.submit_sale_to_cashier(
    p_sale_id pg_catalog.uuid,
    p_folio pg_catalog.text,
    p_items pg_catalog.jsonb,
    p_customer_id pg_catalog.uuid default null
) returns public.sales
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_sale public.sales%rowtype;
    v_item_count pg_catalog.int8;
    v_distinct_item_count pg_catalog.int8;
    v_valid_product_count pg_catalog.int8;
    v_invalid_quantity_count pg_catalog.int8;
    v_subtotal pg_catalog.int8;
    v_priced_items pg_catalog.jsonb;
begin
    if v_user_id is null or not public.has_permission('CREATE_SALES') then
        raise exception using errcode = '42501', message = 'Sale submission is not allowed';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_user_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = '42501', message = 'Sale submission is not allowed';
    end if;

    select s.* into v_sale
    from public.sales s
    where s.idempotency_key = p_sale_id or s.id = p_sale_id
    limit 1;

    if found then
        if v_sale.created_by <> v_user_id then
            raise exception using errcode = '42501', message = 'Idempotency key is unavailable';
        end if;
        return v_sale;
    end if;

    if p_items is null
       or pg_catalog.jsonb_typeof(p_items) <> 'array'
       or pg_catalog.jsonb_array_length(p_items) = 0 then
        raise exception using errcode = '22023', message = 'Sale requires at least one item';
    end if;

    if exists (
        select 1
        from pg_catalog.jsonb_array_elements(p_items) as element(value)
        where pg_catalog.jsonb_typeof(element.value) <> 'object'
    ) then
        raise exception using errcode = '22023', message = 'Sale items are invalid';
    end if;

    select
        pg_catalog.count(*),
        pg_catalog.count(distinct i.product_id),
        pg_catalog.count(*) filter (
            where i.product_id is null
               or i.quantity is null
               or i.quantity <= 0
               or i.quantity > 100000
        )
    into v_item_count, v_distinct_item_count, v_invalid_quantity_count
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4);

    if v_item_count <> pg_catalog.jsonb_array_length(p_items)
       or v_distinct_item_count <> v_item_count
       or v_invalid_quantity_count > 0 then
        raise exception using errcode = '22023', message = 'Sale items are invalid';
    end if;

    perform 1
    from public.products p
    join pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4)
      on i.product_id = p.id
    where p.is_active
    for key share of p;

    select
        pg_catalog.count(*),
        coalesce(pg_catalog.sum(pricing.effective_price_cents * i.quantity), 0),
        coalesce(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'product_id', p.id,
                    'quantity', i.quantity,
                    'list_price_cents', pricing.list_price_cents,
                    'effective_price_cents', pricing.effective_price_cents,
                    'promotion_id', pricing.promotion_id,
                    'promotion_name', pricing.promotion_name
                )
                order by p.id
            ),
            '[]'::pg_catalog.jsonb
        )
    into v_valid_product_count, v_subtotal, v_priced_items
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4)
    join public.products p on p.id = i.product_id and p.is_active
    cross join lateral public.resolve_catalog_product_price(p.id) pricing;

    if v_valid_product_count <> v_item_count or v_subtotal <= 0 then
        raise exception using errcode = '22023', message = 'Sale items are invalid';
    end if;

    insert into public.sales (
        id, folio, branch_id, customer_id, subtotal_cents, discount_cents,
        total_cents, status, created_by, idempotency_key
    ) values (
        p_sale_id, p_folio, v_branch_id, p_customer_id, v_subtotal, 0,
        v_subtotal, 'SENT_TO_CASHIER', v_user_id, p_sale_id
    )
    on conflict (idempotency_key) do nothing
    returning * into v_sale;

    if v_sale.id is null then
        select s.* into strict v_sale
        from public.sales s
        where s.idempotency_key = p_sale_id;

        if v_sale.created_by <> v_user_id then
            raise exception using errcode = '42501', message = 'Idempotency key is unavailable';
        end if;
        return v_sale;
    end if;

    insert into public.sale_items (
        sale_id, product_id, product_name, internal_code, quantity,
        list_price_cents, unit_price_cents, promotion_id, promotion_name
    )
    select
        v_sale.id,
        p.id,
        p.common_name,
        p.internal_code,
        priced.quantity,
        priced.list_price_cents,
        priced.effective_price_cents,
        priced.promotion_id,
        priced.promotion_name
    from pg_catalog.jsonb_to_recordset(v_priced_items) as priced(
        product_id pg_catalog.uuid,
        quantity pg_catalog.int4,
        list_price_cents pg_catalog.int8,
        effective_price_cents pg_catalog.int8,
        promotion_id pg_catalog.uuid,
        promotion_name pg_catalog.text
    )
    join public.products p on p.id = priced.product_id;

    insert into public.sale_status_history(
        sale_id, previous_status, new_status, changed_by, observation
    ) values
        (v_sale.id, null, 'DRAFT', v_user_id, 'Draft accepted by backend'),
        (v_sale.id, 'DRAFT', 'SENT_TO_CASHIER', v_user_id, 'Order sent to cashier');

    return v_sale;
end;
$$;

revoke all on function public.submit_sale_to_cashier(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) from public, anon, authenticated, service_role;
grant execute on function public.submit_sale_to_cashier(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) to authenticated;

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
        with item_discounts as (
            select
                si.sale_id,
                pg_catalog.sum(si.discount_cents) as discount_cents
            from public.sale_items si
            group by si.sale_id
        ),
        daily_stats as (
            select
                s.branch_id,
                b.name as branch_name,
                pg_catalog.date_trunc('day', pay.created_at) as sale_day,
                pg_catalog.count(*) as total_sales,
                pg_catalog.sum(pay.amount_due_cents) as revenue_cents,
                pg_catalog.sum(
                    s.discount_cents + coalesce(items.discount_cents, 0)
                ) as total_discounts_cents
            from public.sale_payments pay
            join public.sales s on s.id = pay.sale_id
            join public.branches b on b.id = s.branch_id
            left join item_discounts items on items.sale_id = s.id
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

revoke all on function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
) from public, anon, authenticated, service_role;
grant execute on function public.get_report_daily_sales(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.date
) to authenticated;

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
          and p.scope = 'SALE'
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
            v_expected_discount := pg_catalog.round(v_promotion.value)::pg_catalog.int8;
        end if;

        if v_promotion.max_discount_cents is not null then
            v_expected_discount := least(v_expected_discount, v_promotion.max_discount_cents);
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

revoke all on function public.apply_sale_discount(
    pg_catalog.uuid, pg_catalog.int8, pg_catalog.text, pg_catalog.uuid
) from public, anon, authenticated, service_role;
grant execute on function public.apply_sale_discount(
    pg_catalog.uuid, pg_catalog.int8, pg_catalog.text, pg_catalog.uuid
) to authenticated;

commit;
