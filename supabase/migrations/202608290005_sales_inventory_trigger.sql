begin;

-- Trigger para registrar movimientos de inventario al completar una venta
create or replace function public.record_sale_inventory_movements()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
begin
    -- Solo actuar cuando la venta pasa a PAID
    if new.status = 'PAID' and (old.status is distinct from 'PAID') then
        -- Insertar movimientos de salida para cada partida de la venta
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
            -si.quantity, -- Negativo para representar salida
            new.id,
            pg_catalog.coalesce(v_actor_id, new.created_by),
            pg_catalog.concat('Venta confirmada: ', new.folio)
        from public.sale_items si
        where si.sale_id = new.id;
    end if;

    return new;
end;
$$;

revoke all on function public.record_sale_inventory_movements() from public, anon, authenticated;

create trigger on_sale_paid_record_inventory
after update on public.sales
for each row
execute function public.record_sale_inventory_movements();

comment on function public.record_sale_inventory_movements() is
'Automatically records SALE inventory movements for all items when a sale status changes to PAID.';

commit;
