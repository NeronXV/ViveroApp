begin;

-- A web order becomes one ordinary cashier sale. Existing clients keep their contracts.
alter table public.sales add column web_order_id pg_catalog.uuid
    unique references public.web_orders(id) on delete restrict;

create or replace function public.send_web_order_to_cashier(p_order_id pg_catalog.uuid)
returns pg_catalog.jsonb
language plpgsql security definer set search_path = ''
as $$
declare
    v_actor pg_catalog.uuid := auth.uid();
    v_branch pg_catalog.uuid;
    v_order public.web_orders%rowtype;
    v_sale public.sales%rowtype;
    v_id pg_catalog.uuid;
    v_attempt integer;
begin
    if v_actor is null or not (
        public.has_permission('OPERATE_CASHIER') or public.has_permission('VIEW_BRANCH_SALES')
        or public.has_permission('VIEW_ALL_SALES')
    ) then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;
    select p.branch_id into v_branch from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor and p.is_active;
    if v_branch is null then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;
    select o.* into v_order from public.web_orders o
    where o.id = p_order_id and o.branch_id = v_branch for update;
    if not found then
        raise exception using errcode = '42501', message = 'WEB_ORDER_ADMIN_UNAUTHORIZED';
    end if;
    select s.* into v_sale from public.sales s where s.web_order_id = v_order.id;
    if found then
        return pg_catalog.jsonb_build_object('schemaVersion', 1, 'orderId', v_order.id,
            'saleId', v_sale.id, 'folio', v_sale.folio, 'status', v_sale.status,
            'totalCents', v_sale.total_cents, 'idempotentReplay', true);
    end if;
    if v_order.status not in ('CONFIRMED', 'READY') then
        raise exception using errcode = 'P0001', message = 'WEB_ORDER_STATUS_INVALID';
    end if;
    if not exists(select 1 from public.web_order_items i where i.order_id = v_order.id)
       or exists(select 1 from public.web_order_items i join public.products p on p.id = i.product_id
           where i.order_id = v_order.id and not p.is_active)
       or (select sum(i.line_total_cents) from public.web_order_items i where i.order_id = v_order.id) <> v_order.total_cents then
        raise exception using errcode = 'P0001', message = 'WEB_ORDER_ITEMS_UNAVAILABLE';
    end if;
    -- Preserve prices already accepted by the server for this order.
    for v_attempt in 1..5 loop
        v_id := pg_catalog.gen_random_uuid();
        begin
            insert into public.sales(id, folio, branch_id, subtotal_cents, discount_cents,
                total_cents, status, created_by, idempotency_key, web_order_id)
            values(v_id, 'VD-' || pg_catalog.to_char(pg_catalog.now(), 'YYMMDD') || '-'
                || pg_catalog.upper(pg_catalog.left(pg_catalog.replace(v_id::text, '-', ''), 6)),
                v_branch, v_order.subtotal_cents, v_order.discount_cents, v_order.total_cents,
                'SENT_TO_CASHIER', v_actor, v_id, v_order.id) returning * into v_sale;
            exit;
        exception when unique_violation then
            if v_attempt = 5 then raise; end if;
            -- Retry a random commercial folio collision inside the locked order.
        end;
    end loop;
    insert into public.sale_items(sale_id, product_id, product_name, internal_code,
        quantity, list_price_cents, unit_price_cents)
    select v_sale.id, i.product_id, i.product_name, i.internal_code, i.quantity,
        i.list_price_cents, i.unit_price_cents from public.web_order_items i where i.order_id = v_order.id;
    insert into public.sale_status_history(sale_id, previous_status, new_status, changed_by, observation)
    values(v_sale.id, null, 'SENT_TO_CASHIER', v_actor, 'Pedido web enviado a cobro presencial');
    return pg_catalog.jsonb_build_object('schemaVersion', 1, 'orderId', v_order.id,
        'saleId', v_sale.id, 'folio', v_sale.folio, 'status', v_sale.status,
        'totalCents', v_sale.total_cents, 'idempotentReplay', false);
end;
$$;
revoke all on function public.send_web_order_to_cashier(pg_catalog.uuid) from public, anon, authenticated;
grant execute on function public.send_web_order_to_cashier(pg_catalog.uuid) to authenticated;

