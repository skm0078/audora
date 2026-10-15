# File: scripts/release/new-release-key.ps1
# Purpose: Creates Audora's release signing key once, on the maintainer's Windows PC, and walks through
#          copying it into GitHub's "release" environment secrets. Runbook: docs/RELEASING.md.
#
# Run in PowerShell:
#   powershell -ExecutionPolicy Bypass -File .\new-release-key.ps1
# The key goes to D:\AUDORA\signing (change with -SigningDir). An existing key is never overwritten:
# running the script again only repeats the copy-to-GitHub steps.
# Needs keytool, which comes with any Java JDK (no Android Studio needed).

[CmdletBinding()]
param(
    [string]$SigningDir = 'D:\AUDORA\signing',
    [string]$KeyAlias = 'audora'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$KeystoreName = 'audora-release.p12'
$NotesName = 'audora-release-passwords.txt'
# About 27 years. Every future update must be signed with this same key, so it has to outlive the app.
$ValidityDays = 10000
$KeySizeBits = 4096
$PasswordLength = 32
# Letters and digits only, without look-alikes (0/O, 1/l/I), so it survives copy, paste and reading aloud.
$PasswordAlphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
$CertificateName = 'CN=Audora, O=skm0078'
$EnvironmentsUrl = 'https://github.com/skm0078/audora/settings/environments'
$PasswordEnvVar = 'AUDORA_NEW_KEY_PASSWORD'

function Find-Keytool {
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $roots = @()
    if ($env:JAVA_HOME) { $roots += $env:JAVA_HOME }
    $roots += @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Android\Android Studio\jbr"
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $found = Get-ChildItem -Path $root -Filter keytool.exe -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

function New-RandomPassword {
    param([int]$Length, [string]$Alphabet)
    # Rejection sampling keeps every character equally likely.
    $limit = 256 - (256 % $Alphabet.Length)
    $builder = New-Object System.Text.StringBuilder
    $buffer = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        while ($builder.Length -lt $Length) {
            $rng.GetBytes($buffer)
            foreach ($b in $buffer) {
                if ($b -lt $limit -and $builder.Length -lt $Length) {
                    [void]$builder.Append($Alphabet[$b % $Alphabet.Length])
                }
            }
        }
    } finally {
        $rng.Dispose()
    }
    return $builder.ToString()
}

function Invoke-Keytool {
    param([string]$Keytool, [string]$Password, [string[]]$Arguments)
    # The password reaches keytool through an environment variable, never the command line.
    Set-Item -Path "Env:$PasswordEnvVar" -Value $Password
    try {
        $output = & $Keytool @Arguments
        if ($LASTEXITCODE -ne 0) { throw "keytool failed (exit code $LASTEXITCODE)" }
        return $output
    } finally {
        Remove-Item -Path "Env:$PasswordEnvVar" -ErrorAction SilentlyContinue
    }
}

function Get-CertificateSha256 {
    param([string]$Keytool, [string]$KeystorePath, [string]$Password)
    $listing = Invoke-Keytool -Keytool $Keytool -Password $Password -Arguments @(
        '-list', '-v', '-keystore', $KeystorePath, '-storetype', 'PKCS12', "-storepass:env", $PasswordEnvVar
    )
    $line = $listing | Where-Object { $_ -match '^\s*SHA256:\s*([0-9A-Fa-f:]+)\s*$' } | Select-Object -First 1
    if (-not $line) { throw 'Could not read the certificate fingerprint from keytool.' }
    [void]($line -match 'SHA256:\s*([0-9A-Fa-f:]+)')
    # Same form apksigner prints, so it can be compared with the release workflow's output.
    return ($Matches[1] -replace ':', '').ToLowerInvariant()
}

function Read-SavedPassword {
    param([string]$NotesPath)
    $line = Get-Content $NotesPath | Where-Object { $_ -match '^Password:\s*(\S+)\s*$' } | Select-Object -First 1
    if (-not $line) { throw "No 'Password:' line in $NotesPath" }
    [void]($line -match '^Password:\s*(\S+)')
    return $Matches[1]
}

function Copy-SecretsToGitHub {
    param([string]$KeystorePath, [string]$Password)
    $secrets = [ordered]@{
        KEYSTORE_B64      = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
        KEYSTORE_PASSWORD = $Password
        KEY_ALIAS         = $KeyAlias
        KEY_PASSWORD      = $Password
    }
    Write-Host ''
    Write-Host "Now open $EnvironmentsUrl and click the 'release' environment (docs/RELEASING.md, step 2)."
    Write-Host 'For each secret below: click "Add environment secret", type the Name shown,'
    Write-Host 'click into the Value box, press Ctrl+V, then click "Add secret".'
    foreach ($name in $secrets.Keys) {
        Set-Clipboard -Value $secrets[$name]
        Write-Host ''
        Write-Host "Copied. Name: $name" -ForegroundColor Cyan
        [void](Read-Host 'Press Enter after adding it')
    }
    Set-Clipboard -Value ' '
    Write-Host ''
    Write-Host 'All four secrets copied. Clipboard cleared.' -ForegroundColor Green
}

$keystorePath = Join-Path $SigningDir $KeystoreName
$notesPath = Join-Path $SigningDir $NotesName

if (Test-Path $keystorePath) {
    Write-Host "A release key already exists at $keystorePath. It will NOT be replaced." -ForegroundColor Yellow
    if (-not (Test-Path $notesPath)) { throw "Its password file $notesPath is missing. Restore it from your backup." }
    Copy-SecretsToGitHub -KeystorePath $keystorePath -Password (Read-SavedPassword -NotesPath $notesPath)
    exit 0
}

$keytool = Find-Keytool
if (-not $keytool) {
    Write-Host 'keytool was not found. Install a free Java JDK, then run this script again:' -ForegroundColor Red
    Write-Host '  winget install EclipseAdoptium.Temurin.21.JDK'
    Write-Host 'Close and reopen PowerShell after installing.'
    exit 1
}
Write-Host "Using $keytool"

New-Item -ItemType Directory -Force -Path $SigningDir | Out-Null
$password = New-RandomPassword -Length $PasswordLength -Alphabet $PasswordAlphabet

[void](Invoke-Keytool -Keytool $keytool -Password $password -Arguments @(
    '-genkeypair', '-keystore', $keystorePath, '-storetype', 'PKCS12', '-alias', $KeyAlias,
    '-keyalg', 'RSA', '-keysize', "$KeySizeBits", '-validity', "$ValidityDays", '-dname', $CertificateName,
    "-storepass:env", $PasswordEnvVar, "-keypass:env", $PasswordEnvVar
))
$fingerprint = Get-CertificateSha256 -Keytool $keytool -KeystorePath $keystorePath -Password $password

$created = Get-Date -Format 'yyyy-MM-dd'
@(
    'Audora release signing key. KEEP SECRET. Never commit, upload or paste this anywhere but GitHub secrets.',
    'If this key is lost, friends cannot update Audora: they must uninstall it and lose their app data.',
    '',
    "Created: $created",
    "Keystore file: $KeystoreName (PKCS12)",
    "Alias: $KeyAlias",
    "Password: $password",
    'Key password: same as Password',
    "Certificate SHA-256: $fingerprint",
    '',
    'Backups: copy this whole folder to two places that are not this PC (docs/RELEASING.md, step 3).'
) | Set-Content -Path $notesPath -Encoding ASCII

Write-Host ''
Write-Host "Created $keystorePath" -ForegroundColor Green
Write-Host "Password and details saved in $notesPath"
Write-Host "Certificate SHA-256: $fingerprint"

Copy-SecretsToGitHub -KeystorePath $keystorePath -Password $password

Write-Host ''
Write-Host "Last step: copy the folder $SigningDir to two backups that are not this PC (for example two USB sticks)." -ForegroundColor Yellow
Write-Host 'Without this key, no future version can update the app on friends'' phones.' -ForegroundColor Yellow
