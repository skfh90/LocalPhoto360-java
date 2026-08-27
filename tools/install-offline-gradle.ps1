#Requires -Version 5.1
<#
.SYNOPSIS
  Replace C:\Users\1-PYC\.gradle with the trimmed LocalPhoto360 cache,
  or only delete Gradle junk (daemons, old versions, transform caches).

.EXAMPLE
  # Parts live in offline\gradle-user-home\ after cloning the repo:
  powershell -ExecutionPolicy Bypass -File .\tools\install-offline-gradle.ps1

.EXAMPLE
  # Only clean a bloated Gradle home; keep existing dependencies:
  powershell -ExecutionPolicy Bypass -File .\tools\install-offline-gradle.ps1 -CleanOnly
#>
param(
    [string]$GradleHome = "C:\Users\1-PYC\.gradle",
    [string]$PartsDir = "",
    [switch]$CleanOnly
)

$ErrorActionPreference = "Stop"

function Stop-GradleDaemons {
    $stopBat = Join-Path $GradleHome "wrapper\dists"
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
        $_.Path -like "*\.gradle\wrapper\dists\*" -or
        $_.CommandLine -like "*GradleDaemon*"
    } | ForEach-Object {
        try { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue } catch { }
    }
}

function Remove-GradleJunk {
    param([string]$HomePath)
    $junkDirs = @(
        "daemon",
        "native",
        "notifications",
        "workers",
        "kotlin-profile",
        "android",
        "caches\journal-1",
        "caches\jars-9"
    )
    foreach ($rel in $junkDirs) {
        $path = Join-Path $HomePath $rel
        if (Test-Path $path) {
            Write-Host "Removing $path"
            Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    $caches = Join-Path $HomePath "caches"
    if (Test-Path $caches) {
        Get-ChildItem $caches -Directory | Where-Object { $_.Name -match '^[0-9]+\.[0-9]+' } | ForEach-Object {
            Write-Host "Removing transform cache $($_.FullName)"
            Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    $dists = Join-Path $HomePath "wrapper\dists"
    if (Test-Path $dists) {
        Get-ChildItem $dists -Directory | Where-Object { $_.Name -ne "gradle-8.9-bin" } | ForEach-Object {
            Write-Host "Removing unused Gradle dist $($_.FullName)"
            Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

function Find-PartsDir {
    if ($PartsDir -and (Test-Path $PartsDir)) { return $PartsDir }
    $repoRoot = Join-Path (Split-Path -Parent $PSCommandPath) ".."
    $candidates = @(
        (Join-Path $repoRoot "offline\gradle-user-home"),
        (Split-Path -Parent $PSCommandPath),
        $repoRoot,
        (Get-Location).Path,
        (Join-Path $env:USERPROFILE "Downloads")
    )
    foreach ($dir in $candidates) {
        $probe = Join-Path $dir "gradle-user-home.zip.00"
        if (Test-Path $probe) { return (Resolve-Path $dir).Path }
        $probeZip = Join-Path $dir "gradle-user-home.zip"
        if (Test-Path $probeZip) { return (Resolve-Path $dir).Path }
    }
    throw "Could not find gradle-user-home.zip or gradle-user-home.zip.00. Download the parts and rerun."
}

function Join-ZipParts {
    param([string]$Dir)
    $whole = Join-Path $Dir "gradle-user-home.zip"
    if (Test-Path $whole) { return $whole }
    $parts = Get-ChildItem -Path $Dir -Filter "gradle-user-home.zip.*" |
        Where-Object { $_.Name -match '^gradle-user-home\.zip\.\d+$' } |
        Sort-Object Name
    if (-not $parts) { throw "No gradle-user-home.zip.* parts in $Dir" }
    $joined = Join-Path $env:TEMP "gradle-user-home-joined.zip"
    Write-Host "Joining $($parts.Count) parts into $joined"
    $out = [System.IO.File]::Create($joined)
    try {
        foreach ($part in $parts) {
            Write-Host "  $($part.Name)"
            $bytes = [System.IO.File]::ReadAllBytes($part.FullName)
            $out.Write($bytes, 0, $bytes.Length)
        }
    } finally {
        $out.Close()
    }
    return $joined
}

Write-Host "Gradle home: $GradleHome"
Stop-GradleDaemons

if ($CleanOnly) {
    if (-not (Test-Path $GradleHome)) {
        Write-Host "Nothing to clean; $GradleHome does not exist."
        exit 0
    }
    Remove-GradleJunk -HomePath $GradleHome
    Write-Host "Cleaned junk. Left wrapper dists and caches\modules-2 in place."
    exit 0
}

$sourceDir = Find-PartsDir
$zipPath = Join-ZipParts -Dir $sourceDir

if (Test-Path $GradleHome) {
    $backup = "$GradleHome.bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
    Write-Host "Renaming existing Gradle home to $backup"
    Rename-Item -LiteralPath $GradleHome -NewName (Split-Path $backup -Leaf)
}

New-Item -ItemType Directory -Path $GradleHome | Out-Null
Write-Host "Extracting $zipPath -> $GradleHome"
Expand-Archive -LiteralPath $zipPath -DestinationPath $GradleHome -Force
Write-Host "Done. wrapper\ and caches\modules-2 are ready for offline ./gradlew --offline assembleDebug."
Write-Host "You still need JDK 17+ and Android SDK Platform 35."
