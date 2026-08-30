begin;

-- 1. Comprobacion segura de duplicados existentes de movimientos SALE
do $$
declare
    v_dup_count pg_catalog.int4;
begin
    select count(*)
    into v_dup_count
    from (
        select reference_id, product_id
        from public.inventory_movements
        where reference_id is not null and movement_type = 'SALE'
        group by reference_id, product_id
        having count(*) > 1
    ) dups;

    if v_dup_count > 0 then
        raise exception using
            errcode = 'P0001',
            message = pg_catalog.concat('Cannot create unique index: found ', v_dup_count, ' duplicate SALE movement group(s) by (reference_id, product_id).');
    end if;
end;
$$;

-- 2. Indice unico de idempotencia para movimientos SALE por venta y producto
create unique index if not exists inventory_sale_movement_idempotency_idx
on public.inventory_movements(reference_id, product_id)
where reference_id is not null and movement_type = 'SALE';

-- 3. Actualizacion de la funcion trigger agrupando partidas por producto e insercion idempotente
create or replace function public.record_sale_inventory_movements()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
begin
    -- Solo actuar cuando la venta pasa a PAID desde un estado no PAID
    if new.status = 'PAID' and (old.status is distinct from 'PAID') then
        -- Insertar movimientos de salida agrupando partidas por producto para idempotencia
        insert into public.inventory_movements (
            branch_id,
            product_id,
            movement_type,
            quantity,
            reference_id,
            created_by,
            notes
        )
        select
            new.branch_id,
            si.product_id,
            'SALE',
            -sum(si.quantity), -- Suma consolidada de partidas del mismo producto
            new.id,
            coalesce(v_actor_id, new.created_by),
            pg_catalog.concat('Venta confirmada: ', new.folio)
        from public.sale_items si
        where si.sale_id = new.id
        group by si.product_id
        on conflict (reference_id, product_id) where reference_id is not null and movement_type = 'SALE'
        do nothing;
    end if;

    return new;
end;
$$;

revoke all on function public.record_sale_inventory_movements() from public, anon, authenticated;

comment on function public.record_sale_inventory_movements() is
'Idempotently records SALE inventory movements grouped by product when sale changes to PAID. Disabled during gradual inventory rollout phase.';

-- 4. Desactivar el trigger en public.sales para la fase gradual de captura de inventario inicial
alter table public.sales disable trigger on_sale_paid_record_inventory;

commit;
