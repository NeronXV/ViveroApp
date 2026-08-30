begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(23);

select extensions.has_function(
    'public', 'record_inventory_movement',
    array['uuid', 'inventory_movement_type', 'numeric', 'uuid', 'uuid', 'text', 'uuid'],
    'inventory movement function exists with the canonical signature'
);
select extensions.has_function(
    'public', 'get_admin_inventory_balances',
    array['uuid', 'integer', 'uuid', 'text', 'boolean'],
    'inventory balance presentation function exists'
);
select extensions.has_function(
    'public', 'get_low_inventory_alerts', array['uuid'],
    'low inventory alert function exists'
);
select extensions.has_function(
    'public', 'search_customers', array['text', 'integer'],
    'customer search function exists'
);
select extensions.has_function(
    'public', 'apply_sale_discount', array['uuid', 'bigint', 'text', 'uuid'],
    'discount application function exists'
);
select extensions.has_function(
    'public', 'get_report_daily_sales', array['uuid', 'date', 'date'],
    'daily sales report function exists'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'anon',
        'public.get_admin_inventory_balances(uuid,integer,uuid,text,boolean)',
        'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.get_low_inventory_alerts(uuid)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.search_customers(text,integer)', 'EXECUTE'
    )
    and not pg_catalog.has_function_privilege(
        'anon', 'public.apply_sale_discount(uuid,bigint,text,uuid)', 'EXECUTE'
    ),
    'anon cannot execute protected MVP extension functions'
);

select extensions.ok(
    pg_catalog.has_function_privilege(
        'authenticated',
        'public.get_admin_inventory_balances(uuid,integer,uuid,text,boolean)',
        'EXECUTE'
    )
    and pg_catalog.has_function_privilege(
        'authenticated', 'public.search_customers(text,integer)', 'EXECUTE'
    )
    and pg_catalog.has_function_privilege(
        'authenticated', 'public.get_report_daily_sales(uuid,date,date)', 'EXECUTE'
    ),
    'authenticated receives only the client entry points required by the MVP extensions'
);

select extensions.ok(
    not pg_catalog.has_function_privilege(
        'authenticated', 'public.update_inventory_balance()', 'EXECUTE'
    ),
    'authenticated cannot invoke the inventory balance trigger function directly'
);

select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_proc p
        join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and p.prosecdef
          and not (p.proconfig @> array['search_path=""'])
    ),
    'every SECURITY DEFINER function keeps an empty search_path'
);

select extensions.ok(
    (select qual from pg_catalog.pg_policies
     where schemaname = 'public' and tablename = 'inventory_locations'
       and policyname = 'inventory_locations_read') like '%is_active%'
    and (select qual from pg_catalog.pg_policies
         where schemaname = 'public' and tablename = 'inventory_locations'
           and policyname = 'inventory_locations_read') like '%MANAGE_INVENTORY%',
    'inventory location reads require an active authorized profile'
);

select extensions.ok(
    (select qual from pg_catalog.pg_policies
     where schemaname = 'public' and tablename = 'inventory_movements'
       and policyname = 'inventory_movements_read') like '%branch_id%'
    and (select qual from pg_catalog.pg_policies
         where schemaname = 'public' and tablename = 'inventory_movements'
           and policyname = 'inventory_movements_read') like '%VIEW_ALL_SALES%',
    'inventory movement reads enforce branch isolation'
);

select extensions.ok(
    (select qual from pg_catalog.pg_policies
     where schemaname = 'public' and tablename = 'inventory_balances'
       and policyname = 'inventory_balances_read') like '%VIEW_INVENTORY_ALERTS%',
    'inventory balance reads require an inventory or reporting capability'
);

select extensions.ok(
    (select qual from pg_catalog.pg_policies
     where schemaname = 'public' and tablename = 'customers'
       and policyname = 'customers_read') like '%CREATE_SALES%'
    and (select qual from pg_catalog.pg_policies
         where schemaname = 'public' and tablename = 'customers'
           and policyname = 'customers_read') like '%MANAGE_USERS%',
    'customer reads require an operational capability'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.get_admin_inventory_balances(uuid,integer,uuid,text,boolean)'::pg_catalog.regprocedure
    ) like '%INVENTORY_UNAUTHORIZED%'
    and pg_catalog.pg_get_functiondef(
        'public.get_admin_inventory_balances(uuid,integer,uuid,text,boolean)'::pg_catalog.regprocedure
    ) like '%p_limit > 100%',
    'inventory presentation validates authorization and bounds'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.get_low_inventory_alerts(uuid)'::pg_catalog.regprocedure
    ) like '%INVENTORY_BRANCH_FORBIDDEN%'
    and pg_catalog.pg_get_functiondef(
        'public.get_low_inventory_alerts(uuid)'::pg_catalog.regprocedure
    ) like '%VIEW_INVENTORY_ALERTS%',
    'low inventory alerts enforce capability and branch scope'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.record_inventory_movement(uuid,inventory_movement_type,numeric,uuid,uuid,text,uuid)'::pg_catalog.regprocedure
    ) like '%l.branch_id = v_branch_id%'
    and pg_catalog.pg_get_functiondef(
        'public.record_inventory_movement(uuid,inventory_movement_type,numeric,uuid,uuid,text,uuid)'::pg_catalog.regprocedure
    ) like '%l.is_active%',
    'inventory movements reject locations outside the active target branch'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.update_inventory_balance()'::pg_catalog.regprocedure
    ) like '%Inventory cannot become negative%',
    'inventory balance trigger rejects negative stock'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.search_customers(text,integer)'::pg_catalog.regprocedure
    ) like '%Customer search is not allowed%'
    and pg_catalog.pg_get_functiondef(
        'public.search_customers(text,integer)'::pg_catalog.regprocedure
    ) like '%p_limit > 50%',
    'customer search validates capability and input bounds'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.upsert_customer(uuid,text,text,text,boolean)'::pg_catalog.regprocedure
    ) like '%Only user managers can update customers%',
    'sales personnel cannot overwrite arbitrary customer records'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.apply_sale_discount(uuid,bigint,text,uuid)'::pg_catalog.regprocedure
    ) like '%Promotion is not applicable%'
    and pg_catalog.pg_get_functiondef(
        'public.apply_sale_discount(uuid,bigint,text,uuid)'::pg_catalog.regprocedure
    ) like '%Discount does not match promotion rules%',
    'promotion-backed discounts are recalculated and validated by PostgreSQL'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.get_report_daily_sales(uuid,date,date)'::pg_catalog.regprocedure
    ) like '%public.sale_payments%'
    and pg_catalog.pg_get_functiondef(
        'public.get_report_daily_sales(uuid,date,date)'::pg_catalog.regprocedure
    ) like '%pay.created_at%',
    'daily sales reports use the canonical payment timestamp'
);

select extensions.ok(
    pg_catalog.pg_get_functiondef(
        'public.get_report_daily_sales(uuid,date,date)'::pg_catalog.regprocedure
    ) like '%Report date range is invalid%',
    'daily sales reports reject inverted date ranges'
);

select * from extensions.finish();

rollback;
