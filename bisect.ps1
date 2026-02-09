#!/usr/bin/env pwsh
# Binary-search bisect script to find minimal failing source set
# Moves files in/out of tmp_disabled and runs KAPT after each change

$ErrorActionPreference = 'Stop'

$workspaceRoot = 'C:\BulkSMS2'
$mainSrcDir = "$workspaceRoot\app\src\main\java\com\bulksms\smsmanager"
$tmpDisabledDir = "$mainSrcDir\tmp_disabled"

# Get all items currently in tmp_disabled
$disabledItems = Get-ChildItem -Path $tmpDisabledDir -Force | Select-Object -ExpandProperty Name

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Binary-Search Bisect Launcher" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total disabled items: $($disabledItems.Count)" -ForegroundColor Yellow
Write-Host ""

# Split in half
$mid = [math]::Floor($disabledItems.Count / 2)
$toReenable = $disabledItems[0..($mid-1)]

Write-Host "Step 1: Re-enabling first $($toReenable.Count) items and running KAPT..." -ForegroundColor Yellow
foreach ($item in $toReenable) {
    $srcPath = "$tmpDisabledDir\$item"
    $dstPath = "$mainSrcDir\$item"
    Write-Host "  Moving: $item" -ForegroundColor Gray
    Move-Item -Path $srcPath -Destination $dstPath -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Running KAPT test..." -ForegroundColor Yellow
Push-Location $workspaceRoot
$result = & .\gradlew.bat :app:kaptReleaseKotlin --stacktrace 2>&1
$success = $LASTEXITCODE -eq 0
Pop-Location

if ($success) {
    Write-Host "✅ KAPT succeeded! The bug is in the DISABLED items." -ForegroundColor Green
    Write-Host "Next: disable half of the current disabled set and re-run." -ForegroundColor Green
} else {
    Write-Host "❌ KAPT failed! The bug is in the RE-ENABLED items." -ForegroundColor Red
    Write-Host "Next: move the re-enabled items back and try different half." -ForegroundColor Red
    foreach ($item in $toReenable) {
        $srcPath = "$mainSrcDir\$item"
        $dstPath = "$tmpDisabledDir\$item"
        Write-Host "  Moving back: $item" -ForegroundColor Gray
        Move-Item -Path $srcPath -Destination $dstPath -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "Bisect step complete. Choose your next move:" -ForegroundColor Cyan
Write-Host "  - If KAPT succeeded: disable half the remaining active set" -ForegroundColor Gray
Write-Host "  - If KAPT failed: move failing set back, try disabled half" -ForegroundColor Gray
Write-Host ""
