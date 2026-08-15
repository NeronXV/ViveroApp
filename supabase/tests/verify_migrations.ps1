$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$migrationDirectory = Join-Path $projectRoot 'supabase\migrations'
$migrationFiles = @(Get-ChildItem $migrationDirectory -Filter '*.sql' | Sort-Object Name)
$allSql = ($migrationFiles | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
$authSql = Get-Content (Join-Path $migrationDirectory '202608080001_auth_roles.sql') -Raw
$salesSql = Get-Content (Join-Path $migrationDirectory '202608080003_sales_cart.sql') -Raw
$branchSql = Get-Content (Join-Path $migrationDirectory '202608140001_branch_management.sql') -Raw
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

Assert-Condition ($migrationFiles.Count -eq 4) 'exactly four ordered migration files exist'
Assert-Condition (
    (($migrationFiles.Name -join ',') -eq (
        '202608080001_auth_roles.sql,202608080002_catalog.sql,202608080003_sales_cart.sql,' +
        '202608140001_branch_management.sql'
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
    $securityDefinerCount -eq 9 -and $secureSearchPathCount -eq $securityDefinerCount
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

Write-Output "Migration security verification passed: $($checks.Count) checks."
$checks | ForEach-Object { Write-Output "PASS: $_" }
