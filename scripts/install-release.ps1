param(
    [string]$ApkPath,
    [string]$Device,
    [switch]$Rebuild,
    [switch]$GrantAll
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if ($PSScriptRoot) {
        $here = $PSScriptRoot
    } else {
        $here = Split-Path -Parent $MyInvocation.MyCommand.Definition
    }
    return (Resolve-Path (Join-Path $here "..")).Path
}

function Get-SdkPath {
    param([string]$RepoRoot)

    $localProps = Join-Path $RepoRoot "local.properties"
    if (Test-Path $localProps) {
        $sdkLine = Get-Content -Path $localProps | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            return (($sdkLine -replace '^sdk.dir=', '') -replace '\\\\', '\')
        }
    }

    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }

    return $null
}

function Get-AdbPath {
    param([string]$SdkPath)

    if (-not $SdkPath) { return $null }
    $adb = Join-Path $SdkPath "platform-tools\\adb.exe"
    if (Test-Path $adb) { return $adb }
    return $null
}

function Get-ConnectedDevices {
    param([string]$AdbPath)

    $lines = & $AdbPath devices
    return $lines | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "`t")[0] }
}

$repoRoot = Get-RepoRoot
$defaultApk = Join-Path $repoRoot "app\\build\\outputs\\apk\\release\\app-release.apk"

if (-not $ApkPath) {
    $ApkPath = $defaultApk
} elseif (-not [System.IO.Path]::IsPathRooted($ApkPath)) {
    $ApkPath = Join-Path $repoRoot $ApkPath
}

if ($Rebuild -or -not (Test-Path $ApkPath)) {
    $gradlew = Join-Path $repoRoot "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        throw "gradlew.bat not found at $gradlew"
    }

    & $gradlew clean :app:assembleRelease --no-build-cache "-Pkotlin.incremental=false" "-Pkapt.useBuildCache=false" "-Pkapt.incremental.apt=false" --console=plain
}

if (-not (Test-Path $ApkPath)) {
    throw "APK not found at $ApkPath"
}

$sdkPath = Get-SdkPath -RepoRoot $repoRoot
$adbPath = Get-AdbPath -SdkPath $sdkPath
if (-not $adbPath) {
    throw "adb not found. Set ANDROID_SDK_ROOT or ANDROID_HOME, or ensure local.properties has sdk.dir."
}

& $adbPath start-server | Out-Null

$devices = @(Get-ConnectedDevices -AdbPath $adbPath)
if ($Device) {
    $serial = $Device
} else {
    if ($devices.Count -eq 0) {
        throw "No connected devices found. Connect a device or specify -Device <serial>."
    }
    if ($devices.Count -gt 1) {
        throw "Multiple devices found. Specify -Device <serial>."
    }
    $serial = $devices[0]
}

$adbArgs = @()
if ($serial) { $adbArgs += @("-s", $serial) }
$adbArgs += "install"
$adbArgs += "-r"
if ($GrantAll) { $adbArgs += "-g" }
$adbArgs += $ApkPath

& $adbPath @adbArgs
