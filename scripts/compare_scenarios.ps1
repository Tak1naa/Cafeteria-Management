param(
    [string]$BaselineConfig = "config/scenarios/baseline_seeded.json",
    [string]$StressConfig = "config/scenarios/stress_peak_seeded.json",
    [string]$OutputDir = "build/reports"
)

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$runOutputDir = Join-Path $OutputDir "runs"
if (!(Test-Path $runOutputDir)) {
    New-Item -ItemType Directory -Path $runOutputDir | Out-Null
}

if (!(Test-Path "build/cafeteria_sim.exe")) {
    Write-Host "Executable not found, building with g++..."
    g++ -std=c++17 -Iinclude src/main.cpp src/person_generator.cpp src/window_queue.cpp src/table_matrix.cpp src/decision_client.cpp src/simulation_engine.cpp -lwinhttp -o build/cafeteria_sim.exe
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed"
    }
}

function Invoke-ScenarioRun {
    param(
        [string]$ConfigPath,
        [string]$Tag,
        [string]$Timestamp,
        [string]$RunOutputDir
    )

    if (!(Test-Path $ConfigPath)) {
        throw "Config not found: $ConfigPath"
    }

    $logPath = Join-Path $RunOutputDir ("{0}_{1}.log" -f $Tag, $Timestamp)
    $csvPath = Join-Path $RunOutputDir ("{0}_{1}.csv" -f $Tag, $Timestamp)

    Write-Host "Running [$Tag] with config: $ConfigPath"
    .\build\cafeteria_sim.exe $ConfigPath | Tee-Object -FilePath $logPath

    if (!(Test-Path "build/step_data.csv")) {
        throw "step_data.csv not generated for scenario: $Tag"
    }

    Copy-Item "build/step_data.csv" $csvPath -Force

    return [pscustomobject]@{
        Tag = $Tag
        ConfigPath = $ConfigPath
        LogPath = $logPath
        CsvPath = $csvPath
    }
}

