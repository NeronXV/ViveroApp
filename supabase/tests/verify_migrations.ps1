$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$migrationDirectory = Join-Path $projectRoot 'supabase\migrations'
$migrationFiles = @(Get-ChildItem $migrationDirectory -Filter '*.sql' | Sort-Object Name)
$allSql = ($migrationFiles | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
$authSql = Get-Content (Join-Path $migrationDirectory '202608080001_auth_roles.sql') -Raw
$salesSql = Get-Content (Join-Path $migrationDirectory '202608080003_sales_cart.sql') -Raw
$branchSql = Get-Content (Join-Path $migrationDirectory '202608140001_branch_management.sql') -Raw
$privilegeSql = Get-Content (Join-Path $migrationDirectory '202608150001_harden_table_privileges.sql') -Raw
$functionPrivilegeSql = Get-Content (Join-Path $migrationDirectory '202608150002_harden_function_privileges.sql') -Raw
$paymentSql = Get-Content (Join-Path $migrationDirectory '202608220001_cashier_payments.sql') -Raw
$accessContextSql = Get-Content (Join-Path $migrationDirectory '202608220002_get_my_access_context.sql') -Raw
$publicCatalogSql = Get-Content (Join-Path $migrationDirectory '202608240001_get_public_catalog.sql') -Raw
$catalogImagesSql = Get-Content (
    Join-Path $migrationDirectory '202608270001_public_catalog_images.sql'
) -Raw
$cashierWebSql = Get-Content (
    Join-Path $migrationDirectory '202608280001_cashier_web_contract.sql'
) -Raw
$adminWebSql = Get-Content (
    Join-Path $migrationDirectory '202608290001_admin_web_contract.sql'
) -Raw
$paymentTestSql = Get-Content (
    Join-Path $projectRoot 'supabase\tests\database\cashier_payments.test.sql'
) -Raw
$publicRpcTestSql = Get-Content (
    Join-Path $projectRoot 'supabase\tests\database\public_rpcs.test.sql'
) -Raw
$catalogImagesTestSql = Get-Content (
    Join-Path $projectRoot 'supabase\tests\database\public_catalog_images.test.sql'
) -Raw
$cashierWebTestSql = Get-Content (
    Join-Path $projectRoot 'supabase\tests\database\cashier_web_contract.test.sql'
) -Raw
$adminWebTestSql = Get-Content (
    Join-Path $projectRoot 'supabase\tests\database\admin_web_contract.test.sql'
) -Raw
$appPermissions = Get-Content (
    Join-Path $projectRoot 'app\src\main\java\com\intutec\viveroapp\core\security\AppPermission.kt'
) -Raw

$checks = [System.Collections.Generic.List[string]]::new()

function Assert-Condition {
    param(
        [Parameter(Mandatory)] [bool] $Condition,
        [Parameter(Mandatory)] [string] $Description
    )

    if (-not $Condition) {
        throw "FAILED: $Description"
    }
    $checks.Add($Description)
}

Assert-Condition ($migrationFiles.Count -eq 12) 'exactly twelve ordered migration files exist'
Assert-Condition (
    (($migrationFiles.Name -join ',') -eq (
        '202608080001_auth_roles.sql,202608080002_catalog.sql,202608080003_sales_cart.sql,' +
        '202608140001_branch_management.sql,202608150001_harden_table_privileges.sql,' +
        '202608150002_harden_function_privileges.sql,' +
        '202608220001_cashier_payments.sql,202608220002_get_my_access_context.sql,' +
        '202608240001_get_public_catalog.sql,' +
        '202608270001_public_catalog_images.sql,' +
        '202608280001_cashier_web_contract.sql,' +
        '202608290001_admin_web_contract.sql'
    ))
) 'migration filenames preserve the required execution order'

$destructivePattern = '(?im)^\s*(drop\s+(table|schema|type)|truncate\s|delete\s+from|alter\s+table.+drop\s)'
Assert-Condition (-not [regex]::IsMatch($allSql, $destructivePattern)) 'migrations contain no destructive statements'

$securityDefinerCount = [regex]::Matches($allSql, '(?i)security\s+definer').Count
$secureSearchPathCount = [regex]::Matches(
    $allSql,
    "(?is)security\s+definer\s+set\s+search_path\s*=\s*''"
).Count
Assert-Condition (
    $securityDefinerCount -eq 20 -and $secureSearchPathCount -eq $securityDefinerCount
) 'every SECURITY DEFINER function uses an empty search_path'

Assert-Condition (
    $authSql.Contains("name in ('SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER')")
) 'the database declares the exact six approved roles'
Assert-Condition (
    $appPermissions.Contains('UserRole.SALES') -and -not $appPermissions.Contains('UserRole.WORKER')
) 'Android uses SALES and contains no legacy WORKER mapping'

$permissionSeed = $authSql.Substring(
    $authSql.IndexOf('insert into public.permissions'),
    $authSql.IndexOf('insert into public.role_permissions') - $authSql.IndexOf('insert into public.permissions')
)
$databaseCapabilityNames = @(
    [regex]::Matches($permissionSeed, "\('([A-Z][A-Z0-9_]+)'\s*,") |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object
)
$appEnumBlock = [regex]::Match(
    $appPermissions,
    '(?s)enum class AppPermission \{(?<body>.*?)\n\}'
).Groups['body'].Value
$androidCapabilityNames = @(
    [regex]::Matches($appEnumBlock, '(?m)^\s*([A-Z][A-Z0-9_]+),\s*$') |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object
)
Assert-Condition (
    (($databaseCapabilityNames -join ',') -eq ($androidCapabilityNames -join ','))
) 'Android and Supabase declare the same platform-independent capabilities'
Assert-Condition (
    $authSql.Contains('grant update (full_name, avatar_path) on public.profiles to authenticated;') -and
    -not $authSql.Contains('grant select, update on public.profiles to authenticated;')
) 'profile updates are restricted to full_name and avatar_path'
Assert-Condition (
    -not [regex]::IsMatch($authSql, '(?i)grant\s+(insert|update|delete).+public\.user_roles.+authenticated')
) 'authenticated has no direct role mutation grant'
Assert-Condition (
    $authSql.Contains("v_actor_role = 'ADMIN'") -and
    $authSql.Contains("p_role_name = 'OWNER'") -and
    $authSql.Contains("v_target_role = 'OWNER'")
) 'ADMIN cannot grant or modify OWNER through assign_user_role'
Assert-Condition (
    $authSql.Contains('grant execute on function public.bootstrap_first_owner') -and
    $authSql.Contains('to service_role;')
) 'first OWNER bootstrap is restricted to the administrative service role'
Assert-Condition (
    $salesSql.Contains("status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')") -and
    $salesSql.Contains("public.has_permission('OPERATE_CASHIER')")
) 'cashier queue is restricted by state and capability'
Assert-Condition (
    $salesSql.Contains('v_sale.created_by <> v_user_id') -and
    $salesSql.Contains('Idempotency key is unavailable')
) 'idempotency collisions verify ownership without exposing sale data'
Assert-Condition (
    $salesSql.Contains('v_valid_product_count <> v_item_count') -and
    $salesSql.Contains('v_distinct_item_count <> v_item_count')
) 'ticket validation requires every distinct item to be valid'
Assert-Condition (
    [regex]::Matches($branchSql, "public\.has_permission\('MANAGE_BRANCHES'\)").Count -eq 3 -and
    $branchSql.Contains("public.has_permission('MANAGE_USERS')")
) 'branch RPCs enforce their platform-independent capabilities'
Assert-Condition (
    $branchSql.Contains("v_actor_role = 'ADMIN' and v_target_role = 'OWNER'")
) 'ADMIN cannot change an OWNER branch assignment'
Assert-Condition (
    $branchSql.Contains("s.status in ('SENT_TO_CASHIER', 'PAYMENT_PENDING')") -and
    $branchSql.Contains('p.branch_id = p_branch_id and p.is_active')
) 'branch deactivation checks active personnel and pending sales'
Assert-Condition (
    [regex]::Matches($branchSql, '(?i)revoke all on function public\.(create_branch|update_branch|set_branch_active|assign_user_branch).* from public;').Count -eq 4 -and
    [regex]::Matches($branchSql, '(?i)revoke all on function public\.(create_branch|update_branch|set_branch_active|assign_user_branch).* from anon;').Count -eq 4 -and
    [regex]::Matches($branchSql, '(?i)grant execute on function public\.(create_branch|update_branch|set_branch_active|assign_user_branch).* to authenticated;').Count -eq 4
) 'branch RPC execution is revoked from PUBLIC and anon and granted to authenticated'
Assert-Condition (
    -not [regex]::IsMatch($branchSql, '(?i)grant\s+(insert|update|delete).+public\.(branches|profiles).+authenticated')
) 'branch management adds no direct authenticated table writes'
Assert-Condition (
    $privilegeSql.Contains('from anon, authenticated, public;') -and
    $privilegeSql.Contains('public.branches,') -and
    $privilegeSql.Contains('public.user_roles')
) 'client and PUBLIC privileges are reset across all twelve application tables'
Assert-Condition (
    $privilegeSql.IndexOf('revoke all privileges on table') -lt
    $privilegeSql.IndexOf('grant update (full_name, avatar_path)')
) 'general table privileges are revoked before profile column updates are granted'
Assert-Condition (
    $privilegeSql.Contains('grant select on table') -and
    $privilegeSql.Contains('to authenticated;')
) 'authenticated retains the table reads required by RLS'
Assert-Condition (
    $privilegeSql.Contains('grant insert, update, delete on table') -and
    $privilegeSql.Contains('public.categories,') -and
    $privilegeSql.Contains('public.product_images,') -and
    $privilegeSql.Contains('public.products')
) 'authenticated retains catalog administration privileges governed by RLS'
Assert-Condition (
    -not $privilegeSql.Contains('service_role') -and
    -not [regex]::IsMatch($privilegeSql, '(?i)alter\s+(role|default\s+privileges)|owner\s+to')
) 'service_role, system roles, ownership, and default privileges remain untouched'
Assert-Condition (
    [regex]::Matches(
        $functionPrivilegeSql,
        '(?is)revoke all on function public\.[a-z_]+\s*\([^;]*?\)\s*from public, anon, authenticated;'
    ).Count -eq 11
) 'all eleven public function signatures revoke client and PUBLIC execution'
Assert-Condition (
    [regex]::Matches(
        $functionPrivilegeSql,
        '(?is)grant execute on function public\.(has_permission|submit_sale_to_cashier|assign_user_role|create_branch|update_branch|set_branch_active|assign_user_branch)\s*\([^;]*?\)\s*to authenticated;'
    ).Count -eq 7
) 'authenticated receives exactly the seven approved public functions'
Assert-Condition (
    $functionPrivilegeSql.Contains(
        'grant execute on function public.bootstrap_first_owner(pg_catalog.uuid)'
    ) -and $functionPrivilegeSql.Contains('to service_role;')
) 'bootstrap_first_owner remains explicitly reserved for service_role'
Assert-Condition (
    -not [regex]::IsMatch(
        $functionPrivilegeSql,
        '(?is)grant execute on function public\.(bootstrap_first_owner|handle_new_user|set_updated_at|enforce_product_price_permission).*to authenticated;'
    )
) 'administrative and trigger functions are not granted to authenticated'
Assert-Condition (
    -not [regex]::IsMatch($functionPrivilegeSql, '(?i)create\s+(or\s+replace\s+)?function|alter\s+default\s+privileges|owner\s+to')
) 'function hardening changes no bodies, owners, or global defaults'

Assert-Condition (
    $paymentSql.Contains("create type public.payment_method as enum ('CASH', 'CARD', 'TRANSFER');") -and
    $paymentSql.Contains('create table public.sale_payments') -and
    $paymentSql.Contains('create table public.sale_payment_claims')
) 'cashier payments use the approved methods and separate auditable claim table'
Assert-Condition (
    $paymentSql.Contains('sale_id pg_catalog.uuid not null unique references public.sales') -and
    $paymentSql.Contains('idempotency_key pg_catalog.uuid not null unique') -and
    $paymentSql.Contains('sale_payment_claims_one_open_per_sale_idx')
) 'database constraints enforce one payment and one open claim per sale'
Assert-Condition (
    [regex]::Matches($paymentSql, '(?is)from\s+public\.sales\s+s.*?for\s+update').Count -eq 3 -and
    $paymentSql.Contains("set status = 'PAID'") -and
    $paymentSql.Contains("'SENT_TO_CASHIER', 'PAID'")
) 'claim, release, and confirmation serialize on the sale while confirmation records PAID atomically'
Assert-Condition (
    $paymentSql.Contains('v_payment.requested_amount_received_cents is distinct from p_amount_received_cents') -and
    $paymentSql.Contains("message = 'IDEMPOTENCY_CONFLICT'") -and
    $paymentSql.Contains("message = 'SALE_ALREADY_PAID'")
) 'payment retries compare the canonical request and reject conflicting keys or second payments'
Assert-Condition (
    $paymentSql.Contains('pg_catalog.clock_timestamp()') -and
    $paymentSql.Contains('pg_catalog.make_interval(mins => 5)') -and
    -not $paymentSql.Contains("set status = 'PAYMENT_PENDING'")
) 'claims use server time without moving sales to PAYMENT_PENDING'
Assert-Condition (
    [regex]::Matches(
        $paymentSql,
        '(?is)revoke all on function public\.(claim_sale_for_payment|release_sale_payment_claim|confirm_sale_payment)\s*\([^;]*?\)\s*from public, anon, authenticated;'
    ).Count -eq 3 -and
    [regex]::Matches(
        $paymentSql,
        '(?is)grant execute on function public\.(claim_sale_for_payment|release_sale_payment_claim|confirm_sale_payment)\s*\([^;]*?\)\s*to authenticated, service_role;'
    ).Count -eq 3
) 'payment RPC execution is limited to authenticated and service_role'
Assert-Condition (
    $paymentSql.Contains('alter table public.sale_payment_claims enable row level security;') -and
    $paymentSql.Contains('alter table public.sale_payments enable row level security;') -and
    $paymentSql.Contains('from public, anon, authenticated;') -and
    -not [regex]::IsMatch($paymentSql, '(?i)grant\s+(select|insert|update|delete).*to\s+authenticated')
) 'payment tables are RLS-enabled and expose no direct client privileges'
Assert-Condition (
    $paymentSql.Contains("public.has_permission('OPERATE_CASHIER')") -and
    [regex]::Matches($paymentSql, 'v_sale\.branch_id\s*<>\s*v_branch_id').Count -eq 3
) 'all payment RPCs require cashier capability and enforce the active session branch'
Assert-Condition (
    $paymentSql.Contains("message = 'CASH_AMOUNT_INSUFFICIENT'") -and
    $paymentSql.Contains("message = 'TRANSFER_REFERENCE_REQUIRED'") -and
    $paymentSql.Contains("message = 'CLAIM_EXPIRED'") -and
    $paymentSql.Contains("message = 'CLAIM_NOT_OWNED'")
) 'payment RPCs expose stable sanitized application error codes'
Assert-Condition (
    $paymentTestSql.Contains('select extensions.plan(51);') -and
    [regex]::Matches(
        $paymentTestSql,
        '(?im)^select\s+extensions\.(ok|is|isnt|lives_ok|throws_ok|results_eq|set_eq|bag_eq|cmp_ok)\s*\('
    ).Count -eq 51
) 'cashier payment pgTAP plan matches its fifty-one assertions'
Assert-Condition (
    $paymentTestSql.Contains('OWNER cannot operate a sale in another branch') -and
    $paymentTestSql.Contains('ADMIN cannot operate a sale in another branch') -and
    $paymentTestSql.Contains('competing confirmations create one payment') -and
    $paymentTestSql.Contains('idempotent retries leave one PAID history row')
) 'payment tests cover branch isolation, competition, and idempotent side effects'

Assert-Condition (
    [regex]::Matches(
        $cashierWebSql,
        '(?is)create or replace function public\.(get_cashier_sales|get_cashier_sale_detail|get_cashier_payment_result)\s*\('
    ).Count -eq 3 -and
    [regex]::Matches($cashierWebSql, "(?is)security\s+definer\s+set\s+search_path\s*=\s*''").Count -eq 3 -and
    [regex]::Matches(
        $cashierWebSql,
        '(?is)alter function public\.(get_cashier_sales|get_cashier_sale_detail|get_cashier_payment_result)\s*\([^;]*?\) owner to postgres;'
    ).Count -eq 3
) 'cashier Web contract defines three postgres-owned secure presentation RPCs'
Assert-Condition (
    [regex]::Matches(
        $cashierWebSql,
        '(?is)alter function public\.(claim_sale_for_payment|release_sale_payment_claim|confirm_sale_payment)\s*\([^;]*?\) owner to postgres;'
    ).Count -eq 3 -and
    -not [regex]::IsMatch(
        $cashierWebSql,
        '(?is)create\s+(or\s+replace\s+)?function\s+public\.(claim_sale_for_payment|release_sale_payment_claim|confirm_sale_payment)'
    )
) 'cashier Web migration pins payment RPC owners without recreating their bodies'
Assert-Condition (
    [regex]::Matches(
        $cashierWebSql,
        '(?is)revoke all on function public\.(get_cashier_sales|get_cashier_sale_detail|get_cashier_payment_result)\s*\([^;]*?\)\s*from public, anon, authenticated, service_role;'
    ).Count -eq 3 -and
    [regex]::Matches(
        $cashierWebSql,
        '(?is)grant execute on function public\.(get_cashier_sales|get_cashier_sale_detail|get_cashier_payment_result)\s*\([^;]*?\)\s*to authenticated;'
    ).Count -eq 3 -and
    -not [regex]::IsMatch(
        $cashierWebSql,
        '(?is)grant execute on function public\.(get_cashier_sales|get_cashier_sale_detail|get_cashier_payment_result)\s*\([^;]*?\)\s*to (anon|service_role|public);'
    )
) 'cashier Web RPC execution is granted exclusively to authenticated'
Assert-Condition (
    $cashierWebSql.Contains("'schemaVersion', 1") -and
    $cashierWebSql.Contains("message = 'CASHIER_UNAUTHORIZED'") -and
    $cashierWebSql.Contains("message = 'SALE_UNAVAILABLE'") -and
    $cashierWebSql.Contains("message = 'CASHIER_PAGE_LIMIT_INVALID'") -and
    $cashierWebSql.Contains("message = 'CASHIER_CURSOR_INVALID'") -and
    [regex]::Matches($cashierWebSql, "public\.has_permission\('OPERATE_CASHIER'\)").Count -eq 3
) 'cashier Web RPCs are versioned and enforce stable authorization and input errors'
Assert-Condition (
    $cashierWebSql.Contains("s.status = 'SENT_TO_CASHIER'") -and
    $cashierWebSql.Contains('order by s.created_at asc, s.id asc') -and
    $cashierWebSql.Contains('p_limit pg_catalog.int4 default 25') -and
    $cashierWebSql.Contains('p_limit < 1 or p_limit > 50') -and
    $cashierWebSql.Contains("'AVAILABLE'") -and
    $cashierWebSql.Contains("'CLAIMED_BY_ME'") -and
    $cashierWebSql.Contains("'CLAIMED_BY_OTHER'") -and
    -not $cashierWebSql.Contains("set status = 'PAYMENT_PENDING'")
) 'cashier queue is FIFO, bounded, claim-aware, and read-only'
Assert-Condition (
    -not [regex]::IsMatch($cashierWebSql, '(?i)create\s+table|alter\s+table|create\s+policy|drop\s+') -and
    -not [regex]::IsMatch(
        $cashierWebSql,
        '(?i)revoke\s+select|revoke\s+all\s+privileges\s+on\s+table'
    ) -and
    -not [regex]::IsMatch(
        $cashierWebSql,
        "(?i)'(customerId|createdById|cashierId|idempotencyKey|internalCode|barcode|wholesalePrice|claimToken|unit)'"
    )
) 'cashier Web migration changes no tables or RLS and exposes no forbidden JSON field names'
Assert-Condition (
    $cashierWebTestSql.Contains('select extensions.plan(60);') -and
    [regex]::Matches(
        $cashierWebTestSql,
        '(?im)^select\s+extensions\.(ok|is|isnt|lives_ok|throws_ok|results_eq|set_eq|bag_eq|cmp_ok)\s*\('
    ).Count -eq 60
) 'cashier Web pgTAP plan matches its sixty assertions'
Assert-Condition (
    $cashierWebTestSql.Contains('queue uses FIFO with UUID as stable tie breaker') -and
    $cashierWebTestSql.Contains('other active claim hides cashier identity') -and
    $cashierWebTestSql.Contains('detail omits PII, internal codes, history, references and claim token') -and
    $cashierWebTestSql.Contains('changing current product unit does not alter historical detail contract') -and
    $cashierWebTestSql.Contains('payment result lines omit unit and preserve captured values') -and
    $cashierWebTestSql.Contains('attempt processed by another cashier remains hidden') -and
    $cashierWebTestSql.Contains('existing payment RPCs have explicit postgres ownership')
) 'cashier Web tests cover FIFO, claim privacy, detail privacy, recovery, and owners'

Assert-Condition (
    [regex]::Matches(
        $adminWebSql,
        '(?is)create or replace function public\.(get_admin_branches|get_admin_staff)\s*\('
    ).Count -eq 2 -and
    [regex]::Matches($adminWebSql, "(?is)security\s+definer\s+set\s+search_path\s*=\s*''").Count -eq 2 -and
    [regex]::Matches(
        $adminWebSql,
        '(?is)alter function public\.(get_admin_branches|get_admin_staff)\s*\([^;]*?\) owner to postgres;'
    ).Count -eq 2
) 'admin Web contract defines two postgres-owned secure presentation RPCs'
Assert-Condition (
    [regex]::Matches(
        $adminWebSql,
        '(?is)revoke all on function public\.(get_admin_branches|get_admin_staff)\s*\([^;]*?\) from public, anon, authenticated, service_role;'
    ).Count -eq 2 -and
    [regex]::Matches(
        $adminWebSql,
        '(?is)grant execute on function public\.(get_admin_branches|get_admin_staff)\s*\([^;]*?\) to authenticated;'
    ).Count -eq 2 -and
    -not [regex]::IsMatch(
        $adminWebSql,
        '(?is)grant execute on function public\.(get_admin_branches|get_admin_staff)\s*\([^;]*?\) to (anon|service_role|public);'
    )
) 'admin Web RPC execution is granted exclusively to authenticated'
Assert-Condition (
    $adminWebSql.Contains("message = 'ADMIN_UNAUTHORIZED'") -and
    $adminWebSql.Contains("message = 'ADMIN_BRANCH_QUERY_INVALID'") -and
    $adminWebSql.Contains("message = 'ADMIN_STAFF_QUERY_INVALID'") -and
    $adminWebSql.Contains("public.has_permission('MANAGE_BRANCHES')") -and
    [regex]::Matches($adminWebSql, "public\.has_permission\('MANAGE_USERS'\)").Count -eq 2
) 'admin Web RPCs enforce management capabilities and stable errors'
Assert-Condition (
    [regex]::Matches($adminWebSql, "'schemaVersion', 1").Count -eq 2 -and
    [regex]::Matches($adminWebSql, 'p_limit < 1 or p_limit > 100').Count -eq 2 -and
    $adminWebSql.Contains('order by b.code collate "C" asc, b.id asc') -and
    $adminWebSql.Contains('order by pg_catalog.lower(staff.full_name) collate "C" asc, staff.id asc') -and
    -not [regex]::IsMatch($adminWebSql, "(?i)'(email|token|claims|encryptedPassword|rawUserMetadata)'")
) 'admin Web projections are versioned, bounded, stable and omit auth fields'
Assert-Condition (
    -not [regex]::IsMatch($adminWebSql, '(?i)create\s+table|alter\s+table|create\s+policy|drop\s+') -and
    -not [regex]::IsMatch($adminWebSql, '(?i)grant\s+(select|insert|update|delete|all)\s+on\s+table')
) 'admin Web migration changes no tables, RLS or table privileges'
Assert-Condition (
    $adminWebTestSql.Contains('select extensions.plan(36);') -and
    [regex]::Matches(
        $adminWebTestSql,
        '(?im)^select\s+extensions\.(has_function|function_returns|ok|is|throws_ok|results_eq)\s*\('
    ).Count -eq 36
) 'admin Web pgTAP plan matches its thirty-six assertions'
Assert-Condition (
    $adminWebTestSql.Contains('branches exclude inactive rows by default') -and
    $adminWebTestSql.Contains('branch cursor advances without repeating the previous row') -and
    $adminWebTestSql.Contains('staff projection omits auth email, tokens and claims') -and
    $adminWebTestSql.Contains('staff search is normalized and case insensitive') -and
    $adminWebTestSql.Contains('SALES cannot query administrative branches') -and
    $adminWebTestSql.Contains('CASHIER cannot query administrative staff')
) 'admin Web tests cover authorization, pagination, filtering and privacy'

Assert-Condition (
    $accessContextSql.Contains('create or replace function public.get_my_access_context()') -and
    [regex]::IsMatch(
        $accessContextSql,
        "(?is)security\s+definer\s+set\s+search_path\s*=\s*''"
    ) -and
    $accessContextSql.Contains('revoke all on function public.get_my_access_context() from public;') -and
    $accessContextSql.Contains('revoke all on function public.get_my_access_context() from anon;') -and
    $accessContextSql.Contains('revoke all on function public.get_my_access_context() from authenticated;') -and
    $accessContextSql.Contains('grant execute on function public.get_my_access_context() to authenticated;') -and
    -not $accessContextSql.Contains('to service_role;')
) 'access-context RPC is restricted to authenticated with a secure definer context'
Assert-Condition (
    $publicCatalogSql.Contains('create or replace function public.get_public_catalog(') -and
    [regex]::IsMatch(
        $publicCatalogSql,
        "(?is)security\s+definer\s+set\s+search_path\s*=\s*''"
    ) -and
    -not $publicCatalogSql.Contains('pg_catalog.coalesce') -and
    [regex]::Matches(
        $publicCatalogSql,
        '(?is)revoke all on function public\.get_public_catalog\s*\([^;]*?\) from (public|anon|authenticated);'
    ).Count -eq 3 -and
    [regex]::IsMatch(
        $publicCatalogSql,
        '(?is)grant execute on function public\.get_public_catalog\s*\([^;]*?\) to anon, authenticated;'
    ) -and
    -not $publicCatalogSql.Contains('to service_role;') -and
    -not [regex]::IsMatch($publicCatalogSql, '(?i)grant\s+.+\s+on\s+(table\s+)?public\.')
) 'public catalog RPC keeps the audited signature, safe body, and exact client grants'
Assert-Condition (
    $catalogImagesSql.Contains('insert into storage.buckets (') -and
    $catalogImagesSql.Contains("'catalog-images',") -and
    $catalogImagesSql.Contains('5242880,') -and
    $catalogImagesSql.Contains(
        "array['image/jpeg', 'image/png', 'image/webp', 'image/avif']::pg_catalog.text[]"
    ) -and
    $catalogImagesSql.Contains(
        'The existing catalog-images bucket is incompatible with the public catalog contract'
    )
) 'catalog image bucket is created with the exact public size and MIME contract and rejects incompatibility'
Assert-Condition (
    $catalogImagesSql.Contains(
        'add constraint product_images_storage_path_catalog_object_key_check'
    ) -and
    $catalogImagesSql.Contains('storage_path = pg_catalog.btrim(storage_path)') -and
    $catalogImagesSql.Contains("storage_path !~* '^[a-z][a-z0-9+.-]*:'") -and
    $catalogImagesSql.Contains("storage_path !~ '^/'") -and
    $catalogImagesSql.Contains("storage_path !~ '^catalog-images/'") -and
    $catalogImagesSql.Contains('pg_catalog.strpos(storage_path, pg_catalog.chr(92)) = 0') -and
    $catalogImagesSql.Contains("storage_path !~ '(^|/)[.]{1,2}(/|$)'") -and
    $catalogImagesSql.Contains("storage_path ~* '[.](jpe?g|png|webp|avif)$'") -and
    $catalogImagesSql.Contains(
        'validate constraint product_images_storage_path_catalog_object_key_check;'
    )
) 'product image paths are validated as safe relative catalog object keys'
Assert-Condition (
    -not [regex]::IsMatch($catalogImagesSql, '(?i)create\s+policy') -and
    -not [regex]::IsMatch(
        $catalogImagesSql,
        '(?i)grant\s+(insert|update|delete|all).+storage\.(objects|buckets)'
    )
) 'catalog image migration adds no Storage write policy or table grant'
Assert-Condition (
    $catalogImagesSql.Contains('create or replace function public.get_public_catalog(') -and
    $catalogImagesSql.Contains("'schemaVersion', 2") -and
    $catalogImagesSql.Contains("'bucketName', 'catalog-images'") -and
    $catalogImagesSql.Contains("'storagePath', row_data.image_storage_path") -and
    -not $catalogImagesSql.Contains('pg_catalog.coalesce') -and
    -not [regex]::IsMatch($catalogImagesSql, '(?i)https?://|supabase\.co') -and
    [regex]::IsMatch(
        $catalogImagesSql,
        "(?is)security\s+definer\s+set\s+search_path\s*=\s*''"
    ) -and
    [regex]::Matches(
        $catalogImagesSql,
        '(?is)revoke all on function public\.get_public_catalog\s*\([^;]*?\) from (public|anon|authenticated);'
    ).Count -eq 3 -and
    [regex]::IsMatch(
        $catalogImagesSql,
        '(?is)grant execute on function public\.get_public_catalog\s*\([^;]*?\) to anon, authenticated;'
    ) -and
    -not $catalogImagesSql.Contains('to service_role;')
) 'public catalog V2 preserves the secure RPC boundary and returns relative bucket references'
Assert-Condition (
    $catalogImagesTestSql.Contains('select extensions.plan(18);') -and
    [regex]::Matches(
        $catalogImagesTestSql,
        '(?im)^select\s+extensions\.(ok|is|lives_ok|throws_ok|throws_like)\s*\('
    ).Count -eq 18
) 'catalog image pgTAP plan matches its eighteen assertions'
Assert-Condition (
    $catalogImagesTestSql.Contains('catalog-images allows exactly the four approved MIME types') -and
    $catalogImagesTestSql.Contains('catalog migration grants no object write policy to client roles') -and
    $catalogImagesTestSql.Contains('an absolute URL is rejected') -and
    $catalogImagesTestSql.Contains('parent traversal is rejected') -and
    $catalogImagesTestSql.Contains('a leading slash is rejected') -and
    $catalogImagesTestSql.Contains('a backslash is rejected') -and
    $catalogImagesTestSql.Contains('an unapproved extension is rejected') -and
    $catalogImagesTestSql.Contains('a valid relative image object key is accepted')
) 'catalog image tests cover bucket security and accepted or rejected path shapes'
Assert-Condition (
    $publicRpcTestSql.Contains('select extensions.plan(20);') -and
    [regex]::Matches(
        $publicRpcTestSql,
        '(?im)^select\s+extensions\.(ok|is|throws_ok|results_eq)\s*\('
    ).Count -eq 20
) 'public RPC pgTAP plan matches its twenty assertions'
Assert-Condition (
    $publicRpcTestSql.Contains('public catalog omits private product fields') -and
    $publicRpcTestSql.Contains('anon has no direct privileges on catalog tables') -and
    $publicRpcTestSql.Contains('service_role cannot execute either presentation RPC') -and
    $publicRpcTestSql.Contains("'schemaVersion', 2") -and
    $publicRpcTestSql.Contains("'bucketName', 'catalog-images'") -and
    $publicRpcTestSql.Contains('public catalog returns a null image when no product image exists')
) 'public RPC tests preserve privacy, role boundaries, and the complete V2 image contract'

Write-Output "Migration security verification passed: $($checks.Count) checks."
$checks | ForEach-Object { Write-Output "PASS: $_" }
