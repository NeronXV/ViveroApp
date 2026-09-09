begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(32);

select extensions.has_table('public', 'suppliers', 'suppliers table exists');
select extensions.has_table('public', 'supplier_presentations', 'supplier presentations table exists');
select extensions.has_table('public', 'supplier_purchase_documents', 'supplier purchase documents table exists');
select extensions.has_table('public', 'supplier_purchase_items', 'supplier purchase items table exists');
select extensions.has_table('public', 'supplier_product_aliases', 'supplier product aliases table exists');

select extensions.ok(
    (
        select pg_catalog.bool_and(c.relrowsecurity)
        from pg_catalog.pg_class c
        where c.oid in (
            'public.suppliers'::pg_catalog.regclass,
            'public.supplier_presentations'::pg_catalog.regclass,
            'public.supplier_purchase_documents'::pg_catalog.regclass,
            'public.supplier_purchase_items'::pg_catalog.regclass,
            'public.supplier_product_aliases'::pg_catalog.regclass
        )
    )
    and not pg_catalog.has_table_privilege('authenticated', 'public.suppliers', 'SELECT')
    and not pg_catalog.has_table_privilege('authenticated', 'public.supplier_purchase_documents', 'SELECT'),
    'supplier purchase tables use RLS without direct client access'
);

select extensions.ok(
    (
        select pg_catalog.count(*) = 7
        from pg_catalog.pg_proc p
        where p.oid in (
            'public.upsert_supplier(text,text,uuid)'::pg_catalog.regprocedure,
            'public.create_supplier_purchase_draft(uuid,date,text,text,bigint,text,jsonb,uuid)'::pg_catalog.regprocedure,
            'public.get_my_supplier_purchases(text,integer)'::pg_catalog.regprocedure,
            'public.get_supplier_purchase(uuid)'::pg_catalog.regprocedure,
            'public.set_supplier_presentation(uuid,text,text,numeric,text,text)'::pg_catalog.regprocedure,
            'public.resolve_supplier_purchase_item(uuid,text,uuid,text,text)'::pg_catalog.regprocedure,
            'public.confirm_supplier_purchase(uuid,uuid)'::pg_catalog.regprocedure
        ) and p.prosecdef and p.proconfig = array['search_path=""']::pg_catalog.text[]
    ),
    'all supplier purchase RPCs are hardened security definers'
);