create or replace function public.guard_web_order_checkout()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
    if new.status is distinct from old.status then
        if new.status = 'COMPLETED' and not exists (
            select 1 from public.sales s join public.sale_payments p on p.sale_id = s.id
            where s.web_order_id = new.id and s.status in ('PAID', 'DELIVERED')
        ) then
            raise exception using errcode = 'P0001', message = 'WEB_ORDER_PAYMENT_REQUIRED';
        end if;
        if new.status = 'CANCELLED' and exists(select 1 from public.sales s where s.web_order_id = new.id) then
            raise exception using errcode = 'P0001', message = 'WEB_ORDER_ALREADY_IN_CASHIER';
        end if;
    end if;
    return new;
end;
$$;
revoke all on function public.guard_web_order_checkout() from public, anon, authenticated;
create trigger web_order_checkout_guard before update of status on public.web_orders
for each row execute function public.guard_web_order_checkout();

-- Activation is explicit for each branch after its initial physical count.
create table public.branch_inventory_activation (
    branch_id pg_catalog.uuid primary key references public.branches(id) on delete restrict,
    activated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    activated_by pg_catalog.uuid not null references public.profiles(id) on delete restrict
);
alter table public.branch_inventory_activation enable row level security;
revoke all on table public.branch_inventory_activation from public, anon, authenticated;

create or replace function public.get_my_inventory_activation()
returns pg_catalog.jsonb language plpgsql stable security definer set search_path = ''
as $$
declare v_branch pg_catalog.uuid; v_activation public.branch_inventory_activation%rowtype;
begin
    if auth.uid() is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'INVENTORY_UNAUTHORIZED';
    end if;
    select p.branch_id into v_branch from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active where p.id = auth.uid() and p.is_active;
    if v_branch is null then raise exception using errcode = '42501', message = 'INVENTORY_UNAUTHORIZED'; end if;
    select * into v_activation from public.branch_inventory_activation where branch_id = v_branch;
    return pg_catalog.jsonb_build_object('schemaVersion', 1, 'branchId', v_branch,
        'enabled', v_activation.branch_id is not null, 'activatedAt', v_activation.activated_at);
end;
$$;
revoke all on function public.get_my_inventory_activation() from public, anon, authenticated;
grant execute on function public.get_my_inventory_activation() to authenticated;

create or replace function public.activate_my_inventory(p_initial_count_confirmed pg_catalog.bool)
returns pg_catalog.jsonb language plpgsql security definer set search_path = ''
as $$
declare v_state pg_catalog.jsonb; v_branch pg_catalog.uuid;
begin
    v_state := public.get_my_inventory_activation();
    v_branch := (v_state->>'branchId')::pg_catalog.uuid;
    if p_initial_count_confirmed is distinct from true then
        raise exception using errcode = '22023', message = 'INVENTORY_INITIAL_COUNT_REQUIRED';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_branch::text, 808));
    insert into public.branch_inventory_activation(branch_id, activated_by)
    values(v_branch, auth.uid()) on conflict(branch_id) do nothing;
    return public.get_my_inventory_activation();
end;
$$;
revoke all on function public.activate_my_inventory(pg_catalog.bool) from public, anon, authenticated;
grant execute on function public.activate_my_inventory(pg_catalog.bool) to authenticated;

create or replace function public.record_sale_inventory_movements()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare v_item record;
begin
    if new.status = 'PAID' and old.status is distinct from 'PAID' then
        perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.branch_id::text, 808));
        if not exists(select 1 from public.branch_inventory_activation where branch_id = new.branch_id) then return new; end if;
        for v_item in select product_id, sum(quantity) as quantity from public.sale_items
            where sale_id = new.id group by product_id order by product_id
        loop
            begin
                insert into public.inventory_movements(branch_id, product_id, movement_type, quantity,
                    reference_id, created_by, notes)
                values(new.branch_id, v_item.product_id, 'SALE', -v_item.quantity, new.id,
                    coalesce(auth.uid(), new.created_by), 'Venta confirmada: ' || new.folio)
                on conflict(reference_id, product_id) where reference_id is not null and movement_type = 'SALE' do nothing;
            exception when check_violation then
                raise exception using errcode = 'P0001', message = 'INVENTORY_INSUFFICIENT';
            end;
        end loop;
    end if;
    return new;
end;
$$;
revoke all on function public.record_sale_inventory_movements() from public, anon, authenticated;
alter table public.sales enable trigger on_sale_paid_record_inventory;
comment on function public.record_sale_inventory_movements() is
'Deducts stock atomically at payment only in explicitly activated branches; never backfills historical sales.';

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
                    'checkout', (select pg_catalog.jsonb_build_object(
                        'saleId', s.id, 'folio', s.folio, 'status', s.status, 'totalCents', s.total_cents
                    ) from public.sales s where s.web_order_id = o.id),
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


commit;
