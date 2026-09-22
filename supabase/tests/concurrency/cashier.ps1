#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^vivero-validation-db-[a-z0-9-]+$')]
    [string]$Container
)

$ErrorActionPreference = 'Stop'
$dockerPath = (Get-Command docker -ErrorAction Stop).Source
$purpose = & $dockerPath --context desktop-linux inspect $Container --format '{{index .Config.Labels "purpose"}}'
if ($LASTEXITCODE -ne 0 -or $purpose -ne 'vivero-release-validation') {
    throw 'Concurrency fixtures require the isolated validation container.'
}
$cashierA = '22000000-0000-0000-0000-000000000002'
$cashierB = '22000000-0000-0000-0000-000000000003'

function New-AuthenticatedSql {
    param([string]$ActorId, [string]$Statement)
    @"
begin;
set local statement_timeout = '20s';
set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '$ActorId', true);
select pg_catalog.set_config('request.jwt.claims', '{"sub":"$ActorId","role":"authenticated"}', true);
$Statement
commit;
"@
}

function Invoke-Psql {
    param([string]$Sql, [switch]$AllowFailure)
    $output = & $dockerPath --context desktop-linux exec $Container psql -U postgres -d postgres -X -q -A -t -v ON_ERROR_STOP=1 -c $Sql 2>&1
    $exitCode = $LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "psql failed: $($output -join ' ')"
    }
    [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join "`n") }
}

function Start-PsqlJob {
    param([string]$Name, [string]$Sql)
    Start-Job -Name $Name -ArgumentList $dockerPath, $container, $Sql -ScriptBlock {
        param($DockerPath, $Container, $CommandSql)
        $output = & $DockerPath --context desktop-linux exec $Container psql -U postgres -d postgres -X -q -A -t -v ON_ERROR_STOP=1 -c $CommandSql 2>&1
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n") }
    }
}

