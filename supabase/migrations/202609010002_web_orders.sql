begin;

create type public.web_order_status as enum (
    'PENDING',
    'CONFIRMED',
    'READY',
    'COMPLETED',
    'CANCELLED'
);

create table public.web_orders (
    id pg_catalog.uuid primary key,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    customer_name pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(customer_name)) between 2 and 160),
    customer_phone pg_catalog.text,
    customer_email pg_catalog.text,
    notes pg_catalog.text,
    subtotal_cents pg_catalog.int8 not null check (subtotal_cents > 0),
    discount_cents pg_catalog.int8 not null default 0
        check (discount_cents >= 0 and discount_cents <= subtotal_cents),
    total_cents pg_catalog.int8 not null
        check (total_cents = subtotal_cents - discount_cents),
    status public.web_order_status not null default 'PENDING',
    idempotency_key pg_catalog.uuid not null unique,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    check (customer_phone is not null or customer_email is not null),
    check (customer_phone is null or pg_catalog.char_length(customer_phone) between 8 and 24),
    check (customer_email is null or pg_catalog.char_length(customer_email) between 5 and 254),
    check (notes is null or pg_catalog.char_length(notes) <= 500)
);

create table public.web_order_items (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    order_id pg_catalog.uuid not null references public.web_orders(id) on delete cascade,
    product_id pg_catalog.uuid not null references public.products(id) on delete restrict,
    product_name pg_catalog.text not null,
    internal_code pg_catalog.text not null,
    quantity pg_catalog.int4 not null check (quantity between 1 and 100),
    list_price_cents pg_catalog.int8 not null check (list_price_cents >= 0),
    unit_price_cents pg_catalog.int8 not null
        check (unit_price_cents >= 0 and unit_price_cents <= list_price_cents),
    promotion_id pg_catalog.uuid references public.promotions(id) on delete set null,
    promotion_name pg_catalog.text,
    discount_cents pg_catalog.int8 generated always as
        ((list_price_cents - unit_price_cents) * quantity) stored,
    line_total_cents pg_catalog.int8 generated always as
        (unit_price_cents * quantity) stored,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    unique (order_id, product_id)
);

create table public.web_order_status_history (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    order_id pg_catalog.uuid not null references public.web_orders(id) on delete cascade,
    previous_status public.web_order_status,
    new_status public.web_order_status not null,
    changed_by pg_catalog.uuid references public.profiles(id) on delete restrict,
    observation pg_catalog.text,
    changed_at pg_catalog.timestamptz not null default pg_catalog.now()
);

create index web_orders_branch_status_created_idx
on public.web_orders(branch_id, status, created_at desc, id desc);

create index web_orders_contact_created_idx
on public.web_orders(customer_phone, customer_email, created_at desc);

create index web_order_items_order_idx on public.web_order_items(order_id);
create index web_order_status_history_order_idx
on public.web_order_status_history(order_id, changed_at desc);

create trigger web_orders_set_updated_at before update on public.web_orders
for each row execute function public.set_updated_at();

alter table public.web_orders enable row level security;
alter table public.web_order_items enable row level security;
alter table public.web_order_status_history enable row level security;

revoke all on table public.web_orders, public.web_order_items, public.web_order_status_history
from public, anon, authenticated;

grant all on table public.web_orders, public.web_order_items, public.web_order_status_history
to service_role;

create or replace function public.get_public_web_order_options()
returns pg_catalog.jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branches', coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', b.id,
                        'code', b.code,
                        'name', b.name
                    ) order by b.name, b.id
                )
                from public.branches b
                where b.is_active
            ),
            '[]'::pg_catalog.jsonb
        )
    );
$$;

