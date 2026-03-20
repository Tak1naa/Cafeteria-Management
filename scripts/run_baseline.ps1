param(
    [string]$ConfigPath = "config/scenarios/baseline_seeded.json",
    [string]$OutputDir = "build/baseline"
)

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

if (!(Test-Path "build/cafeteria_sim.exe")) {
    Write-Host "Executable not found, building with g++..."
    g++ -std=c++17 -Iinclude src/main.cpp src/person_generator.cpp src/window_queue.cpp src/table_matrix.cpp src/decision_client.cpp src/simulation_engine.cpp -lwinhttp -o build/cafeteria_sim.exe
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed"
    }
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logPath = Join-Path $OutputDir "run_$timestamp.log"
$csvPath = Join-Path $OutputDir "step_data_$timestamp.csv"

Write-Host "Running scenario with config: $ConfigPath"
.\build\cafeteria_sim.exe $ConfigPath | Tee-Object -FilePath $logPath

if (Test-Path "build/step_data.csv") {
    Copy-Item "build/step_data.csv" $csvPath -Force
    Write-Host "Step CSV copied to: $csvPath"
}

if (!(Test-Path $csvPath)) {
    Write-Host "No CSV output found, skip summary."
    exit 0
}

$rows = Import-Csv $csvPath
if ($rows.Count -eq 0) {
    Write-Host "CSV is empty, skip summary."
    exit 0
}

$configObj = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$capacity = [int]$configObj.tableRows * [int]$configObj.tableCols
$last = $rows | Select-Object -Last 1
$utilization = 0
if ($capacity -gt 0) {
    $utilization = [math]::Round((1.0 - ([double]$last.availableSeats / [double]$capacity)) * 100.0, 2)
}

Write-Host ""
Write-Host "=== Baseline Summary ==="
Write-Host "Total arrived: $($last.totalArrived)"
Write-Host "Total served: $($last.totalServed)"
Write-Host "Waiting for seat(end): $($last.waitingForSeat)"
Write-Host "Seat utilization(end): $utilization%"
Write-Host "Log file: $logPath"
Write-Host "CSV file: $csvPath"
