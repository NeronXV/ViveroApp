begin;

create type public.sale_status as enum (
    'DRAFT',
    'SENT_TO_CASHIER',
    'PAYMENT_PENDING',
    'PAID',
    'CANCELLED',
    'DELIVERED'
);

create table public.sales (
    id uuid primary key,
    folio text not null unique check (folio ~ '^VD-[0-9]{6}-[A-Z0-9]{6}$'),
    branch_id uuid not null references public.branches(id) on delete restrict,
    customer_id uuid,
    subtotal_cents bigint not null check (subtotal_cents >= 0),
    discount_cents bigint not null default 0 check (discount_cents >= 0 and discount_cents <= subtotal_cents),
    total_cents bigint not null check (total_cents = subtotal_cents - discount_cents),
    status public.sale_status not null default 'DRAFT',
    created_by uuid not null references public.profiles(id) on delete restrict,
    idempotency_key uuid not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.sale_items (
    id uuid primary key default gen_random_uuid(),
    sale_id uuid not null references public.sales(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete restrict,
    product_name text not null,
    internal_code text not null,
    quantity integer not null check (quantity > 0),
    list_price_cents bigint not null check (list_price_cents >= 0),
    unit_price_cents bigint not null check (unit_price_cents >= 0 and unit_price_cents <= list_price_cents),
    discount_cents bigint generated always as ((list_price_cents - unit_price_cents) * quantity) stored,
    line_total_cents bigint generated always as (unit_price_cents * quantity) stored,
    created_at timestamptz not null default now(),
    unique (sale_id, product_id)
);

create table public.sale_status_history (
    id uuid primary key default gen_random_uuid(),
    sale_id uuid not null references public.sales(id) on delete cascade,
    previous_status public.sale_status,
    new_status public.sale_status not null,
    changed_by uuid not null references public.profiles(id) on delete restrict,
    observation text,
    changed_at timestamptz not null default now()
);

create index sales_branch_status_created_idx on public.sales(branch_id, status, created_at desc);
create index sales_created_by_created_idx on public.sales(created_by, created_at desc);
create index sale_items_sale_idx on public.sale_items(sale_id);
create index sale_status_history_sale_changed_idx on public.sale_status_history(sale_id, changed_at);

create trigger sales_set_updated_at before update on public.sales
for each row execute function public.set_updated_at();

alter table public.sales enable row level security;
alter table public.sale_items enable row level security;
alter table public.sale_status_history enable row level security;

create policy sales_creator_read_own on public.sales
for select to authenticated
using (created_by = auth.uid() and public.has_permission('VIEW_OWN_SALES'));

create policy sales_cashier_read_pending on public.sales
for select to authenticated
using (
    branch_id = (select p.branch_id from public.profiles p where p.id = auth.uid())
    and status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')
    and public.has_permission('OPERATE_CASHIER')
);

create policy sales_management_read_branch on public.sales
for select to authenticated
using (
    branch_id = (select p.branch_id from public.profiles p where p.id = auth.uid())
    and public.has_permission('VIEW_BRANCH_SALES')
);

create policy sales_management_read_all on public.sales
for select to authenticated
using (public.has_permission('VIEW_ALL_SALES'));

create policy sale_items_read_visible_sale on public.sale_items
for select to authenticated
using (exists (select 1 from public.sales s where s.id = sale_items.sale_id));

create policy sale_history_read_visible_sale on public.sale_status_history
for select to authenticated
using (exists (select 1 from public.sales s where s.id = sale_status_history.sale_id));

grant select on public.sales, public.sale_items, public.sale_status_history to authenticated;

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
        pg_catalog.coalesce(pg_catalog.sum(p.price_cents * i.quantity), 0)
    into v_valid_product_count, v_subtotal
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4)
    join public.products p on p.id = i.product_id and p.is_active;

    -- Until branch inventory is introduced, the permitted product scope is the
    -- complete active catalog. Every requested row must match that scope.
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
        list_price_cents, unit_price_cents
    )
    select v_sale.id, p.id, p.common_name, p.internal_code, i.quantity,
           p.price_cents, p.price_cents
    from pg_catalog.jsonb_to_recordset(p_items)
        as i(product_id pg_catalog.uuid, quantity pg_catalog.int4)
    join public.products p on p.id = i.product_id and p.is_active;

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
) from public;
grant execute on function public.submit_sale_to_cashier(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) to authenticated;

commit;
