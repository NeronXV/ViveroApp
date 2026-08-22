begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(50);

insert into public.branches (id, code, name, is_active) values
    ('11000000-0000-0000-0000-000000000001', 'PAY-CENTRO', 'Caja Centro', true),
    ('11000000-0000-0000-0000-000000000002', 'PAY-NORTE', 'Caja Norte', true),
    ('11000000-0000-0000-0000-000000000003', 'PAY-INACT', 'Caja Inactiva', false);

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'pay-sales@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Ventas Pagos"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'pay-cashier-a@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja A"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'pay-cashier-b@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja B"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'pay-cashier-north@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Norte"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'pay-owner@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Owner Pagos"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'pay-admin@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Admin Pagos"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000007', 'authenticated', 'authenticated', 'pay-inactive@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Caja Inactiva"}', pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-0000-0000-000000000008', 'authenticated', 'authenticated', 'pay-branch-inactive@test.invalid', '', pg_catalog.now(), '{}', '{"full_name":"Sucursal Inactiva"}', pg_catalog.now(), pg_catalog.now());

update public.profiles
set branch_id = case
    when id = '21000000-0000-0000-0000-000000000004' then '11000000-0000-0000-0000-000000000002'::pg_catalog.uuid
    when id = '21000000-0000-0000-0000-000000000008' then '11000000-0000-0000-0000-000000000003'::pg_catalog.uuid
    else '11000000-0000-0000-0000-000000000001'::pg_catalog.uuid
end;

update public.profiles
set is_active = false
where id = '21000000-0000-0000-0000-000000000007';

insert into public.user_roles (user_id, role_id)
select assigned.user_id, r.id
from (values
    ('21000000-0000-0000-0000-000000000001'::pg_catalog.uuid, 'SALES'),
    ('21000000-0000-0000-0000-000000000002'::pg_catalog.uuid, 'CASHIER'),
    ('21000000-0000-0000-0000-000000000003'::pg_catalog.uuid, 'CASHIER'),
    ('21000000-0000-0000-0000-000000000004'::pg_catalog.uuid, 'CASHIER'),
    ('21000000-0000-0000-0000-000000000005'::pg_catalog.uuid, 'OWNER'),
    ('21000000-0000-0000-0000-000000000006'::pg_catalog.uuid, 'ADMIN'),
    ('21000000-0000-0000-0000-000000000007'::pg_catalog.uuid, 'CASHIER'),
    ('21000000-0000-0000-0000-000000000008'::pg_catalog.uuid, 'CASHIER')
) as assigned(user_id, role_name)
join public.roles r on r.name = assigned.role_name;

