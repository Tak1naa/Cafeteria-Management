param(
    [string]$OutputDir = "build/validation",
    [string]$ApiContractDir = "build/api_contract",
    [string]$AiContractDir = "build/ai_contract",
    [string]$ReportsDir = "build/reports"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$summaryJsonPath = Join-Path $OutputDir "validation_summary_$timestamp.json"
$summaryMdPath = Join-Path $OutputDir "validation_summary_$timestamp.md"

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    $start = Get-Date
    try {
        & $Action | Out-Null
        $end = Get-Date
        return [pscustomobject]@{
            name = $Name
            status = "passed"
            startedAt = $start.ToString("o")
            finishedAt = $end.ToString("o")
            durationSec = [math]::Round(($end - $start).TotalSeconds, 2)
            message = "ok"
        }
    } catch {
        $end = Get-Date
        return [pscustomobject]@{
            name = $Name
            status = "failed"
            startedAt = $start.ToString("o")
            finishedAt = $end.ToString("o")
            durationSec = [math]::Round(($end - $start).TotalSeconds, 2)
            message = $_.Exception.Message
        }
    }
}

$results = @()

$results += Run-Step -Name "build_executable" -Action {
    g++ -std=c++17 -Iinclude src/main.cpp src/person_generator.cpp src/window_queue.cpp src/table_matrix.cpp src/decision_client.cpp src/simulation_engine.cpp -lwinhttp -o build/cafeteria_sim.exe
    if ($LASTEXITCODE -ne 0) {
        throw "g++ build failed"
    }
}

$results += Run-Step -Name "api_contract_test" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test_api_contract.ps1 -OutputDir $ApiContractDir
    if ($LASTEXITCODE -ne 0) {
        throw "test_api_contract.ps1 failed"
    }
}

$results += Run-Step -Name "ai_contract_test" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ai_contract_test.ps1 -OutputDir $AiContractDir
    if ($LASTEXITCODE -ne 0) {
        throw "run_ai_contract_test.ps1 failed"
    }
}

$results += Run-Step -Name "scenario_comparison" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/compare_scenarios.ps1 -OutputDir $ReportsDir
    if ($LASTEXITCODE -ne 0) {
        throw "compare_scenarios.ps1 failed"
    }
}

$passedCount = @($results | Where-Object { $_.status -eq "passed" }).Count
$failedCount = @($results | Where-Object { $_.status -eq "failed" }).Count
$overallStatus = if ($failedCount -eq 0) { "passed" } else { "failed" }

$summary = [pscustomobject]@{
    timestamp = $timestamp
    overallStatus = $overallStatus
    passedCount = $passedCount
    failedCount = $failedCount
    steps = $results
    artifacts = [pscustomobject]@{
        apiContractDir = $ApiContractDir
        aiContractDir = $AiContractDir
        reportsDir = $ReportsDir
    }
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $summaryJsonPath -Encoding UTF8

$mdLines = @(
    "# Full Validation Summary",
    "",
    "- Timestamp: $timestamp",
    "- OverallStatus: $overallStatus",
    "- Passed: $passedCount",
    "- Failed: $failedCount",
    "",
    "## Steps",
    "",
    "| Step | Status | Duration(s) | Message |",
    "|---|---|---:|---|"
)

foreach ($row in $results) {
    $mdLines += "| $($row.name) | $($row.status) | $($row.durationSec) | $($row.message) |"
}

$mdLines += ""
$mdLines += "## Artifact Directories"
$mdLines += ""
$mdLines += "- ApiContractDir: $ApiContractDir"
$mdLines += "- AiContractDir: $AiContractDir"
$mdLines += "- ReportsDir: $ReportsDir"

$mdLines | Set-Content -Path $summaryMdPath -Encoding UTF8

Write-Host ""
Write-Host "=== Full Validation Completed ==="
Write-Host "Overall: $overallStatus"
Write-Host "Summary JSON: $summaryJsonPath"
Write-Host "Summary MD: $summaryMdPath"

if ($overallStatus -ne "passed") {
    exit 1
}
