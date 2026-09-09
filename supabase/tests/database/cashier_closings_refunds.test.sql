begin;

create extension if not exists pgtap with schema extensions;
select extensions.plan(8);

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


insert into public.sales(id,folio,branch_id,subtotal_cents,total_cents,status,created_by,idempotency_key)
values('95000000-0000-4000-8000-000000000041','VD-260908-CASH01','95000000-0000-4000-8000-000000000001',15000,15000,'SENT_TO_CASHIER','95000000-0000-4000-8000-000000000011','95000000-0000-4000-8000-000000000041');
insert into public.sale_items(sale_id,product_id,product_name,internal_code,quantity,list_price_cents,unit_price_cents)
values('95000000-0000-4000-8000-000000000041','95000000-0000-4000-8000-000000000031','Planta Sin Stock','GRAD-01',1,15000,15000);
set local role authenticated;
set local request.jwt.claims='{"sub":"95000000-0000-4000-8000-000000000011","role":"authenticated"}';
select public.confirm_sale_payment(
 '95000000-0000-4000-8000-000000000041',
 (public.claim_sale_for_payment('95000000-0000-4000-8000-000000000041')->>'claim_token')::uuid,
 '95000000-0000-4000-8000-000000000042','CASH',20000,null);
select extensions.is((public.get_my_cashier_closing_preview()#>>'{payments,cash}')::bigint,15000::bigint,'closing excludes change from cash income');
select extensions.is((public.get_refundable_sale('VD-260908-CASH01')->>'amountCents')::bigint,15000::bigint,'refund is paid amount, not cash tendered');
select extensions.throws_ok(
 $$select public.refund_sale_in_person('VD-260908-CASH01','Cliente devuelve','CASH',true,false,'95000000-0000-4000-8000-000000000043')$$,
 '22023','REFUND_DATA_INVALID','refund requires money-return acknowledgement');
select extensions.is((public.refund_sale_in_person('VD-260908-CASH01','Cliente devuelve','CASH',true,true,'95000000-0000-4000-8000-000000000043')->>'amountCents')::bigint,15000::bigint,'full refund uses authoritative payment');
select extensions.is((public.refund_sale_in_person('VD-260908-CASH01','Cliente devuelve','CASH',true,true,'95000000-0000-4000-8000-000000000043')->>'idempotentReplay')::boolean,true,'same refund retry is idempotent');
select extensions.is((public.close_my_cashier(10000,10000,'95000000-0000-4000-8000-000000000044')->>'differenceCents')::bigint,0::bigint,'closing reconciles opening plus payment minus refund');
select extensions.is((public.get_my_cashier_closing_preview()#>>'{payments,count}')::bigint,0::bigint,'closed payment is excluded from next shift');
reset role;
select extensions.is((select count(*) from public.inventory_movements where product_id='95000000-0000-4000-8000-000000000031'),0::bigint,'refund does not invent stock for a sale before activation');
select extensions.finish();
rollback;
