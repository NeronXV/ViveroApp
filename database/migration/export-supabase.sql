-- Prepared read-only export; execute only after explicitly confirming source and permission.
-- Contains personal/customer data. Save privately outside Git; never paste its result in logs.
-- No passwords, sessions or live claim/newsletter tokens are exported.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
-- Fail rather than silently exporting an RLS-filtered subset; grants are unchanged.
SET LOCAL row_security = off;
SET LOCAL TIME ZONE 'UTC';
SELECT jsonb_build_object(
  'schema_version', 1,
  'authority', 'supabase-export',
  'exported_at', to_char(transaction_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
  'tables', jsonb_build_object(
  'auth_users', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'email', email) ORDER BY id), '[]'::jsonb) FROM auth.users),
  'roles', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.roles t),
  'permissions', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.permissions t),
  'role_permissions', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.role_permissions t),
  'branches', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.branches t),
  'profiles', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.profiles t),
  'user_roles', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.user_roles t),
  'categories', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.categories t),
  'products', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.products t),
  'product_images', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.product_images t),
  'sales', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sales t),
  'sale_items', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sale_items t),
  'sale_status_history', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sale_status_history t),
  'sale_payment_claims', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t) - 'claim_token') AS fields(k,v))), '[]'::jsonb) FROM public.sale_payment_claims t),
  'sale_payments', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sale_payments t),
  'inventory_locations', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.inventory_locations t),
  'inventory_movements', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.inventory_movements t),
  'inventory_balances', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.inventory_balances t),
  'customers', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.customers t),
  'promotions', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.promotions t),
  'sale_discounts', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sale_discounts t),
  'inventory_counts', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.inventory_counts t),
  'promotion_products', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.promotion_products t),
  'web_orders', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.web_orders t),
  'web_order_items', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.web_order_items t),
  'web_order_status_history', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.web_order_status_history t),
  'suppliers', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.suppliers t),
  'supplier_presentations', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.supplier_presentations t),
  'supplier_purchase_documents', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.supplier_purchase_documents t),
  'supplier_purchase_items', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.supplier_purchase_items t),
  'supplier_product_aliases', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.supplier_product_aliases t),
  'branch_inventory_activation', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.branch_inventory_activation t),
  'sale_refunds', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.sale_refunds t),
  'cashier_closings', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.cashier_closings t),
  'cashier_closing_payments', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.cashier_closing_payments t),
  'cashier_closing_refunds', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.cashier_closing_refunds t),
  'newsletter_subscribers', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t) - 'confirmation_token' - 'unsubscribe_token') AS fields(k,v))), '[]'::jsonb) FROM public.newsletter_subscribers t),
  'newsletter_campaigns', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.newsletter_campaigns t),
  'newsletter_deliveries', (SELECT COALESCE(jsonb_agg((SELECT jsonb_object_agg(k, CASE WHEN k LIKE '%\_cents' ESCAPE '\' AND v <> 'null'::jsonb THEN to_jsonb(v #>> '{}') ELSE v END) FROM jsonb_each(to_jsonb(t)) AS fields(k,v))), '[]'::jsonb) FROM public.newsletter_deliveries t)
  ),
  'primary_keys', (SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'table', c.relname, 'columns', (SELECT jsonb_agg(a.attname ORDER BY k.ordinality) FROM unnest(con.conkey) WITH ORDINALITY k(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=k.attnum))), '[]'::jsonb)
    FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid JOIN pg_namespace ns ON ns.oid=c.relnamespace WHERE con.contype='p' AND ns.nspname='public'),
  'foreign_keys', (SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'table', c.relname,
    'columns', (SELECT jsonb_agg(a.attname ORDER BY k.ordinality) FROM unnest(con.conkey) WITH ORDINALITY k(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=k.attnum),
    'referenced_table', CASE WHEN rns.nspname='auth' AND rc.relname='users' THEN 'auth_users' ELSE rc.relname END,
    'referenced_columns', (SELECT jsonb_agg(a.attname ORDER BY k.ordinality) FROM unnest(con.confkey) WITH ORDINALITY k(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=rc.oid AND a.attnum=k.attnum))), '[]'::jsonb)
    FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid JOIN pg_namespace ns ON ns.oid=c.relnamespace JOIN pg_class rc ON rc.oid=con.confrelid JOIN pg_namespace rns ON rns.oid=rc.relnamespace WHERE con.contype='f' AND ns.nspname='public')
) AS migration_snapshot;
COMMIT;