insert into public.sales (
    id, folio, branch_id, subtotal_cents, total_cents, status, created_by, idempotency_key
) values
    ('51000000-0000-0000-0000-000000000001', 'VD-100001-PAY001', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000001'),
    ('51000000-0000-0000-0000-000000000002', 'VD-100002-PAY002', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000002'),
    ('51000000-0000-0000-0000-000000000003', 'VD-100003-PAY003', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000003'),
    ('51000000-0000-0000-0000-000000000004', 'VD-100004-PAY004', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000004'),
    ('51000000-0000-0000-0000-000000000005', 'VD-100005-PAY005', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000005'),
    ('51000000-0000-0000-0000-000000000006', 'VD-100006-PAY006', '11000000-0000-0000-0000-000000000002', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000004', '61000000-0000-0000-0000-000000000006'),
    ('51000000-0000-0000-0000-000000000007', 'VD-100007-PAY007', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'DRAFT', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000007'),
    ('51000000-0000-0000-0000-000000000008', 'VD-100008-PAY008', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000008'),
    ('51000000-0000-0000-0000-000000000009', 'VD-100009-PAY009', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000009'),
    ('51000000-0000-0000-0000-000000000010', 'VD-100010-PAY010', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000010'),
    ('51000000-0000-0000-0000-000000000011', 'VD-100011-PAY011', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000011'),
    ('51000000-0000-0000-0000-000000000012', 'VD-100012-PAY012', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000012'),
    ('51000000-0000-0000-0000-000000000013', 'VD-100013-PAY013', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000013'),
    ('51000000-0000-0000-0000-000000000014', 'VD-100014-PAY014', '11000000-0000-0000-0000-000000000001', 10000, 10000, 'SENT_TO_CASHIER', '21000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000014');

select extensions.results_eq(
    $$select enumlabel from pg_catalog.pg_enum where enumtypid = 'public.payment_method'::pg_catalog.regtype order by enumsortorder$$,
    $$values ('CASH'::pg_catalog.name), ('CARD'::pg_catalog.name), ('TRANSFER'::pg_catalog.name)$$,
    'payment methods are restricted to the approved MVP list'
);

select extensions.ok(
    (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.sale_payment_claims'::pg_catalog.regclass),
    'claims use RLS'
);
select extensions.ok(
    (select c.relrowsecurity from pg_catalog.pg_class c where c.oid = 'public.sale_payments'::pg_catalog.regclass),
    'payments use RLS'
);
select extensions.ok(
    (select pg_catalog.bool_and(
        not pg_catalog.has_table_privilege('authenticated', target.table_name, privilege.name)
     )
     from (values
        ('public.sale_payment_claims'::pg_catalog.text),
        ('public.sale_payments'::pg_catalog.text)
     ) target(table_name)
     cross join (values
        ('SELECT'::pg_catalog.text), ('INSERT'::pg_catalog.text),
        ('UPDATE'::pg_catalog.text), ('DELETE'::pg_catalog.text),
        ('TRUNCATE'::pg_catalog.text), ('REFERENCES'::pg_catalog.text),
        ('TRIGGER'::pg_catalog.text)
     ) privilege(name)),
    'authenticated has no direct access to payment tables'
);
select extensions.ok(
    (select pg_catalog.bool_and(
        not pg_catalog.has_table_privilege('anon', target.table_name, privilege.name)
     )
     from (values
        ('public.sale_payment_claims'::pg_catalog.text),
        ('public.sale_payments'::pg_catalog.text)
     ) target(table_name)
     cross join (values
        ('SELECT'::pg_catalog.text), ('INSERT'::pg_catalog.text),
        ('UPDATE'::pg_catalog.text), ('DELETE'::pg_catalog.text),
        ('TRUNCATE'::pg_catalog.text), ('REFERENCES'::pg_catalog.text),
        ('TRIGGER'::pg_catalog.text)
     ) privilege(name)),
    'anon has no direct access to payment tables'
);
select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.claim_sale_for_payment(uuid,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.release_sale_payment_claim(uuid,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.confirm_sale_payment(uuid,uuid,uuid,public.payment_method,bigint,text)', 'EXECUTE'),
    'authenticated can execute the three payment RPCs'
);
select extensions.ok(
    not pg_catalog.has_function_privilege('anon', 'public.claim_sale_for_payment(uuid,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.release_sale_payment_claim(uuid,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.confirm_sale_payment(uuid,uuid,uuid,public.payment_method,bigint,text)', 'EXECUTE'),
    'anon cannot execute payment RPCs'
);
select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_proc p
        cross join lateral pg_catalog.aclexplode(coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))) acl
        where p.oid = any(array[
            'public.claim_sale_for_payment(uuid,uuid)'::pg_catalog.regprocedure::pg_catalog.oid,
            'public.release_sale_payment_claim(uuid,uuid)'::pg_catalog.regprocedure::pg_catalog.oid,
            'public.confirm_sale_payment(uuid,uuid,uuid,public.payment_method,bigint,text)'::pg_catalog.regprocedure::pg_catalog.oid
        ]) and acl.grantee = 0 and acl.privilege_type = 'EXECUTE'
    ),
    'PUBLIC cannot execute payment RPCs'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
set local "request.jwt.claims" = '{"sub":"21000000-0000-0000-0000-000000000002","role":"authenticated"}';
select extensions.lives_ok(
    $$with claimed as (
        select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000001', null) response
    ) select public.confirm_sale_payment(
        '51000000-0000-0000-0000-000000000001',
        (response ->> 'claim_token')::pg_catalog.uuid,
        '71000000-0000-0000-0000-000000000001', 'CASH', 12000, null
    ) from claimed$$,
    'CASH payment succeeds with sufficient received amount'
);
reset role;
select extensions.is((select amount_due_cents from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000001'), 10000::pg_catalog.int8, 'amount due comes from sales');
select extensions.is((select amount_received_cents from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000001'), 12000::pg_catalog.int8, 'CASH stores received cents');
select extensions.is((select change_cents from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000001'), 2000::pg_catalog.int8, 'CASH change is calculated by the server');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.throws_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000002', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000002', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000002', 'CASH', 9999, null) from claimed$$,
    'P0001', 'CASH_AMOUNT_INSUFFICIENT', 'insufficient CASH is rejected'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000003', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000003', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000003', 'CARD', null, 'OP-123456') from claimed$$,
    'CARD payment succeeds for an external terminal operation'
);
reset role;
select extensions.ok(
    (select amount_received_cents = amount_due_cents and change_cents = 0 and reference = 'OP-123456'
     from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000003'),
    'CARD records the server total and short reference'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000004', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000004', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000004', 'TRANSFER', null, 'TRX-STAGING-01') from claimed$$,
    'TRANSFER succeeds with a reference'
);
reset role;
select extensions.is((select reference from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000004'), 'TRX-STAGING-01', 'TRANSFER stores its normalized reference');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.throws_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000005', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000005', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000005', 'TRANSFER', null, '  ') from claimed$$,
    'P0001', 'TRANSFER_REFERENCE_REQUIRED', 'TRANSFER without reference is rejected'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000001';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000002', null)$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'user without OPERATE_CASHIER is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000007';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000002', null)$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'inactive profile is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000008';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000002', null)$$, 'P0001', 'CASHIER_UNAUTHORIZED', 'inactive branch is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000005';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000006', null)$$, 'P0001', 'SALE_UNAVAILABLE', 'OWNER cannot operate a sale in another branch');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000006';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000006', null)$$, 'P0001', 'SALE_UNAVAILABLE', 'ADMIN cannot operate a sale in another branch');
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000009999', null)$$, 'P0001', 'SALE_UNAVAILABLE', 'missing sale is rejected without disclosure');
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000007', null)$$, 'P0001', 'SALE_STATUS_INVALID', 'sale in invalid status is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000008', null)$$, 'first cashier claims a sale');
reset role;
set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000003';
select extensions.throws_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000008', null)$$, 'P0001', 'CLAIM_UNAVAILABLE', 'active claim owned by another cashier is rejected');
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000009', null)$$, 'claim for expiration test is created');
reset role;
update public.sale_payment_claims
set claim_token = '81000000-0000-0000-0000-000000000009',
    created_at = pg_catalog.now() - interval '10 minutes',
    expires_at = pg_catalog.now() - interval '5 minutes'
where sale_id = '51000000-0000-0000-0000-000000000009';
set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.throws_ok(
    $$select public.confirm_sale_payment('51000000-0000-0000-0000-000000000009', '81000000-0000-0000-0000-000000000009',
      '71000000-0000-0000-0000-000000000009', 'CASH', 10000, null)$$,
    'P0001', 'CLAIM_EXPIRED', 'expired claim cannot confirm payment'
);
select extensions.lives_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000009', null)$$, 'expired claim can be replaced safely');
reset role;
select extensions.ok(
    (select pg_catalog.count(*) = 1 from public.sale_payment_claims where sale_id = '51000000-0000-0000-0000-000000000009' and closed_reason = 'EXPIRED')
    and (select pg_catalog.count(*) = 1 from public.sale_payment_claims where sale_id = '51000000-0000-0000-0000-000000000009' and released_at is null and consumed_at is null),
    'replacement closes the expired claim and leaves one open claim'
);

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000010', null) response)
      select public.release_sale_payment_claim('51000000-0000-0000-0000-000000000010', (response ->> 'claim_token')::uuid) from claimed$$,
    'claim owner can release it'
);
reset role;
select extensions.is((select closed_reason from public.sale_payment_claims where sale_id = '51000000-0000-0000-0000-000000000010'), 'RELEASED', 'release is audited');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok($$select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000011', null)$$, 'claim for ownership test is created');
reset role;
update public.sale_payment_claims set claim_token = '81000000-0000-0000-0000-000000000011' where sale_id = '51000000-0000-0000-0000-000000000011';
set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000003';
select extensions.throws_ok(
    $$select public.release_sale_payment_claim('51000000-0000-0000-0000-000000000011', '81000000-0000-0000-0000-000000000011')$$,
    'P0001', 'CLAIM_NOT_OWNED', 'another cashier cannot release the claim'
);
reset role;

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.lives_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000012', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000012', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000012', 'CASH', 10000, null) from claimed$$,
    'first serialized confirmation succeeds'
);
select extensions.throws_ok(
    $$select public.confirm_sale_payment('51000000-0000-0000-0000-000000000012', '81000000-0000-0000-0000-000000000012',
      '72000000-0000-0000-0000-000000000012', 'CASH', 10000, null)$$,
    'P0001', 'SALE_ALREADY_PAID', 'competing confirmation with a new key is rejected'
);
reset role;
select extensions.is((select pg_catalog.count(*) from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000012'), 1::pg_catalog.int8, 'competing confirmations create one payment');
select extensions.is((select pg_catalog.count(*) from public.sale_status_history where sale_id = '51000000-0000-0000-0000-000000000012' and previous_status = 'SENT_TO_CASHIER' and new_status = 'PAID'), 1::pg_catalog.int8, 'payment transition history is created exactly once');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.is(
    (select (public.confirm_sale_payment('51000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001',
      '71000000-0000-0000-0000-000000000001', 'CASH', 12000, null) ->> 'idempotent_replay')::pg_catalog.bool),
    true,
    'same idempotency key and payload returns canonical result'
);
select extensions.throws_ok(
    $$select public.confirm_sale_payment('51000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001',
      '71000000-0000-0000-0000-000000000001', 'CASH', 13000, null)$$,
    'P0001', 'IDEMPOTENCY_CONFLICT', 'same key with different payload conflicts'
);
select extensions.throws_ok(
    $$select public.confirm_sale_payment('51000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001',
      '72000000-0000-0000-0000-000000000001', 'CASH', 12000, null)$$,
    'P0001', 'SALE_ALREADY_PAID', 'new key on paid sale conflicts without duplication'
);
reset role;
select extensions.is((select pg_catalog.count(*) from public.sale_payments where sale_id = '51000000-0000-0000-0000-000000000001'), 1::pg_catalog.int8, 'idempotent retries leave one payment');
select extensions.is((select pg_catalog.count(*) from public.sale_status_history where sale_id = '51000000-0000-0000-0000-000000000001' and new_status = 'PAID'), 1::pg_catalog.int8, 'idempotent retries leave one PAID history row');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.is(
    (with claimed as (
        select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000013', null)
    ) select s.status::pg_catalog.text
      from claimed
      join public.sales s on s.id = '51000000-0000-0000-0000-000000000013'),
    'SENT_TO_CASHIER',
    'claiming does not change the sale to PAYMENT_PENDING'
);
select extensions.ok(
    (with first_claim as (
        select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000013', null) response
    ), renewed_claim as (
        select public.claim_sale_for_payment(
            '51000000-0000-0000-0000-000000000013',
            (first_claim.response ->> 'claim_token')::pg_catalog.uuid
        ) response from first_claim
    ) select
        (first_claim.response ->> 'claim_token') = (renewed_claim.response ->> 'claim_token')
        and (renewed_claim.response ->> 'renewed')::pg_catalog.bool
      from first_claim cross join renewed_claim),
    'owner can renew the same claim token using server time'
);
reset role;
select extensions.is((select renewal_count from public.sale_payment_claims where sale_id = '51000000-0000-0000-0000-000000000013'), 1, 'claim renewal is audited');

set local role authenticated;
set local "request.jwt.claim.sub" = '21000000-0000-0000-0000-000000000002';
select extensions.throws_ok(
    $$with claimed as (select public.claim_sale_for_payment('51000000-0000-0000-0000-000000000014', null) response)
      select public.confirm_sale_payment('51000000-0000-0000-0000-000000000014', (response ->> 'claim_token')::uuid,
      '71000000-0000-0000-0000-000000000014', 'CARD', null, '4111 1111 1111 1111') from claimed$$,
    'P0001', 'PAYMENT_DATA_INVALID', 'CARD reference resembling PAN is rejected'
);
reset role;

select extensions.ok(
    exists (select 1 from pg_catalog.pg_index where indexrelid = 'public.sale_payment_claims_one_open_per_sale_idx'::pg_catalog.regclass and indisunique)
    and exists (select 1 from pg_catalog.pg_constraint where conrelid = 'public.sale_payments'::pg_catalog.regclass and contype = 'u'),
    'database constraints backstop one open claim and one payment per sale'
);

select extensions.ok(
    (select pg_catalog.count(*) = 3 and pg_catalog.bool_and(
        p.prosecdef and pg_catalog.pg_get_functiondef(p.oid) like '%SET search_path TO ''''%'
     )
     from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname in ('claim_sale_for_payment', 'release_sale_payment_claim', 'confirm_sale_payment')),
    'all payment RPCs are SECURITY DEFINER with empty search_path'
);

select * from extensions.finish();
rollback;
