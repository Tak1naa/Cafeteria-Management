param(
    [int]$Port = 18080
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ContractSchemaVersion = "v1"

function Read-RequestBody {
    param([System.Net.HttpListenerRequest]$Request)
    $reader = New-Object System.IO.StreamReader($Request.InputStream, $Request.ContentEncoding)
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Close()
    }
}

function Write-JsonResponse {
    param(
        [System.Net.HttpListenerContext]$Context,
        [int]$StatusCode,
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 8 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

    $Context.Response.StatusCode = $StatusCode
    $Context.Response.ContentType = "application/json"
    $Context.Response.ContentEncoding = [System.Text.Encoding]::UTF8
    $Context.Response.ContentLength64 = $bytes.Length
    $Context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Context.Response.OutputStream.Close()
}

function Get-RequiredProperty {
    param(
        [object]$Object,
        [string]$Name
    )

    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop) {
        return $null
    }
    return $prop.Value
}

function Try-ReadNonNegativeInt {
    param(
        [object]$Value,
        [ref]$Result
    )

    try {
        $intValue = [int]$Value
    } catch {
        return $false
    }

    if ($intValue -lt 0) {
        return $false
    }

    $Result.Value = $intValue
    return $true
}

function Try-ReadQueueLengths {
    param(
        [object]$Value,
        [ref]$Result
    )

    if ($null -eq $Value -or $Value -isnot [System.Collections.IEnumerable]) {
        return $false
    }

    $array = @()
    foreach ($item in $Value) {
        $q = 0
        if (-not (Try-ReadNonNegativeInt -Value $item -Result ([ref]$q))) {
            return $false
        }
        $array += $q
    }

    if ($array.Count -le 0) {
        return $false
    }

    $Result.Value = $array
    return $true
}

function Validate-SimulationDataPayload {
    param([object]$Data)

    if ($null -eq $Data) {
        return "body must be valid JSON object"
    }

    $incomingSchemaVersion = Get-RequiredProperty -Object $Data -Name "schemaVersion"
    if ($incomingSchemaVersion -ne $ContractSchemaVersion) {
        return "schemaVersion must be '$ContractSchemaVersion'"
    }

    $queueLengthsObj = Get-RequiredProperty -Object $Data -Name "queueLengths"
    $queueLengths = @()
    if (-not (Try-ReadQueueLengths -Value $queueLengthsObj -Result ([ref]$queueLengths))) {
        return "queueLengths must be non-empty int array with non-negative values"
    }

    $windowCountObj = Get-RequiredProperty -Object $Data -Name "windowCount"
    $windowCount = 0
    if (-not (Try-ReadNonNegativeInt -Value $windowCountObj -Result ([ref]$windowCount))) {
        return "windowCount must be non-negative int"
    }
    if ($windowCount -ne $queueLengths.Count) {
        return "windowCount must equal queueLengths length"
    }

    foreach ($field in @("simTime", "availableSeats", "waitingForSeat", "totalArrived", "totalServed", "totalSeated", "totalFinishedDining", "newArrivals")) {
        $v = Get-RequiredProperty -Object $Data -Name $field
        $tmp = 0
        if (-not (Try-ReadNonNegativeInt -Value $v -Result ([ref]$tmp))) {
            return "$field must be non-negative int"
        }
    }

    return $null
}

function Validate-DecisionRequestPayload {
    param([object]$Data)

    if ($null -eq $Data) {
        return "body must be valid JSON object"
    }

    $incomingSchemaVersion = Get-RequiredProperty -Object $Data -Name "schemaVersion"
    if ($incomingSchemaVersion -ne $ContractSchemaVersion) {
        return "schemaVersion must be '$ContractSchemaVersion'"
    }

    $queueLengthsObj = Get-RequiredProperty -Object $Data -Name "queueLengths"
    $queueLengths = @()
    if (-not (Try-ReadQueueLengths -Value $queueLengthsObj -Result ([ref]$queueLengths))) {
        return "queueLengths must be non-empty int array with non-negative values"
    }

    $windowCountObj = Get-RequiredProperty -Object $Data -Name "windowCount"
    $windowCount = 0
    if (-not (Try-ReadNonNegativeInt -Value $windowCountObj -Result ([ref]$windowCount))) {
        return "windowCount must be non-negative int"
    }
    if ($windowCount -ne $queueLengths.Count) {
        return "windowCount must equal queueLengths length"
    }

    foreach ($field in @("simTime", "availableSeats", "waitingForSeat", "newArrivals")) {
        $v = Get-RequiredProperty -Object $Data -Name $field
        $tmp = 0
        if (-not (Try-ReadNonNegativeInt -Value $v -Result ([ref]$tmp))) {
            return "$field must be non-negative int"
        }
    }

    return $null
}