select extensions.ok(
    pg_catalog.has_function_privilege('authenticated', 'public.upsert_supplier(text,text,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.create_supplier_purchase_draft(uuid,date,text,text,bigint,text,jsonb,uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.get_my_supplier_purchases(text,integer)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.get_supplier_purchase(uuid)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.set_supplier_presentation(uuid,text,text,numeric,text,text)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.resolve_supplier_purchase_item(uuid,text,uuid,text,text)', 'EXECUTE')
    and pg_catalog.has_function_privilege('authenticated', 'public.confirm_supplier_purchase(uuid,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('anon', 'public.upsert_supplier(text,text,uuid)', 'EXECUTE')
    and not pg_catalog.has_function_privilege('service_role', 'public.confirm_supplier_purchase(uuid,uuid)', 'EXECUTE'),
    'supplier purchase RPCs expose only authenticated execution'
);

select extensions.throws_ok(
    $$select public.upsert_supplier('TEST', 'Proveedor prueba')$$,
    '42501', 'SUPPLIER_MANAGEMENT_UNAUTHORIZED',
    'unauthenticated supplier management is rejected'
);

select extensions.throws_ok(
    $$select public.create_supplier_purchase_draft(
        null, current_date, null, 'CREDIT', 0, null, '[]'::jsonb,
        '92000000-0000-4000-8000-000000000001'
    )$$,
    '42501', 'PURCHASE_MANAGEMENT_UNAUTHORIZED',
    'unauthenticated purchase creation is rejected'
);

insert into public.branches(id, code, name) values
    ('92000000-0000-4000-8000-000000000011', 'PUR-A', 'Compras prueba A'),
    ('92000000-0000-4000-8000-000000000012', 'PUR-B', 'Compras prueba B');

insert into auth.users(
    id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at
) values
    (
        '92000000-0000-4000-8000-000000000021',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'purchase-a@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    ),
    (
        '92000000-0000-4000-8000-000000000022',
        '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'purchase-b@example.test', '',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    );

update public.profiles set branch_id = '92000000-0000-4000-8000-000000000011'
where id = '92000000-0000-4000-8000-000000000021';
update public.profiles set branch_id = '92000000-0000-4000-8000-000000000012'
where id = '92000000-0000-4000-8000-000000000022';

insert into public.user_roles(user_id, role_id)
select actor.id, r.id
from (values
    ('92000000-0000-4000-8000-000000000021'::pg_catalog.uuid),
    ('92000000-0000-4000-8000-000000000022'::pg_catalog.uuid)
) actor(id)
cross join lateral (select id from public.roles where name = 'INVENTORY') r;

insert into public.categories(id, name) values
    ('92000000-0000-4000-8000-000000000031', 'Compra piloto');

insert into public.products(
    id, internal_code, common_name, category_id, price_cents, unit
) values
    (
        '92000000-0000-4000-8000-000000000041', 'PUR-CLOR-M06',
        'Clorofito M06', '92000000-0000-4000-8000-000000000031', 3000, 'pieza'
    ),
    (
        '92000000-0000-4000-8000-000000000042', 'PUR-CLOR-M10',
        'Clorofito M10', '92000000-0000-4000-8000-000000000031', 5000, 'pieza'
    );

set local role authenticated;
set local "request.jwt.claim.sub" = '92000000-0000-4000-8000-000000000021';
set local "request.jwt.claims" = '{"sub":"92000000-0000-4000-8000-000000000021","role":"authenticated"}';

select extensions.is(
    public.upsert_supplier(
        'getsemani-test', 'Proveedor sintético',
        '92000000-0000-4000-8000-000000000051'
    ) #>> '{supplier,code}',
    'GETSEMANI-TEST',
    'authorized inventory staff can create a normalized supplier'
);

select extensions.throws_ok(
    $$select public.upsert_supplier('?', 'X')$$,
    '22023', 'SUPPLIER_INPUT_INVALID',
    'invalid supplier input is rejected'
);

select extensions.throws_ok(
    $$select public.create_supplier_purchase_draft(
        '92000000-0000-4000-8000-000000000051',
        '2026-08-13', null, 'CREDIT', 1, 'pilot.pdf',
        '[{"lineNumber":1,"rawDescription":"CLOROFITO","containerCode":"M06","quantity":2,"unitCostCents":1000}]'::jsonb,
        '92000000-0000-4000-8000-000000000061'
    )$$,
    '22023', 'PURCHASE_TOTAL_MISMATCH',
    'purchase total is recalculated and mismatches are rejected'
);

select extensions.ok(
    public.create_supplier_purchase_draft(
        '92000000-0000-4000-8000-000000000051',
        '2026-08-13', null, 'CREDIT', 6500, 'pilot.pdf',
        '[
          {"lineNumber":1,"rawDescription":"CLOROFITO","containerCode":"M06","suggestedCommonName":"Clorofito","suggestedPresentation":"M06 por confirmar","quantity":2,"unitCostCents":1000},
          {"lineNumber":2,"rawDescription":"CLOROFITO","containerCode":"M10","suggestedCommonName":"Clorofito","suggestedPresentation":"M10 por confirmar","quantity":3,"unitCostCents":1500}
        ]'::jsonb,
        '92000000-0000-4000-8000-000000000062'
    ) #>> '{idempotentReplay}' = 'false',
    'a valid supplier purchase is created as a new draft'
);

reset role;

select extensions.is(
    (
        select d.status from public.supplier_purchase_documents d
        where d.idempotency_key = '92000000-0000-4000-8000-000000000062'
    ),
    'DRAFT',
    'a new purchase remains a draft'
);

select extensions.is(
    (select pg_catalog.count(*) from public.inventory_movements m
     where m.notes like 'Compra de proveedor%'),
    0::pg_catalog.int8,
    'creating a draft does not affect inventory'
);

select extensions.is(
    (select pg_catalog.count(*) from public.supplier_presentations p
     where p.supplier_id = '92000000-0000-4000-8000-000000000051'),
    2::pg_catalog.int8,
    'draft import learns distinct supplier presentation codes'
);

set local role authenticated;

do $test$
begin
    perform public.set_supplier_presentation(
        '92000000-0000-4000-8000-000000000051',
        'M06', 'Maceta 6 cm', 6, 'cm', 'Medida sintética de prueba'
    );
end;
$test$;

select extensions.ok(
    public.get_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
    ) #>> '{purchase,items,0,supplierPresentation,displayName}' = 'Maceta 6 cm'
    and (
        public.get_supplier_purchase(
            (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
        ) #>> '{purchase,items,0,supplierPresentation,nominalSize}'
    )::pg_catalog.numeric = 6,
    'supplier presentation codes can be enriched with a confirmed measurement'
);

select extensions.ok(
    public.create_supplier_purchase_draft(
        '92000000-0000-4000-8000-000000000051',
        '2026-08-13', null, 'CREDIT', 6500, 'pilot.pdf',
        '[
          {"lineNumber":1,"rawDescription":"CLOROFITO","containerCode":"M06","suggestedCommonName":"Clorofito","suggestedPresentation":"M06 por confirmar","quantity":2,"unitCostCents":1000},
          {"lineNumber":2,"rawDescription":"CLOROFITO","containerCode":"M10","suggestedCommonName":"Clorofito","suggestedPresentation":"M10 por confirmar","quantity":3,"unitCostCents":1500}
        ]'::jsonb,
        '92000000-0000-4000-8000-000000000062'
    ) #>> '{idempotentReplay}' = 'true',
    'repeating the same draft request is idempotent'
);

select extensions.throws_ok(
    $$select public.create_supplier_purchase_draft(
        '92000000-0000-4000-8000-000000000051',
        '2026-08-13', null, 'CREDIT', 2000, 'pilot.pdf',
        '[{"lineNumber":1,"rawDescription":"OTRO","containerCode":"M06","quantity":2,"unitCostCents":1000}]'::jsonb,
        '92000000-0000-4000-8000-000000000062'
    )$$,
    'P0001', 'PURCHASE_IDEMPOTENCY_CONFLICT',
    'reusing a draft key with another request is rejected'
);

select extensions.ok(
    pg_catalog.jsonb_array_length(public.get_my_supplier_purchases('DRAFT', 10) -> 'items') = 1
    and public.get_my_supplier_purchases('DRAFT', 10) ->> 'branchId'
        = '92000000-0000-4000-8000-000000000011',
    'purchase queue is limited to the actor branch'
);

select extensions.ok(
    public.get_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
    ) #>> '{purchase,items,0,unitCostCents}' = '1000'
    and public.get_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
    ) #>> '{purchase,items,1,lineTotalCents}' = '4500',
    'purchase detail preserves supplier costs and calculated line totals'
);