create or replace function public.submit_web_order(
    p_order_id pg_catalog.uuid,
    p_branch_id pg_catalog.uuid,
    p_customer_name pg_catalog.text,
    p_customer_phone pg_catalog.text,
    p_customer_email pg_catalog.text,
    p_notes pg_catalog.text,
    p_items pg_catalog.jsonb
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order public.web_orders%rowtype;
    v_customer_name pg_catalog.text := pg_catalog.btrim(p_customer_name);
    v_customer_phone pg_catalog.text := nullif(pg_catalog.btrim(p_customer_phone), '');
    v_customer_email pg_catalog.text := nullif(pg_catalog.lower(pg_catalog.btrim(p_customer_email)), '');
    v_notes pg_catalog.text := nullif(pg_catalog.btrim(p_notes), '');
    v_contact_key pg_catalog.text;
    v_item_count pg_catalog.int8;
    v_distinct_item_count pg_catalog.int8;
    v_invalid_item_count pg_catalog.int8;
    v_valid_product_count pg_catalog.int8;
    v_subtotal pg_catalog.int8;
    v_list_subtotal pg_catalog.int8;
    v_priced_items pg_catalog.jsonb;
begin
    if p_order_id is null or p_branch_id is null then
        raise exception using errcode = '22023', message = 'WEB_ORDER_INVALID';
    end if;

    select o.* into v_order
    from public.web_orders o
    where o.idempotency_key = p_order_id or o.id = p_order_id
    limit 1;

    if found then
        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'orderId', v_order.id,
            'orderNumber', 'VW-' || pg_catalog.upper(pg_catalog.left(pg_catalog.replace(v_order.id::pg_catalog.text, '-', ''), 10)),
            'status', v_order.status,
            'totalCents', v_order.total_cents,
            'createdAt', v_order.created_at,
            'idempotentReplay', true
        );
    end if;

    if v_customer_name is null
       or pg_catalog.char_length(v_customer_name) not between 2 and 160
       or (v_customer_phone is null and v_customer_email is null)
       or (v_customer_phone is not null and v_customer_phone !~ '^[0-9+() .-]{8,24}$')
       or (v_customer_email is not null and v_customer_email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
       or pg_catalog.char_length(coalesce(v_notes, '')) > 500 then
        raise exception using errcode = '22023', message = 'WEB_ORDER_CUSTOMER_INVALID';
    end if;

    if not exists (
        select 1 from public.branches b where b.id = p_branch_id and b.is_active
    ) then
        raise exception using errcode = '22023', message = 'WEB_ORDER_BRANCH_UNAVAILABLE';
    end if;

    if p_items is null
       or pg_catalog.jsonb_typeof(p_items) <> 'array'
       or pg_catalog.jsonb_array_length(p_items) not between 1 and 25
       or exists (
           select 1
           from pg_catalog.jsonb_array_elements(p_items) element(value)
           where pg_catalog.jsonb_typeof(element.value) <> 'object'
       ) then
        raise exception using errcode = '22023', message = 'WEB_ORDER_ITEMS_INVALID';
    end if;

    select
        pg_catalog.count(*),
        pg_catalog.count(distinct i.product_id),
        pg_catalog.count(*) filter (
            where i.product_id is null
               or i.quantity is null
               or i.quantity not between 1 and 100
        )
    into v_item_count, v_distinct_item_count, v_invalid_item_count
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4);

    if v_item_count <> pg_catalog.jsonb_array_length(p_items)
       or v_distinct_item_count <> v_item_count
       or v_invalid_item_count > 0 then
        raise exception using errcode = '22023', message = 'WEB_ORDER_ITEMS_INVALID';
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
        coalesce(pg_catalog.sum(pricing.list_price_cents * i.quantity), 0),
        coalesce(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'product_id', p.id,
                    'quantity', i.quantity,
                    'list_price_cents', pricing.list_price_cents,
                    'effective_price_cents', pricing.effective_price_cents,
                    'promotion_id', pricing.promotion_id,
                    'promotion_name', pricing.promotion_name
                ) order by p.id
            ),
            '[]'::pg_catalog.jsonb
        )
    into v_valid_product_count, v_subtotal, v_list_subtotal, v_priced_items
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4)
    join public.products p on p.id = i.product_id and p.is_active
    cross join lateral public.resolve_catalog_product_price(p.id) pricing;

    if v_valid_product_count <> v_item_count or v_subtotal <= 0 then
        raise exception using errcode = '22023', message = 'WEB_ORDER_ITEMS_UNAVAILABLE';
    end if;

    v_contact_key := coalesce(v_customer_phone, '') || '|' || coalesce(v_customer_email, '');
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_contact_key, 0));

    if (
        select pg_catalog.count(*)
        from public.web_orders o
        where o.created_at >= pg_catalog.now() - pg_catalog.make_interval(mins => 15)
          and o.status <> 'CANCELLED'
          and (
              (v_customer_phone is not null and o.customer_phone = v_customer_phone)
              or (v_customer_email is not null and o.customer_email = v_customer_email)
          )
    ) >= 3 then
        raise exception using errcode = 'P0001', message = 'WEB_ORDER_RATE_LIMITED';
    end if;

    insert into public.web_orders (
        id, branch_id, customer_name, customer_phone, customer_email, notes,
        subtotal_cents, discount_cents, total_cents, idempotency_key
    ) values (
        p_order_id, p_branch_id, v_customer_name, v_customer_phone, v_customer_email, v_notes,
        v_list_subtotal, v_list_subtotal - v_subtotal, v_subtotal, p_order_id
    )
    returning * into v_order;

    insert into public.web_order_items (
        order_id, product_id, product_name, internal_code, quantity,
        list_price_cents, unit_price_cents, promotion_id, promotion_name
    )
    select
        v_order.id, p.id, p.common_name, p.internal_code, priced.quantity,
        priced.list_price_cents, priced.effective_price_cents,
        priced.promotion_id, priced.promotion_name
    from pg_catalog.jsonb_to_recordset(v_priced_items) as priced(
        product_id pg_catalog.uuid,
        quantity pg_catalog.int4,
        list_price_cents pg_catalog.int8,
        effective_price_cents pg_catalog.int8,
        promotion_id pg_catalog.uuid,
        promotion_name pg_catalog.text
    )
    join public.products p on p.id = priced.product_id;

    insert into public.web_order_status_history (
        order_id, previous_status, new_status, observation
    ) values (
        v_order.id, null, 'PENDING', 'Pedido recibido desde la tienda web'
    );

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'orderId', v_order.id,
        'orderNumber', 'VW-' || pg_catalog.upper(pg_catalog.left(pg_catalog.replace(v_order.id::pg_catalog.text, '-', ''), 10)),
        'status', v_order.status,
        'totalCents', v_order.total_cents,
        'createdAt', v_order.created_at,
        'idempotentReplay', false
    );
