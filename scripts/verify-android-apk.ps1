[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$PreviousApkPath,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$ExpectedVersionCode,
    [Parameter(Mandatory = $true)][string]$ExpectedVersionName,
    [string]$BuildToolsPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\37.0.0'),
    [switch]$AllowDebug
)

$ErrorActionPreference = 'Stop'
$signer = Join-Path $BuildToolsPath 'apksigner.bat'
$aapt = Join-Path $BuildToolsPath 'aapt2.exe'
if (!(Test-Path -LiteralPath $signer -PathType Leaf) -or !(Test-Path -LiteralPath $aapt -PathType Leaf)) {
    throw 'No se encuentran apksigner y aapt2. Indica -BuildToolsPath del SDK instalado.'
}

function Read-ApkEvidence([string]$Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if (!(Test-Path -LiteralPath $resolved -PathType Leaf)) { throw 'Se requiere un archivo APK.' }
    $signatures = (& $signer verify --print-certs $resolved 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Firma APK invalida o verificacion no disponible.' }
    $certificates = @([regex]::Matches($signatures, 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Select-Object -Unique)
    if ($certificates.Count -ne 1) { throw 'Se requiere exactamente un certificado de firma; revisar manualmente rotaciones o firmas multiples.' }
    $badging = (& $aapt dump badging $resolved 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron leer los metadatos APK.' }
    $package = [regex]::Match($badging, "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
    if (!$package.Success) { throw 'Metadatos de paquete incompletos.' }
    [pscustomobject]@{
        path = $resolved
        package = $package.Groups[1].Value
        version_code = [int]$package.Groups[2].Value
        version_name = $package.Groups[3].Value
        certificate_sha256 = $certificates[0]
        debuggable = [regex]::IsMatch($badging, '(?m)^application-debuggable\s*$')
    }
}

$candidate = Read-ApkEvidence $ApkPath
$previous = Read-ApkEvidence $PreviousApkPath
if ($candidate.package -ne 'com.intutec.viveroapp' -or $previous.package -ne $candidate.package) {
    throw 'El paquete debe coincidir con la app habitual com.intutec.viveroapp.'
}
if ($candidate.certificate_sha256 -ne $previous.certificate_sha256) {
    throw 'Certificados distintos: este APK no permite actualizar normalmente la instalacion de referencia. Conservar sus datos y resolver la firma.'
}
if ($candidate.version_code -le $previous.version_code) { throw 'La version nueva debe tener un versionCode mayor que la anterior.' }
if ($candidate.version_code -ne $ExpectedVersionCode -or $candidate.version_name -cne $ExpectedVersionName) {
    throw 'La version del artefacto no coincide con la entrega esperada.'
}
if ($candidate.debuggable -and !$AllowDebug) { throw 'APK debug: solo se admite con -AllowDebug para ensayo, no como release definitivo.' }

[pscustomobject]@{
    status = 'PASS'
    apk = $candidate.path
    package = $candidate.package
    version_code = $candidate.version_code
    version_name = $candidate.version_name
    certificate_sha256 = $candidate.certificate_sha256
    apk_sha256 = (Get-FileHash -LiteralPath $candidate.path -Algorithm SHA256).Hash.ToLowerInvariant()
    debuggable = $candidate.debuggable
    scope = 'Firma, paquete y version compatibles; no acredita migracion Room, destino HTTPS ni aceptacion en dispositivo.'
} | ConvertTo-Json
