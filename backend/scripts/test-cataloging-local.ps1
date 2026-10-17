param([Parameter(Mandatory = $true)][string]$MariaDbBin)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$binaryDirectory = (Resolve-Path -LiteralPath $MariaDbBin).Path
$installer = Join-Path $binaryDirectory 'mysql_install_db.exe'
$server = Join-Path $binaryDirectory 'mariadbd.exe'
if (!(Test-Path -LiteralPath $installer) -or !(Test-Path -LiteralPath $server)) { throw 'MariaDB portable binaries required.' }
# Refuse to use another process already listening on the test port.
$probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,43389)
try { $probe.Start() } finally { $probe.Stop() }
$run = 'plants-contract-' + [guid]::NewGuid().ToString('N')
$directory = Join-Path $repository "tmp/$run"
New-Item -ItemType Directory -Path $directory | Out-Null
$password = 'Synthetic-' + [guid]::NewGuid().ToString('N')
@{password=$password} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'native-db.private.json') -Encoding utf8
& $installer "--datadir=$directory/data" "--password=$password" --port=43389
if ($LASTEXITCODE -ne 0) { throw 'Synthetic database initialization failed.' }
$databaseProcess = $null
$previousEnvironment = @{}
foreach ($name in @('TEST_SCOPE','DB_HOST','DB_PORT','CATALOGING_TEST_RUN')) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location (Join-Path $repository 'backend')
try {
  $databaseProcess = Start-Process -FilePath $server -ArgumentList @('--no-defaults',"--datadir=`"$directory/data`"",'--port=43389','--bind-address=127.0.0.1','--skip-name-resolve',"--log-error=`"$directory/database.log`"") -WindowStyle Hidden -PassThru
  $ready = $false
  for ($attempt=0; $attempt -lt 40; $attempt++) {
    if ($databaseProcess.HasExited) { throw "Synthetic MariaDB exited. Inspect $directory/database.log" }
    $client = [Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1',43389); $ready=$true; break }
    catch { Start-Sleep -Milliseconds 250 }
    finally { $client.Dispose() }
  }
  if (!$ready) { throw 'Synthetic MariaDB did not start.' }
  $env:TEST_SCOPE='cataloging-native-synthetic'
  $env:DB_HOST='127.0.0.1'
  $env:DB_PORT='43389'
  $env:CATALOGING_TEST_RUN=$run
  & node --test --test-concurrency=1 test/cataloging-integration.test.js
  $testExit = $LASTEXITCODE
} finally {
  if ($databaseProcess -and !$databaseProcess.HasExited) { Stop-Process -Id $databaseProcess.Id; $databaseProcess.WaitForExit() }
  foreach ($name in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
  Pop-Location
}
Write-Output "Synthetic artifacts retained in $directory; test server stopped."
exit $testExit
