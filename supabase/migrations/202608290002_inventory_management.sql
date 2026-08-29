begin;

-- 1. Tablas de Inventario

create table public.inventory_locations (
    id uuid primary key default gen_random_uuid(),
    branch_id uuid not null references public.branches(id) on delete restrict,
    name text not null check (char_length(trim(name)) between 2 and 60),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (branch_id, name)
);

create type public.inventory_movement_type as enum (
    'RECEPTION', -- Entrada por compra o produccion
    'ADJUSTMENT_ADD', -- Ajuste manual positivo
    'ADJUSTMENT_SUB', -- Ajuste manual negativo
    'SALE', -- Salida por venta (atÃ³mica)
    'TRANSFER_OUT', -- Salida por traspaso
    'TRANSFER_IN' -- Entrada por traspaso
);

create table public.inventory_movements (
    id uuid primary key default gen_random_uuid(),
    branch_id uuid not null references public.branches(id) on delete restrict,
    location_id uuid references public.inventory_locations(id) on delete restrict,
    product_id uuid not null references public.products(id) on delete restrict,
    movement_type public.inventory_movement_type not null,
    quantity numeric(14,3) not null check (quantity <> 0),
    reference_id uuid, -- ID de venta, traspaso, etc.
    notes text,
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now()
);

-- Tabla de saldos (proyecciÃ³n para consulta rÃ¡pida, mantenida por triggers)
create table public.inventory_balances (
    branch_id uuid not null references public.branches(id) on delete restrict,
    product_id uuid not null references public.products(id) on delete restrict,
    total_quantity numeric(14,3) not null default 0,
    updated_at timestamptz not null default now(),
    primary key (branch_id, product_id)
);

-- 2. Ãndices y Seguridad

create index inventory_movements_branch_product_idx on public.inventory_movements(branch_id, product_id, created_at desc);
create index inventory_movements_reference_idx on public.inventory_movements(reference_id) where reference_id is not null;
create index inventory_balances_quantity_idx on public.inventory_balances(total_quantity) where total_quantity > 0;

alter table public.inventory_locations enable row level security;
alter table public.inventory_movements enable row level security;
alter table public.inventory_balances enable row level security;

-- PolÃ­ticas: Lectura para personal activo, Escritura vÃ­a RPC o para personal con permisos
create policy inventory_locations_read on public.inventory_locations
for select to authenticated using (true);

create policy inventory_movements_read on public.inventory_movements
for select to authenticated
using (branch_id = (select branch_id from public.profiles where id = auth.uid()) or public.has_permission('VIEW_REPORTS'));

create policy inventory_balances_read on public.inventory_balances
for select to authenticated using (true);

-- 3. Trigger para mantener balances (Idempotente por inserciÃ³n de movimiento)

create or replace function public.update_inventory_balance()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.inventory_balances (branch_id, product_id, total_quantity, updated_at)
    values (new.branch_id, new.product_id, new.quantity, new.created_at)
    on conflict (branch_id, product_id) do update
    set total_quantity = public.inventory_balances.total_quantity + excluded.total_quantity,
        updated_at = excluded.updated_at;
    return new;
end;
$$;

create trigger on_inventory_movement_insert
after insert on public.inventory_movements
for each row execute function public.update_inventory_balance();

-- 4. RPCs Administrativos

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

    -- Determinar sucursal (la del actor si no se especifica y no es ADMIN/OWNER)
    if p_branch_id is not null and public.has_permission('MANAGE_BRANCHES') then
        v_branch_id := p_branch_id;
    else
        select branch_id into v_branch_id from public.profiles where id = v_actor_id;
    end if;

    if v_branch_id is null then
        raise exception using errcode = '22023', message = 'Branch is required';
    end if;

    -- Validar signo segun tipo
    if p_movement_type in ('RECEPTION', 'ADJUSTMENT_ADD', 'TRANSFER_IN') and v_quantity < 0 then
        v_quantity := v_quantity * -1;
    elsif p_movement_type in ('SALE', 'ADJUSTMENT_SUB', 'TRANSFER_OUT') and v_quantity > 0 then
        v_quantity := v_quantity * -1;
    end if;

    if v_quantity = 0 then
        raise exception using errcode = '22023', message = 'Quantity cannot be zero';
    end if;

    insert into public.inventory_movements (
        branch_id, location_id, product_id, movement_type, quantity, reference_id, notes, created_by
    ) values (
        v_branch_id, p_location_id, p_product_id, p_movement_type, v_quantity, p_reference_id, p_notes, v_actor_id
    );
end;
$$;

-- 5. Privilegios

revoke all on table public.inventory_locations, public.inventory_movements, public.inventory_balances from public, anon, authenticated;
grant select on table public.inventory_locations, public.inventory_balances to authenticated;
grant select on table public.inventory_movements to authenticated;

revoke all on function public.record_inventory_movement from public, anon, authenticated;
grant execute on function public.record_inventory_movement to authenticated;

commit;