function Build-MockAllocation {
    param(
        [int[]]$QueueLengths,
        [int]$NewArrivals
    )

    $windowCount = $QueueLengths.Count
    if ($windowCount -le 0) {
        return @()
    }

    $allocation = New-Object int[] $windowCount
    if ($NewArrivals -le 0) {
        return $allocation
    }

    $projected = @()
    foreach ($q in $QueueLengths) {
        $projected += [int]$q
    }

    for ($i = 0; $i -lt $NewArrivals; $i++) {
        $bestIndex = 0
        $bestValue = $projected[0]

        for ($j = 1; $j -lt $projected.Count; $j++) {
            if ($projected[$j] -lt $bestValue) {
                $bestValue = $projected[$j]
                $bestIndex = $j
            }
        }

        $allocation[$bestIndex] = $allocation[$bestIndex] + 1
        $projected[$bestIndex] = $projected[$bestIndex] + 1
    }

    return $allocation
}

$listener = [System.Net.HttpListener]::new()
$prefix = "http://127.0.0.1:$Port/"
$listener.Prefixes.Add($prefix)
$listener.Start()

Write-Host "Mock AI server started at $prefix"

try {
    while ($listener.IsListening) {
        try {
            $context = $listener.GetContext()
        } catch {
            break
        }

        $request = $context.Request
        $path = $request.Url.AbsolutePath.ToLowerInvariant()
        $method = $request.HttpMethod.ToUpperInvariant()

        try {
            if ($path -eq "/health" -and $method -eq "GET") {
                Write-JsonResponse -Context $context -StatusCode 200 -Body @{ status = "ok"; service = "mock_ai"; schemaVersion = $ContractSchemaVersion }
                Write-Host "$method $path => 200"
                continue
            }

            if ($path -eq "/api/simulation/data" -and $method -eq "POST") {
                $body = Read-RequestBody -Request $request
                $data = $body | ConvertFrom-Json
                $validationError = Validate-SimulationDataPayload -Data $data
                if ($null -ne $validationError) {
                    Write-JsonResponse -Context $context -StatusCode 400 -Body @{
                        error = "validation_failed"
                        schemaVersion = $ContractSchemaVersion
                        message = $validationError
                    }
                    Write-Host "$method $path => 400 ($validationError)"
                    continue
                }

                Write-JsonResponse -Context $context -StatusCode 200 -Body @{
                    accepted = $true
                    schemaVersion = $ContractSchemaVersion
                    receivedAt = (Get-Date).ToString("o")
                }
                Write-Host "$method $path => 200"
                continue
            }

            if ($path -eq "/api/ai/decision" -and $method -eq "POST") {
                $body = Read-RequestBody -Request $request
                $data = $body | ConvertFrom-Json

                $validationError = Validate-DecisionRequestPayload -Data $data
                if ($null -ne $validationError) {
                    Write-JsonResponse -Context $context -StatusCode 400 -Body @{
                        error = "validation_failed"
                        schemaVersion = $ContractSchemaVersion
                        message = $validationError
                    }
                    Write-Host "$method $path => 400 ($validationError)"
                    continue
                }

                $queueLengths = @()
                if ($null -ne $data.queueLengths) {
                    foreach ($item in $data.queueLengths) {
                        $queueLengths += [int]$item
                    }
                }

                $newArrivals = 0
                if ($null -ne $data.newArrivals) {
                    $newArrivals = [int]$data.newArrivals
                }

                $allocation = Build-MockAllocation -QueueLengths $queueLengths -NewArrivals $newArrivals
                Write-JsonResponse -Context $context -StatusCode 200 -Body @{
                    allocation = $allocation
                    source = "mock_ai_server"
                    decisionMode = "AI"
                    schemaVersion = $ContractSchemaVersion
                }
                Write-Host "$method $path => 200"
                continue
            }

            Write-JsonResponse -Context $context -StatusCode 404 -Body @{ error = "not_found"; path = $path }
            Write-Host "$method $path => 404"
        } catch {
            Write-JsonResponse -Context $context -StatusCode 500 -Body @{ error = "internal_error"; message = $_.Exception.Message; schemaVersion = $ContractSchemaVersion }
            Write-Host "$method $path => 500 ($($_.Exception.Message))"
        }
    }
} finally {
    if ($listener.IsListening) {
        $listener.Stop()
    }
    $listener.Close()
    Write-Host "Mock AI server stopped"
}
