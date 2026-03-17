param(
    [int]$Port = 18080,
    [string]$OutputDir = "build/api_contract"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body
    )

    $payload = $Body | ConvertTo-Json -Depth 8

    try {
        $resp = Invoke-WebRequest -Uri $Url -Method Post -ContentType "application/json" -Body $payload -TimeoutSec 3 -UseBasicParsing
        return [pscustomobject]@{
            status = [int]$resp.StatusCode
            content = $resp.Content
        }
    } catch {
        $exception = $_.Exception
        if ($null -ne $exception.Response) {
            $statusCode = [int]$exception.Response.StatusCode
            $stream = $exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            try {
                $content = $reader.ReadToEnd()
            } finally {
                $reader.Close()
            }

            return [pscustomobject]@{
                status = $statusCode
                content = $content
            }
        }

        throw
    }
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$serverStdoutLog = Join-Path $OutputDir "mock_api_server_$timestamp.out.log"
$serverStderrLog = Join-Path $OutputDir "mock_api_server_$timestamp.err.log"
$summaryPath = Join-Path $OutputDir "api_contract_summary_$timestamp.json"

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
        throw "Mock API server failed health check"
    }

    $validState = @{
        schemaVersion = "v1"
        simTime = 10
        queueLengths = @(2, 3, 1, 0)
        windowCount = 4
        availableSeats = 30
        waitingForSeat = 1
        totalArrived = 50
        totalServed = 40
        totalSeated = 39
        totalFinishedDining = 20
        newArrivals = 4
    }

    $validDecision = @{
        schemaVersion = "v1"
        simTime = 10
        queueLengths = @(2, 3, 1, 0)
        windowCount = 4
        availableSeats = 30
        waitingForSeat = 1
        newArrivals = 4
    }

    $invalidSchema = @{
        schemaVersion = "v999"
        simTime = 10
        queueLengths = @(1, 2, 3, 4)
        windowCount = 4
        availableSeats = 30
        waitingForSeat = 1
        newArrivals = 4
    }

    $invalidWindowCount = @{
        schemaVersion = "v1"
        simTime = 10
        queueLengths = @(1, 2, 3, 4)
        windowCount = 3
        availableSeats = 30
        waitingForSeat = 1
        newArrivals = 4
    }

    $cases = @(
        [pscustomobject]@{ name = "state_valid"; url = "http://127.0.0.1:$Port/api/simulation/data"; body = $validState; expectedStatus = 200 },
        [pscustomobject]@{ name = "decision_valid"; url = "http://127.0.0.1:$Port/api/ai/decision"; body = $validDecision; expectedStatus = 200 },
        [pscustomobject]@{ name = "decision_invalid_schema"; url = "http://127.0.0.1:$Port/api/ai/decision"; body = $invalidSchema; expectedStatus = 400 },
        [pscustomobject]@{ name = "decision_invalid_window_count"; url = "http://127.0.0.1:$Port/api/ai/decision"; body = $invalidWindowCount; expectedStatus = 400 }
    )

    $results = @()
    foreach ($case in $cases) {
        $response = Invoke-JsonPost -Url $case.url -Body $case.body
        $passed = $response.status -eq $case.expectedStatus
        $results += [pscustomobject]@{
            name = $case.name
            expectedStatus = $case.expectedStatus
            actualStatus = $response.status
            passed = $passed
            response = $response.content
        }

        if (-not $passed) {
            throw "Contract test failed on case '$($case.name)': expected $($case.expectedStatus), actual $($response.status)"
        }
    }

    $summary = [pscustomobject]@{
        timestamp = $timestamp
        port = $Port
        serverStdoutLog = $serverStdoutLog
        serverStderrLog = $serverStderrLog
        caseCount = $results.Count
        passedCount = (@($results | Where-Object { $_.passed }).Count)
        results = $results
    }

    $summary | ConvertTo-Json -Depth 8 | Set-Content -Path $summaryPath -Encoding UTF8

    Write-Host ""
    Write-Host "=== API Contract Test Passed ==="
    Write-Host "Cases: $($results.Count)"
    Write-Host "Summary: $summaryPath"
    Write-Host "Server stdout log: $serverStdoutLog"
    Write-Host "Server stderr log: $serverStderrLog"
} finally {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id -Force
    }
}
