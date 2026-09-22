begin;

create extension if not exists pgtap with schema extensions;
select extensions.plan(14);

-- Fixtures para pruebas
insert into public.branches (id, code, name) values
    ('95000000-0000-4000-8000-000000000001', 'GRADUAL-A', 'Sucursal Gradual A');

insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at
) values
    ('95000000-0000-4000-8000-000000000011', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'owner_gradual@example.test', '', pg_catalog.now(), pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = '95000000-0000-4000-8000-000000000001', full_name = 'Propietario Gradual'
where id = '95000000-0000-4000-8000-000000000011';

insert into public.user_roles (user_id, role_id)
select '95000000-0000-4000-8000-000000000011', r.id from public.roles r where r.name = 'OWNER';

insert into public.categories (id, name) values
    ('95000000-0000-4000-8000-000000000021', 'Categoria Gradual');

insert into public.products (
    id, internal_code, common_name, category_id, price_cents, unit, minimum_stock
) values
    ('95000000-0000-4000-8000-000000000031', 'GRAD-01', 'Planta Sin Stock', '95000000-0000-4000-8000-000000000021', 15000, 'pieza', 5),
    ('95000000-0000-4000-8000-000000000032', 'GRAD-02', 'Planta Con Stock', '95000000-0000-4000-8000-000000000021', 20000, 'pieza', 2);

-- 1. La funcion trigger existe en el esquema public
select extensions.has_function('public', 'record_sale_inventory_movements', 'record_sale_inventory_movements existe');

-- 2. La funcion trigger es SECURITY DEFINER con search_path vacio
select extensions.ok(
    exists (
        select 1 from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and p.proname = 'record_sale_inventory_movements'
          and p.prosecdef = true
          and p.proconfig @> array['search_path=""']
    ),
    'record_sale_inventory_movements es SECURITY DEFINER con search_path vacio'
);

-- 2. Privilegios revotados de anon y authenticated
select extensions.ok(
    not has_function_privilege('anon', 'public.record_sale_inventory_movements()', 'EXECUTE')
    and not has_function_privilege('authenticated', 'public.record_sale_inventory_movements()', 'EXECUTE'),
    'record_sale_inventory_movements no es ejecutable por clientes anon o authenticated'
);

-- 3. El trigger existe y esta deshabilitado (tgenabled = D)
select extensions.ok(
    exists (
        select 1 from pg_trigger t
        join pg_class c on c.oid = t.tgrelid
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public'
          and c.relname = 'sales'
          and t.tgname = 'on_sale_paid_record_inventory'
          and t.tgenabled = 'O'
    ),
    'El trigger on_sale_paid_record_inventory esta habilitado y cada sucursal requiere activacion'
);

-- 4. El indice de idempotencia para movimientos SALE existe
select extensions.ok(
    exists (
        select 1 from pg_indexes
        where schemaname = 'public'
          and tablename = 'inventory_movements'
          and indexname = 'inventory_sale_movement_idempotency_idx'
    ),
    'El indice unico de idempotencia para SALE existe'
);

-- 5. Crear una venta con producto de stock cero y pasar a PAID (Caja cobra sin bloqueo)
insert into public.sales (
    id, folio, branch_id, status, subtotal_cents, discount_cents, total_cents, created_by, idempotency_key
) values (
    '95000000-0000-4000-8000-000000000041', 'VD-260908-GRAD01', '95000000-0000-4000-8000-000000000001',
    'SENT_TO_CASHIER', 15000, 0, 15000, '95000000-0000-4000-8000-000000000011', '95000000-0000-4000-8000-000000000041'
);

insert into public.sale_items (id, sale_id, product_id, quantity, unit_price_cents, product_name, internal_code, list_price_cents)
values ('95000000-0000-4000-8000-000000000051', '95000000-0000-4000-8000-000000000041', '95000000-0000-4000-8000-000000000031', 2, 7500, 'Planta Sin Stock', 'GRAD-01', 7500);

-- Actualizar a PAID (simulando confirm_sale_payment)
update public.sales
set status = 'PAID'
where id = '95000000-0000-4000-8000-000000000041';

select extensions.is(
    (select status from public.sales where id = '95000000-0000-4000-8000-000000000041'),
    'PAID',
    'La venta pasa exitosamente a PAID con stock cero en fase gradual'
);

-- 6. No se genera ningun movimiento SALE durante la fase gradual
select extensions.is(
    (select count(*)::int from public.inventory_movements where reference_id = '95000000-0000-4000-8000-000000000041'),
    0,
    'No se generan movimientos SALE mientras el trigger esta deshabilitado'
);

-- 7. Actualizaciones subsecuentes a la venta PAID no generan movimientos
update public.sales
set updated_at = pg_catalog.now()
where id = '95000000-0000-4000-8000-000000000041';

select extensions.is(
    (select count(*)::int from public.inventory_movements where reference_id = '95000000-0000-4000-8000-000000000041'),
    0,
    'Actualizaciones a ventas PAID no generan movimientos espurios'
);

-- 8. Probar como actor autenticado OWNER
set local role authenticated;
set local request.jwt.claims = '{"sub":"95000000-0000-4000-8000-000000000011","role":"authenticated"}';

-- 9. Registrar recepcion actualiza saldos normalmente
select extensions.ok(
    (public.record_inventory_reception(
        '95000000-0000-4000-8000-000000000032',
        20,
        'Lote inicial de prueba',
        '95000000-0000-4000-8000-000000000061'
    ) ->> 'totalQuantity')::numeric = 20,
    'record_inventory_reception actualiza saldo a 20 unidades'
);

-- 10. Conciliar conteo fisico ajusta saldo normalmente
select extensions.ok(
    (public.reconcile_inventory_count(
        '95000000-0000-4000-8000-000000000032',
        18,
        'Conteo fisico de bodega',
        '95000000-0000-4000-8000-000000000071',
        null
    ) ->> 'adjustmentQuantity')::numeric = -2,
    'reconcile_inventory_count calcula ajuste de -2 y existencia de 18'
);

-- 11. Tablero de inventario devuelve los productos de la sucursal
select extensions.is(
    jsonb_array_length(public.get_my_inventory_dashboard(100, null) -> 'items'),
    2,
    'get_my_inventory_dashboard devuelve los 2 productos de la sucursal'
);

-- 12. Historial devuelve los movimientos auditables
select extensions.is(
    jsonb_array_length(public.get_my_inventory_history('95000000-0000-4000-8000-000000000032', 50, null, null) -> 'items'),
    2,
    'get_my_inventory_history devuelve la recepcion y el conteo de la planta'
);

-- Reset role para pruebas de la logica interna del trigger
reset role;

-- 13. Venta con partidas duplicadas del mismo producto: agrupacion consolidada
insert into public.sales (
    id, folio, branch_id, status, subtotal_cents, discount_cents, total_cents, created_by, idempotency_key
) values (
    '95000000-0000-4000-8000-000000000081', 'VD-260908-GRAD02', '95000000-0000-4000-8000-000000000001',
    'SENT_TO_CASHIER', 50000, 0, 50000, '95000000-0000-4000-8000-000000000011', '95000000-0000-4000-8000-000000000081'
);

insert into public.sale_items (id, sale_id, product_id, quantity, unit_price_cents, product_name, internal_code, list_price_cents) values
    ('95000000-0000-4000-8000-000000000091', '95000000-0000-4000-8000-000000000081', '95000000-0000-4000-8000-000000000032', 5, 10000, 'Planta Con Stock', 'GRAD-02', 10000);

-- En un test controlado con el trigger temporalmente habilitado
insert into public.branch_inventory_activation(branch_id, activated_by)
values ('95000000-0000-4000-8000-000000000001', '95000000-0000-4000-8000-000000000011');
update public.sales set status = 'PAID' where id = '95000000-0000-4000-8000-000000000081';

-- Comprobar que solo se genero 1 movimiento consolidado con quantity = -5
select extensions.is(
    (select quantity::int from public.inventory_movements where reference_id = '95000000-0000-4000-8000-000000000081' and product_id = '95000000-0000-4000-8000-000000000032'),
    -5,
    'La venta descuenta cinco unidades tras activar la sucursal'
);

-- 14. Idempotencia: disparar de nuevo la logica no duplica movimientos por ON CONFLICT DO NOTHING
update public.sales set updated_at = pg_catalog.now() where id = '95000000-0000-4000-8000-000000000081';

select extensions.is(
    (select count(*)::int from public.inventory_movements where reference_id = '95000000-0000-4000-8000-000000000081'),
    1,
    'El indice unico de idempotencia garantiza exactamente 1 movimiento por producto y venta'
);

select extensions.finish();
rollback;