exception
    when unique_violation then
        select o.* into strict v_order
        from public.web_orders o
        where o.idempotency_key = p_order_id;

        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'orderId', v_order.id,
            'orderNumber', 'VW-' || pg_catalog.upper(pg_catalog.left(pg_catalog.replace(v_order.id::pg_catalog.text, '-', ''), 10)),
            'status', v_order.status,
            'totalCents', v_order.total_cents,
            'createdAt', v_order.created_at,
            'idempotentReplay', true
        );
end;
$$;

create or replace function public.get_admin_web_orders(
    p_limit pg_catalog.int4 default 50,
    p_after_created_at pg_catalog.timestamptz default null,
    p_after_id pg_catalog.uuid default null,
    p_status public.web_order_status default null
)
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
    v_has_more pg_catalog.bool;
    v_rows pg_catalog.jsonb;
begin
    if v_actor_id is null
       or not (
           public.has_permission('VIEW_BRANCH_SALES')
           or public.has_permission('VIEW_ALL_SALES')
       ) then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;

    if p_limit not between 1 and 100
       or ((p_after_created_at is null) <> (p_after_id is null)) then
        raise exception using errcode = '22023', message = 'WEB_ORDER_QUERY_INVALID';
    end if;

    select p.branch_id into v_actor_branch_id
    from public.profiles p
    where p.id = v_actor_id and p.is_active;

    if not public.has_permission('VIEW_ALL_SALES') and v_actor_branch_id is null then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;

    with scoped as (
        select o.*
        from public.web_orders o
        where (public.has_permission('VIEW_ALL_SALES') or o.branch_id = v_actor_branch_id)
          and (p_status is null or o.status = p_status)
          and (
              p_after_created_at is null
              or (o.created_at, o.id) < (p_after_created_at, p_after_id)
          )
        order by o.created_at desc, o.id desc
        limit p_limit + 1
    ), page as (
        select * from scoped
        order by created_at desc, id desc
        limit p_limit
    )
    select
        exists(select 1 from scoped offset p_limit),
        coalesce(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'id', o.id,
                    'orderNumber', 'VW-' || pg_catalog.upper(pg_catalog.left(pg_catalog.replace(o.id::pg_catalog.text, '-', ''), 10)),
                    'branch', pg_catalog.jsonb_build_object('id', b.id, 'code', b.code, 'name', b.name),
                    'customer', pg_catalog.jsonb_build_object(
                        'name', o.customer_name,
                        'phone', o.customer_phone,
                        'email', o.customer_email
                    ),
                    'notes', o.notes,
                    'subtotalCents', o.subtotal_cents,
                    'discountCents', o.discount_cents,
                    'totalCents', o.total_cents,
                    'status', o.status,
                    'createdAt', o.created_at,
                    'updatedAt', o.updated_at,
                    'items', coalesce(items.value, '[]'::pg_catalog.jsonb)
                ) order by o.created_at desc, o.id desc
            ),
            '[]'::pg_catalog.jsonb
        )
    into v_has_more, v_rows
    from page o
    join public.branches b on b.id = o.branch_id
    left join lateral (
        select pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'productId', i.product_id,
                'name', i.product_name,
                'code', i.internal_code,
                'quantity', i.quantity,
                'listPriceCents', i.list_price_cents,
                'unitPriceCents', i.unit_price_cents,
                'promotionName', i.promotion_name,
                'lineTotalCents', i.line_total_cents
            ) order by i.created_at, i.id
        ) as value
        from public.web_order_items i
        where i.order_id = o.id
    ) items on true;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', v_rows,
        'page', pg_catalog.jsonb_build_object(
            'limit', p_limit,
            'hasMore', v_has_more,
            'nextCursor', case
                when v_has_more and pg_catalog.jsonb_array_length(v_rows) > 0 then
                    pg_catalog.jsonb_build_object(
                        'createdAt', v_rows->-1->>'createdAt',
                        'id', v_rows->-1->>'id'
                    )
                else null
            end
        ),
        'serverTime', pg_catalog.now()
    );
