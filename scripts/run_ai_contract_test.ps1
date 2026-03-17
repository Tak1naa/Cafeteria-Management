param(
    [string]$ConfigPath = "config/scenarios/ai_mock_seeded.json",
    [int]$Port = 18080,
    [string]$OutputDir = "build/ai_contract"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$simExePath = "build/cafeteria_sim.exe"

if (!(Test-Path $simExePath)) {
    Write-Host "Executable not found, building with g++ + WinHTTP fallback..."
    g++ -std=c++17 -Iinclude src/main.cpp src/person_generator.cpp src/window_queue.cpp src/table_matrix.cpp src/decision_client.cpp src/simulation_engine.cpp -lwinhttp -o $simExePath
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed: g++ compile/link failed (expected WinHTTP library on Windows)"
    }
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$serverStdoutLog = Join-Path $OutputDir "mock_ai_server_$timestamp.out.log"
$serverStderrLog = Join-Path $OutputDir "mock_ai_server_$timestamp.err.log"
$simLog = Join-Path $OutputDir "ai_contract_run_$timestamp.log"
$tempConfigPath = Join-Path $OutputDir "ai_contract_config_$timestamp.json"

$sourceConfig = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$sourceConfig.aiEnabled = $true
$sourceConfig.backendBaseUrl = "http://127.0.0.1:$Port"
if ($null -eq $sourceConfig.randomSeed) {
    $sourceConfig | Add-Member -NotePropertyName randomSeed -NotePropertyValue 20260317
} else {
    $sourceConfig.randomSeed = 20260317
}
if ($null -eq $sourceConfig.stepRecordToFile) {
    $sourceConfig | Add-Member -NotePropertyName stepRecordToFile -NotePropertyValue $true
} else {
    $sourceConfig.stepRecordToFile = $true
}
$sourceConfig.stepRecordFilePath = "build/ai_contract/step_data_$timestamp.csv"

$sourceConfig | ConvertTo-Json -Depth 8 | Set-Content -Path $tempConfigPath -Encoding UTF8

$serverProcess = $null
try {
    $serverProcess = Start-Process -FilePath "powershell" -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/mock_ai_server.ps1",
        "-Port", "$Port"
    ) -PassThru -RedirectStandardOutput $serverStdoutLog -RedirectStandardError $serverStderrLog

    $healthOk = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 250
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -Method Get -TimeoutSec 1
            if ($null -ne $health -and $health.status -eq "ok") {
                $healthOk = $true
                break
            }
        } catch {
        }
    }

    if (-not $healthOk) {
        throw "Mock AI server failed health check"
    }

    Write-Host "Running AI contract simulation with config: $tempConfigPath"
    $simOutput = & $simExePath $tempConfigPath 2>&1
    $simOutput | Set-Content -Path $simLog -Encoding UTF8

    $aiLines = @($simOutput | Select-String -Pattern "mode=AI")
    $httpFailedLines = @($simOutput | Select-String -Pattern "reason=http_failed")
    $invalidRespLines = @($simOutput | Select-String -Pattern "reason=invalid_ai_response|reason=ai_allocation_mismatch|reason=invalid_allocation_sanitized")

    if ($aiLines.Count -le 0) {
        throw "Contract test failed: no AI decisions were applied"
    }
    if ($httpFailedLines.Count -gt 0) {
        throw "Contract test failed: http_failed detected"
    }
    if ($invalidRespLines.Count -gt 0) {
        throw "Contract test failed: invalid AI allocation detected"
    }

    $summary = [pscustomobject]@{
        timestamp = $timestamp
        configPath = $tempConfigPath
        aiDecisionLineCount = $aiLines.Count
        serverStdoutLog = $serverStdoutLog
        serverStderrLog = $serverStderrLog
        simulationLog = $simLog
        stepDataCsv = $sourceConfig.stepRecordFilePath
    }

    $summaryPath = Join-Path $OutputDir "ai_contract_summary_$timestamp.json"
    $summary | ConvertTo-Json -Depth 4 | Set-Content -Path $summaryPath -Encoding UTF8

    Write-Host ""
    Write-Host "=== AI Contract Test Passed ==="
    Write-Host "AI decision lines: $($aiLines.Count)"
    Write-Host "Server stdout log: $serverStdoutLog"
    Write-Host "Server stderr log: $serverStderrLog"
    Write-Host "Simulation log: $simLog"
    Write-Host "Summary JSON: $summaryPath"
    Write-Host "Step CSV: $($sourceConfig.stepRecordFilePath)"
} finally {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id -Force
    }
}