function Get-ScenarioMetrics {
    param(
        [string]$CsvPath,
        [string]$ConfigPath
    )

    $rows = Import-Csv $CsvPath
    if ($rows.Count -eq 0) {
        throw "CSV has no rows: $CsvPath"
    }

    $configObj = Get-Content $ConfigPath -Raw | ConvertFrom-Json
    $capacity = [int]$configObj.tableRows * [int]$configObj.tableCols

    $last = $rows | Select-Object -Last 1
    $totalArrived = [int]$last.totalArrived
    $totalServed = [int]$last.totalServed
    $waitingForSeatEnd = [int]$last.waitingForSeat
    $availableSeatsEnd = [int]$last.availableSeats
    $avgQueueWaitSecEnd = if ($null -ne $last.avgQueueWaitSec) { [double]$last.avgQueueWaitSec } else { 0.0 }
    $maxQueueWaitSecEnd = if ($null -ne $last.maxQueueWaitSec) { [int]$last.maxQueueWaitSec } else { 0 }
    $p50QueueWaitSecEnd = if ($null -ne $last.p50QueueWaitSec) { [double]$last.p50QueueWaitSec } else { 0.0 }
    $p90QueueWaitSecEnd = if ($null -ne $last.p90QueueWaitSec) { [double]$last.p90QueueWaitSec } else { 0.0 }
    $p99QueueWaitSecEnd = if ($null -ne $last.p99QueueWaitSec) { [double]$last.p99QueueWaitSec } else { 0.0 }

    $queueTotals = @()
    $availableSeatsSeries = @()

    foreach ($row in $rows) {
        $availableSeatsSeries += [double]$row.availableSeats

        $queueTotal = 0
        if ($null -ne $row.queueLengths -and $row.queueLengths -ne "") {
            foreach ($value in ($row.queueLengths -split "\|")) {
                if ($value -ne "") {
                    $queueTotal += [int]$value
                }
            }
        }
        $queueTotals += [double]$queueTotal
    }

    $avgQueueTotal = [math]::Round((($queueTotals | Measure-Object -Average).Average), 2)
    $maxQueueTotal = [int](($queueTotals | Measure-Object -Maximum).Maximum)
    $avgAvailableSeats = [math]::Round((($availableSeatsSeries | Measure-Object -Average).Average), 2)

    $servedRatePct = 0.0
    if ($totalArrived -gt 0) {
        $servedRatePct = [math]::Round(($totalServed * 100.0) / $totalArrived, 2)
    }

    $avgSeatUtilizationPct = 0.0
    $endSeatUtilizationPct = 0.0
    if ($capacity -gt 0) {
        $avgSeatUtilizationPct = [math]::Round((1.0 - ($avgAvailableSeats / $capacity)) * 100.0, 2)
        $endSeatUtilizationPct = [math]::Round((1.0 - ($availableSeatsEnd / $capacity)) * 100.0, 2)
    }

    return [pscustomobject]@{
        TotalArrived = $totalArrived
        TotalServed = $totalServed
        ServedRatePct = $servedRatePct
        WaitingForSeatEnd = $waitingForSeatEnd
        AvgQueueTotal = $avgQueueTotal
        MaxQueueTotal = $maxQueueTotal
        AvgQueueWaitSecEnd = [math]::Round($avgQueueWaitSecEnd, 2)
        MaxQueueWaitSecEnd = $maxQueueWaitSecEnd
        P50QueueWaitSecEnd = [math]::Round($p50QueueWaitSecEnd, 2)
        P90QueueWaitSecEnd = [math]::Round($p90QueueWaitSecEnd, 2)
        P99QueueWaitSecEnd = [math]::Round($p99QueueWaitSecEnd, 2)
        AvgSeatUtilizationPct = $avgSeatUtilizationPct
        EndSeatUtilizationPct = $endSeatUtilizationPct
        TickCount = [int]$rows.Count
    }
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$baselineRun = Invoke-ScenarioRun -ConfigPath $BaselineConfig -Tag "baseline" -Timestamp $timestamp -RunOutputDir $runOutputDir
$stressRun = Invoke-ScenarioRun -ConfigPath $StressConfig -Tag "stress" -Timestamp $timestamp -RunOutputDir $runOutputDir

$baselineMetrics = Get-ScenarioMetrics -CsvPath $baselineRun.CsvPath -ConfigPath $baselineRun.ConfigPath
$stressMetrics = Get-ScenarioMetrics -CsvPath $stressRun.CsvPath -ConfigPath $stressRun.ConfigPath

$reportPath = Join-Path $OutputDir ("scenario_comparison_{0}.md" -f $timestamp)
$summaryPath = Join-Path $OutputDir ("scenario_comparison_{0}.json" -f $timestamp)

$reportLines = @(
    "# Scenario Comparison Report",
    "",
    "- GeneratedAt: $timestamp",
    "- BaselineConfig: $BaselineConfig",
    "- StressConfig: $StressConfig",
    "- BaselineLog: $($baselineRun.LogPath)",
    "- BaselineCsv: $($baselineRun.CsvPath)",
    "- StressLog: $($stressRun.LogPath)",
    "- StressCsv: $($stressRun.CsvPath)",
    "",
    "## KPI Comparison",
    "",
    "| Metric | Baseline | Stress | Delta (Stress - Baseline) |",
    "|---|---:|---:|---:|",
    "| TotalArrived | $($baselineMetrics.TotalArrived) | $($stressMetrics.TotalArrived) | $([int]$stressMetrics.TotalArrived - [int]$baselineMetrics.TotalArrived) |",
    "| TotalServed | $($baselineMetrics.TotalServed) | $($stressMetrics.TotalServed) | $([int]$stressMetrics.TotalServed - [int]$baselineMetrics.TotalServed) |",
    "| ServedRatePct | $($baselineMetrics.ServedRatePct)% | $($stressMetrics.ServedRatePct)% | $([math]::Round(([double]$stressMetrics.ServedRatePct - [double]$baselineMetrics.ServedRatePct), 2))% |",
    "| WaitingForSeatEnd | $($baselineMetrics.WaitingForSeatEnd) | $($stressMetrics.WaitingForSeatEnd) | $([int]$stressMetrics.WaitingForSeatEnd - [int]$baselineMetrics.WaitingForSeatEnd) |",
    "| AvgQueueTotal | $($baselineMetrics.AvgQueueTotal) | $($stressMetrics.AvgQueueTotal) | $([math]::Round(([double]$stressMetrics.AvgQueueTotal - [double]$baselineMetrics.AvgQueueTotal), 2)) |",
    "| MaxQueueTotal | $($baselineMetrics.MaxQueueTotal) | $($stressMetrics.MaxQueueTotal) | $([int]$stressMetrics.MaxQueueTotal - [int]$baselineMetrics.MaxQueueTotal) |",
    "| AvgQueueWaitSecEnd | $($baselineMetrics.AvgQueueWaitSecEnd) | $($stressMetrics.AvgQueueWaitSecEnd) | $([math]::Round(([double]$stressMetrics.AvgQueueWaitSecEnd - [double]$baselineMetrics.AvgQueueWaitSecEnd), 2)) |",
    "| MaxQueueWaitSecEnd | $($baselineMetrics.MaxQueueWaitSecEnd) | $($stressMetrics.MaxQueueWaitSecEnd) | $([int]$stressMetrics.MaxQueueWaitSecEnd - [int]$baselineMetrics.MaxQueueWaitSecEnd) |",
    "| P50QueueWaitSecEnd | $($baselineMetrics.P50QueueWaitSecEnd) | $($stressMetrics.P50QueueWaitSecEnd) | $([math]::Round(([double]$stressMetrics.P50QueueWaitSecEnd - [double]$baselineMetrics.P50QueueWaitSecEnd), 2)) |",
    "| P90QueueWaitSecEnd | $($baselineMetrics.P90QueueWaitSecEnd) | $($stressMetrics.P90QueueWaitSecEnd) | $([math]::Round(([double]$stressMetrics.P90QueueWaitSecEnd - [double]$baselineMetrics.P90QueueWaitSecEnd), 2)) |",
    "| P99QueueWaitSecEnd | $($baselineMetrics.P99QueueWaitSecEnd) | $($stressMetrics.P99QueueWaitSecEnd) | $([math]::Round(([double]$stressMetrics.P99QueueWaitSecEnd - [double]$baselineMetrics.P99QueueWaitSecEnd), 2)) |",
    "| AvgSeatUtilizationPct | $($baselineMetrics.AvgSeatUtilizationPct)% | $($stressMetrics.AvgSeatUtilizationPct)% | $([math]::Round(([double]$stressMetrics.AvgSeatUtilizationPct - [double]$baselineMetrics.AvgSeatUtilizationPct), 2))% |",
    "| EndSeatUtilizationPct | $($baselineMetrics.EndSeatUtilizationPct)% | $($stressMetrics.EndSeatUtilizationPct)% | $([math]::Round(([double]$stressMetrics.EndSeatUtilizationPct - [double]$baselineMetrics.EndSeatUtilizationPct), 2))% |"
)

Set-Content -Path $reportPath -Value $reportLines -Encoding UTF8

$summaryObj = [ordered]@{
    generatedAt = $timestamp
    baseline = [ordered]@{
        config = $BaselineConfig
        log = $baselineRun.LogPath
        csv = $baselineRun.CsvPath
        metrics = $baselineMetrics
    }
    stress = [ordered]@{
        config = $StressConfig
        log = $stressRun.LogPath
        csv = $stressRun.CsvPath
        metrics = $stressMetrics
    }
}

$summaryObj | ConvertTo-Json -Depth 6 | Set-Content -Path $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "=== Comparison Report Generated ==="
Write-Host "Markdown: $reportPath"
Write-Host "JSON: $summaryPath"