end;
$$;

create or replace function public.set_admin_web_order_status(
    p_order_id pg_catalog.uuid,
    p_status public.web_order_status,
    p_observation pg_catalog.text default null
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_branch_id pg_catalog.uuid;
    v_order public.web_orders%rowtype;
    v_previous_status public.web_order_status;
    v_observation pg_catalog.text := nullif(pg_catalog.btrim(p_observation), '');
begin
    if v_actor_id is null
       or not (
           public.has_permission('VIEW_BRANCH_SALES')
           or public.has_permission('VIEW_ALL_SALES')
       ) then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;

    if p_order_id is null or p_status is null
       or pg_catalog.char_length(coalesce(v_observation, '')) > 500 then
        raise exception using errcode = '22023', message = 'WEB_ORDER_STATUS_INVALID';
    end if;

    select p.branch_id into v_actor_branch_id
    from public.profiles p
    where p.id = v_actor_id and p.is_active;

    select o.* into v_order
    from public.web_orders o
    where o.id = p_order_id
      and (public.has_permission('VIEW_ALL_SALES') or o.branch_id = v_actor_branch_id)
    for update;

    if not found then
        raise exception using errcode = 'P0001', message = 'WEB_ORDER_UNAVAILABLE';
    end if;

    if v_order.status = p_status then
        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'orderId', v_order.id,
            'status', v_order.status,
            'updatedAt', v_order.updated_at,
            'idempotentReplay', true
        );
    end if;

    if not (
        (v_order.status = 'PENDING' and p_status in ('CONFIRMED', 'CANCELLED'))
        or (v_order.status = 'CONFIRMED' and p_status in ('READY', 'CANCELLED'))
        or (v_order.status = 'READY' and p_status in ('COMPLETED', 'CANCELLED'))
    ) then
        raise exception using errcode = '22023', message = 'WEB_ORDER_STATUS_INVALID';
    end if;

    v_previous_status := v_order.status;

    update public.web_orders o
    set status = p_status
    where o.id = v_order.id
    returning * into v_order;

    insert into public.web_order_status_history (
        order_id, previous_status, new_status, changed_by, observation
    ) values (
        v_order.id, v_previous_status, p_status, v_actor_id, v_observation
    );

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'orderId', v_order.id,
        'status', v_order.status,
        'updatedAt', v_order.updated_at,
        'idempotentReplay', false
    );
end;
$$;

revoke all on function public.get_public_web_order_options()
from public, anon, authenticated, service_role;
revoke all on function public.submit_web_order(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.text,
    pg_catalog.text, pg_catalog.text, pg_catalog.jsonb
) from public, anon, authenticated, service_role;
revoke all on function public.get_admin_web_orders(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid, public.web_order_status
) from public, anon, authenticated, service_role;
revoke all on function public.set_admin_web_order_status(
    pg_catalog.uuid, public.web_order_status, pg_catalog.text
) from public, anon, authenticated, service_role;

grant execute on function public.get_public_web_order_options() to anon, authenticated;
grant execute on function public.submit_web_order(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.text,
    pg_catalog.text, pg_catalog.text, pg_catalog.jsonb
) to anon, authenticated;
grant execute on function public.get_admin_web_orders(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid, public.web_order_status
) to authenticated;
grant execute on function public.set_admin_web_order_status(
    pg_catalog.uuid, public.web_order_status, pg_catalog.text
) to authenticated;

comment on function public.submit_web_order(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.text, pg_catalog.text,
    pg_catalog.text, pg_catalog.text, pg_catalog.jsonb
) is 'Creates an idempotent public Web order with authoritative product and promotion prices.';

commit;
