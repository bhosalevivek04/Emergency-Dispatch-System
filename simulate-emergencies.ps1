param(
    [string]$BaseUrl = "http://localhost:8081/emergency",
    [int]$Count = 50,
    [int]$DelayMs = 200,
    [string]$IdPrefix = "ELOAD",
    [double]$CenterLat = 18.5204,
    [double]$CenterLon = 73.8567,
    [double]$Spread = 0.02,
    [switch]$Parallel,
    [int]$Throttle = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-RandomPriority {
    $roll = Get-Random -Minimum 1 -Maximum 101
    if ($roll -le 20) { return "HIGH" }
    if ($roll -le 60) { return "MEDIUM" }
    return "LOW"
}

function New-RandomCoordinate([double]$center, [double]$spread) {
    $offset = (Get-Random -Minimum -10000 -Maximum 10001) / 10000.0
    return [math]::Round($center + ($offset * $spread), 6)
}

function New-EmergencyPayload([int]$index) {
    $id = "{0}{1:00000}" -f $IdPrefix, $index
    return [ordered]@{
        emergencyId = $id
        lat = New-RandomCoordinate -center $CenterLat -spread $Spread
        lon = New-RandomCoordinate -center $CenterLon -spread $Spread
        priority = New-RandomPriority
    }
}

function Send-Emergency([hashtable]$payload) {
    $json = $payload | ConvertTo-Json -Depth 3
    $start = Get-Date
    try {
        $response = Invoke-RestMethod -Method Post -Uri $BaseUrl -ContentType "application/json" -Body $json
        $elapsedMs = [int](((Get-Date) - $start).TotalMilliseconds)
        return [pscustomobject]@{ emergencyId=$payload.emergencyId; priority=$payload.priority; status='OK'; elapsedMs=$elapsedMs; response=[string]$response; error='' }
    }
    catch {
        $elapsedMs = [int](((Get-Date) - $start).TotalMilliseconds)
        return [pscustomobject]@{ emergencyId=$payload.emergencyId; priority=$payload.priority; status='FAIL'; elapsedMs=$elapsedMs; response=''; error=$_.Exception.Message }
    }
}

Write-Host "Starting emergency simulation..."
Write-Host "BaseUrl=$BaseUrl Count=$Count DelayMs=$DelayMs Parallel=$Parallel Throttle=$Throttle"

$results=@()
if ($Parallel) {
    $jobs=@()
    for($i=1;$i -le $Count;$i++){
        $payload = New-EmergencyPayload -index $i
        while((@($jobs | Where-Object {$_.State -eq 'Running'}).Count) -ge $Throttle){ Start-Sleep -Milliseconds 100 }
        $jobs += Start-Job -ScriptBlock {
            param($baseUrl, $payloadObj)
            $json = $payloadObj | ConvertTo-Json -Depth 3
            $start = Get-Date
            try {
                $response = Invoke-RestMethod -Method Post -Uri $baseUrl -ContentType "application/json" -Body $json
                $elapsedMs = [int](((Get-Date) - $start).TotalMilliseconds)
                [pscustomobject]@{ emergencyId=$payloadObj.emergencyId; priority=$payloadObj.priority; status='OK'; elapsedMs=$elapsedMs; response=[string]$response; error='' }
            } catch {
                $elapsedMs = [int](((Get-Date) - $start).TotalMilliseconds)
                [pscustomobject]@{ emergencyId=$payloadObj.emergencyId; priority=$payloadObj.priority; status='FAIL'; elapsedMs=$elapsedMs; response=''; error=$_.Exception.Message }
            }
        } -ArgumentList $BaseUrl, $payload
        if($DelayMs -gt 0){ Start-Sleep -Milliseconds $DelayMs }
    }
    $results = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job -Force
} else {
    for($i=1;$i -le $Count;$i++){
        $payload = New-EmergencyPayload -index $i
        $result = Send-Emergency -payload $payload
        $results += $result
        if($result.status -eq 'OK'){
            Write-Host "[$i/$Count] OK   $($result.emergencyId) $($result.priority) $($result.elapsedMs)ms"
        } else {
            Write-Host "[$i/$Count] FAIL $($result.emergencyId) $($result.priority) $($result.elapsedMs)ms -> $($result.error)"
        }
        if($DelayMs -gt 0){ Start-Sleep -Milliseconds $DelayMs }
    }
}

$ok=@($results | Where-Object {$_.status -eq 'OK'}).Count
$fail=@($results | Where-Object {$_.status -eq 'FAIL'}).Count
$avg=0
if($results.Count -gt 0){ $avg=[math]::Round((($results | Measure-Object -Property elapsedMs -Average).Average),2) }
Write-Host ""
Write-Host "Simulation complete"
Write-Host "Total=$($results.Count) OK=$ok FAIL=$fail AvgLatencyMs=$avg"
$timestamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile="simulate-emergencies-result-$timestamp.csv"
$results | Export-Csv -Path $outFile -NoTypeInformation
Write-Host "Detailed result file: $outFile"