select extensions.throws_ok(
    $$select public.confirm_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::uuid,
        '92000000-0000-4000-8000-000000000071'
    )$$,
    'P0001', 'PURCHASE_NOT_READY',
    'a purchase with unmatched lines cannot affect inventory'
);

select extensions.is(
    public.resolve_supplier_purchase_item(
        (public.get_supplier_purchase(
            (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
        ) #>> '{purchase,items,0,id}')::pg_catalog.uuid,
        'MATCHED', '92000000-0000-4000-8000-000000000041',
        'Clorofito', 'M06 por confirmar'
    ) #>> '{resolutionStatus}',
    'MATCHED',
    'the first purchase line can be matched to an active product'
);

select extensions.is(
    public.resolve_supplier_purchase_item(
        (public.get_supplier_purchase(
            (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
        ) #>> '{purchase,items,1,id}')::pg_catalog.uuid,
        'MATCHED', '92000000-0000-4000-8000-000000000042',
        'Clorofito', 'M10 por confirmar'
    ) #>> '{resolutionStatus}',
    'MATCHED',
    'the second purchase line can be matched to another presentation'
);

reset role;

select extensions.is(
    (select pg_catalog.count(*) from public.supplier_product_aliases a
     where a.supplier_id = '92000000-0000-4000-8000-000000000051'),
    2::pg_catalog.int8,
    'manual product matches teach supplier aliases'
);

set local role authenticated;

select extensions.ok(
    confirmation.result #>> '{status}' = 'RECEIVED'
    and confirmation.result #>> '{receivedQuantity}' = '5',
    'a reviewed purchase is received atomically'
)
from (
    select public.confirm_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid,
        '92000000-0000-4000-8000-000000000071'
    ) as result
) confirmation;

reset role;

select extensions.ok(
    (select b.total_quantity from public.inventory_balances b
     where b.branch_id = '92000000-0000-4000-8000-000000000011'
       and b.product_id = '92000000-0000-4000-8000-000000000041') = 2
    and (select b.total_quantity from public.inventory_balances b
     where b.branch_id = '92000000-0000-4000-8000-000000000011'
       and b.product_id = '92000000-0000-4000-8000-000000000042') = 3,
    'purchase confirmation creates the exact inventory balances'
);

set local role authenticated;

select extensions.ok(
    public.confirm_supplier_purchase(
        (public.get_my_supplier_purchases('RECEIVED', 10) #>> '{items,0,id}')::pg_catalog.uuid,
        '92000000-0000-4000-8000-000000000071'
    ) #>> '{idempotentReplay}' = 'true',
    'repeating purchase confirmation does not duplicate inventory'
);

select extensions.throws_ok(
    $$select public.confirm_supplier_purchase(
        (public.get_my_supplier_purchases('RECEIVED', 10) #>> '{items,0,id}')::uuid,
        '92000000-0000-4000-8000-000000000072'
    )$$,
    'P0001', 'PURCHASE_CONFIRMATION_CONFLICT',
    'a received purchase rejects another confirmation key'
);

do $test$
begin
    perform public.create_supplier_purchase_draft(
        '92000000-0000-4000-8000-000000000051',
        '2026-08-20', null, 'CASH', 6500, 'pilot-2.pdf',
        '[
          {"lineNumber":1,"rawDescription":"CLOROFITO","containerCode":"M06","quantity":2,"unitCostCents":1000},
          {"lineNumber":2,"rawDescription":"CLOROFITO","containerCode":"M10","quantity":3,"unitCostCents":1500}
        ]'::jsonb,
        '92000000-0000-4000-8000-000000000063'
    );
end;
$test$;

select extensions.ok(
    public.get_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
    ) #>> '{purchase,items,0,resolutionStatus}' = 'AUTO_MATCHED'
    and public.get_supplier_purchase(
        (public.get_my_supplier_purchases('DRAFT', 10) #>> '{items,0,id}')::pg_catalog.uuid
    ) #>> '{purchase,items,1,resolutionStatus}' = 'AUTO_MATCHED',
    'future supplier descriptions are matched automatically'
);

do $test$
begin
    perform pg_catalog.set_config(
        'test.first_purchase_id',
        public.get_my_supplier_purchases('RECEIVED', 10) #>> '{items,0,id}',
        true
    );
end;
$test$;

set local "request.jwt.claim.sub" = '92000000-0000-4000-8000-000000000022';
set local "request.jwt.claims" = '{"sub":"92000000-0000-4000-8000-000000000022","role":"authenticated"}';

select extensions.throws_ok(
    $$select public.get_supplier_purchase(
        pg_catalog.current_setting('test.first_purchase_id')::uuid
    )$$,
    '22023', 'PURCHASE_NOT_FOUND',
    'another branch cannot read the supplier purchase'
);

reset role;

select * from extensions.finish();

rollback;