function Receive-PsqlPair {
    param($First, $Second)
    try {
        $null = Wait-Job -Job $First, $Second -Timeout 30
        if ($First.State -ne 'Completed' -or $Second.State -ne 'Completed') {
            throw 'Concurrent sessions did not finish successfully within 30 seconds.'
        }
        @((Receive-Job $First), (Receive-Job $Second))
    } finally {
        Stop-Job -Job $First, $Second
        Remove-Job -Job $First, $Second
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-Scalar {
    param([string]$Sql)
    (Invoke-Psql -Sql $Sql).Output.Trim()
}

& $dockerPath --context desktop-linux exec $Container psql -U postgres -d postgres -X -q -v ON_ERROR_STOP=1 -f /workspace/supabase/tests/concurrency/cashier_setup.sql
if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated concurrency fixtures.' }

Write-Output 'SCENARIO claim-race'
$claimA = New-AuthenticatedSql $cashierA "select public.claim_sale_for_payment('52000000-0000-0000-0000-000000000001', null);"
$claimB = New-AuthenticatedSql $cashierB "select public.claim_sale_for_payment('52000000-0000-0000-0000-000000000001', null);"
$claimRace = Receive-PsqlPair (Start-PsqlJob 'claim-a' $claimA) (Start-PsqlJob 'claim-b' $claimB)
Assert-True (($claimRace | Where-Object ExitCode -eq 0).Count -eq 1) 'Exactly one competing cashier must acquire the claim.'
Assert-True (($claimRace | Where-Object { $_.Output -match 'CLAIM_UNAVAILABLE' }).Count -eq 1) 'The losing cashier must receive CLAIM_UNAVAILABLE.'
Write-Output 'PASS: one claim acquired; competing cashier rejected'

foreach ($saleSuffix in 2, 3, 5) {
    $saleId = '52000000-0000-0000-0000-{0:d12}' -f $saleSuffix
    $sql = New-AuthenticatedSql $cashierA "select public.claim_sale_for_payment('$saleId', null);"
    $null = Invoke-Psql $sql
}

Write-Output 'SCENARIO different-idempotency-keys'
$token2 = Get-Scalar "select claim_token from public.sale_payment_claims where sale_id='52000000-0000-0000-0000-000000000002' and released_at is null and consumed_at is null;"
$confirm2a = New-AuthenticatedSql $cashierA "select public.confirm_sale_payment('52000000-0000-0000-0000-000000000002','$token2','72000000-0000-0000-0000-000000000201','CARD',null,'TERM-A1');"
$confirm2b = New-AuthenticatedSql $cashierA "select public.confirm_sale_payment('52000000-0000-0000-0000-000000000002','$token2','72000000-0000-0000-0000-000000000202','CARD',null,'TERM-A1');"
$differentKeys = Receive-PsqlPair (Start-PsqlJob 'different-a' $confirm2a) (Start-PsqlJob 'different-b' $confirm2b)
Assert-True (($differentKeys | Where-Object ExitCode -eq 0).Count -eq 1) 'Only one different-key confirmation may succeed.'
Assert-True (($differentKeys | Where-Object { $_.Output -match 'SALE_ALREADY_PAID' }).Count -eq 1) 'The second different key must receive SALE_ALREADY_PAID.'
Write-Output 'PASS: one payment; second distinct key rejected'

Write-Output 'SCENARIO same-idempotency-key'
$token3 = Get-Scalar "select claim_token from public.sale_payment_claims where sale_id='52000000-0000-0000-0000-000000000003' and released_at is null and consumed_at is null;"
$confirm3 = New-AuthenticatedSql $cashierA "select public.confirm_sale_payment('52000000-0000-0000-0000-000000000003','$token3','73000000-0000-0000-0000-000000000003','CARD',null,'TERM-SAME');"
$sameKey = Receive-PsqlPair (Start-PsqlJob 'same-a' $confirm3) (Start-PsqlJob 'same-b' $confirm3)
Assert-True (($sameKey | Where-Object ExitCode -eq 0).Count -eq 2) 'Both identical retries must return successfully.'
Assert-True (($sameKey | Where-Object { $_.Output -match '"idempotent_replay": false' }).Count -eq 1) 'Exactly one call must create the payment.'
Assert-True (($sameKey | Where-Object { $_.Output -match '"idempotent_replay": true' }).Count -eq 1) 'Exactly one call must return the canonical replay.'
Write-Output 'PASS: identical simultaneous retry returned one create and one replay'

Write-Output 'SCENARIO expired-claim-race'
$expiredA = New-AuthenticatedSql $cashierA "select public.claim_sale_for_payment('52000000-0000-0000-0000-000000000004', null);"
$expiredB = New-AuthenticatedSql $cashierB "select public.claim_sale_for_payment('52000000-0000-0000-0000-000000000004', null);"
$expiredRace = Receive-PsqlPair (Start-PsqlJob 'expired-a' $expiredA) (Start-PsqlJob 'expired-b' $expiredB)
Assert-True (($expiredRace | Where-Object ExitCode -eq 0).Count -eq 1) 'Exactly one cashier must replace the expired claim.'
Assert-True (($expiredRace | Where-Object { $_.Output -match 'CLAIM_UNAVAILABLE' }).Count -eq 1) 'The competing replacement must be rejected.'
Write-Output 'PASS: expired claim closed; one replacement claim acquired'

Write-Output 'SCENARIO confirmation-waits-for-sale-lock'
$token5 = Get-Scalar "select claim_token from public.sale_payment_claims where sale_id='52000000-0000-0000-0000-000000000005' and released_at is null and consumed_at is null;"
$blockerSql = "begin; set local statement_timeout = '20s'; set local application_name = 'vivero-lock-holder'; select id from public.sales where id='52000000-0000-0000-0000-000000000005' for update; select pg_catalog.pg_sleep(8); commit;"
$confirm5 = New-AuthenticatedSql $cashierA "select public.confirm_sale_payment('52000000-0000-0000-0000-000000000005','$token5','75000000-0000-0000-0000-000000000005','TRANSFER',null,'BANK-LOCK');"
$blockerJob = Start-PsqlJob 'blocker' $blockerSql
$confirmJob = $null
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $lockHeld = Get-Scalar "select count(*) from pg_stat_activity where application_name='vivero-lock-holder' and wait_event='PgSleep';"
        if ($lockHeld -eq '1') { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    Assert-True ($lockHeld -eq '1') 'The blocker did not acquire the sale lock.'
    $confirm5 = $confirm5.Replace('begin;', "begin; set local application_name = 'vivero-lock-waiter';")
    $confirmJob = Start-PsqlJob 'blocked-confirm' $confirm5
    do {
        $lockWait = Get-Scalar "select count(*) from pg_stat_activity where application_name='vivero-lock-waiter' and wait_event_type='Lock' and cardinality(pg_blocking_pids(pid)) > 0;"
        if ($lockWait -eq '1') { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    Assert-True ($lockWait -eq '1') 'Confirmation was not observed waiting for the sale lock.'
    $blocked = Receive-PsqlPair $blockerJob $confirmJob
    Assert-True (($blocked | Where-Object ExitCode -ne 0).Count -eq 0) 'The blocker and waiting confirmation must both commit.'
} finally {
    @($blockerJob, $confirmJob) | Where-Object { $_ -and (Get-Job -Id $_.Id -ErrorAction SilentlyContinue) } | ForEach-Object {
        Stop-Job $_
        Remove-Job $_
    }
}
Write-Output 'PASS: observed a database lock wait; confirmation committed after release'

$verification = Invoke-Psql @"
select s.id, s.status,
       (select count(*) from public.sale_payments p where p.sale_id=s.id) payment_count,
       (select count(*) from public.sale_status_history h where h.sale_id=s.id and h.previous_status='SENT_TO_CASHIER' and h.new_status='PAID') paid_history_count,
       (select count(*) from public.sale_payment_claims c where c.sale_id=s.id and c.consumed_at is not null and c.closed_reason='CONFIRMED') consumed_claim_count,
       (select count(*) from public.inventory_movements m where m.reference_id=s.id and m.quantity=-1) inventory_movement_count
from public.sales s
where s.id in (
 '52000000-0000-0000-0000-000000000002',
 '52000000-0000-0000-0000-000000000003',
 '52000000-0000-0000-0000-000000000005'
)
order by s.id;
"@
$verifiedRows = @($verification.Output -split "`n" | Where-Object { $_ -match '^52000000-' })
Assert-True ($verifiedRows.Count -eq 3) 'Expected verification rows for three paid local fixtures.'
Assert-True (($verifiedRows | Where-Object { $_ -notmatch '\|PAID\|1\|1\|1\|1$' }).Count -eq 0) 'A paid fixture has partial or duplicate side effects.'
$stock = Get-Scalar "select total_quantity = 47 from public.inventory_balances where branch_id='12000000-0000-0000-0000-000000000001' and product_id='42000000-0000-4000-8000-000000000001';"
Assert-True ($stock -eq 't') 'Exactly three paid units must be deducted from inventory.'

$openClaims = Get-Scalar "select count(*) from public.sale_payment_claims where sale_id in ('52000000-0000-0000-0000-000000000001','52000000-0000-0000-0000-000000000004') and released_at is null and consumed_at is null;"
$expiredClosed = Get-Scalar "select count(*) from public.sale_payment_claims where sale_id='52000000-0000-0000-0000-000000000004' and released_at is not null and closed_reason='EXPIRED';"
Assert-True ($openClaims -eq '2') 'Claim-only scenarios must retain exactly one open claim per sale.'
Assert-True ($expiredClosed -eq '1') 'The original expired claim must be closed exactly once.'

Write-Output 'FINAL COUNTS'
Write-Output $verification.Output
Write-Output 'PASS: each paid sale has one payment, one PAID transition, one consumed claim and one inventory movement; stock is 47'
