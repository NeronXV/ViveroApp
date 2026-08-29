begin;

-- 1. Tabla de Promociones (Reglas de negocio)
create table public.promotions (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(trim(name)) between 2 and 100),
    description text,
    is_active boolean not null default true,
    starts_at timestamptz,
    ends_at timestamptz,
    -- Tipo de promocion: PERCENTAGE (sobre el total) o FIXED_AMOUNT
    promo_type text not null check (promo_type in ('PERCENTAGE', 'FIXED_AMOUNT')),
    value numeric(14,2) not null check (value > 0),
    min_purchase_cents bigint default 0 check (min_purchase_cents >= 0),
    max_discount_cents bigint check (max_discount_cents is null or max_discount_cents > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (starts_at is null or ends_at is null or starts_at < ends_at)
);

-- 2. Tabla de Descuentos Aplicados a Ventas
create table public.sale_discounts (
    id uuid primary key default gen_random_uuid(),
    sale_id uuid not null unique references public.sales(id) on delete cascade,
    promotion_id uuid references public.promotions(id) on delete set null,
    discount_cents bigint not null check (discount_cents >= 0),
    reason text not null,
    applied_by uuid not null references public.profiles(id) on delete restrict,
    created_at timestamptz not null default now()
);

-- 3. Seguridad e Indices
create index promotions_active_dates_idx on public.promotions(is_active) where is_active;
create index sale_discounts_sale_idx on public.sale_discounts(sale_id);

alter table public.promotions enable row level security;
alter table public.sale_discounts enable row level security;

create policy promotions_read on public.promotions
for select to authenticated using (true);

-- Solo ADMIN/OWNER pueden crear promociones
create policy promotions_manage on public.promotions
for all to authenticated
using (public.has_permission('MANAGE_SETTINGS'))
with check (public.has_permission('MANAGE_SETTINGS'));

create trigger promotions_set_updated_at before update on public.promotions
for each row execute function public.set_updated_at();

-- 4. RPC para Aplicar Descuento a una Venta
-- El descuento se aplica sobre el total actual de la venta (subtotal)
create or replace function public.apply_sale_discount(
    p_sale_id pg_catalog.uuid,
    p_discount_cents pg_catalog.bigint,
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
    v_reason pg_catalog.text := pg_catalog.btrim(p_reason);
begin
    if v_actor_id is null or not public.has_permission('MANAGE_DISCOUNTS') then
        raise exception using errcode = '42501', message = 'Discount management is not allowed';
    end if;

    select * into v_sale from public.sales where id = p_sale_id for update;

    if not found then
        raise exception using errcode = '22023', message = 'Sale unavailable';
    end if;

    if v_sale.status <> 'SENT_TO_CASHIER' then
        raise exception using errcode = 'P0001', message = 'Sale status must be SENT_TO_CASHIER to apply discounts';
    end if;

    if p_discount_cents < 0 or p_discount_cents > v_sale.subtotal_cents then
        raise exception using errcode = '22023', message = 'Invalid discount amount';
    end if;

    if v_reason is null or pg_catalog.char_length(v_reason) < 3 then
        raise exception using errcode = '22023', message = 'Discount reason is required';
    end if;

    -- Insertar o actualizar el descuento aplicado
    insert into public.sale_discounts (sale_id, promotion_id, discount_cents, reason, applied_by)
    values (p_sale_id, p_promotion_id, p_discount_cents, v_reason, v_actor_id)
    on conflict (sale_id) do update
    set promotion_id = excluded.promotion_id,
        discount_cents = excluded.discount_cents,
        reason = excluded.reason,
        applied_by = excluded.applied_by,
        created_at = excluded.created_at;

    -- Actualizar el total de la venta
    update public.sales
    set discount_cents = p_discount_cents,
        total_cents = subtotal_cents - p_discount_cents,
        updated_at = pg_catalog.now()
    where id = p_sale_id;
end;
$$;

-- 5. Privilegios
revoke all on table public.promotions, public.sale_discounts from public, anon, authenticated;
grant select on table public.promotions, public.sale_discounts to authenticated;

revoke all on function public.apply_sale_discount from public, anon, authenticated;
grant execute on function public.apply_sale_discount to authenticated;

commit;
