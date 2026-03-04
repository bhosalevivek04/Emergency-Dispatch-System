# Service-Level Authorization Test Script
# Tests role-based access control for Emergency Dispatch System

Write-Host "=== Service-Level Authorization Tests ===" -ForegroundColor Cyan
Write-Host ""

$baseUrls = @{
    "emergency" = "http://localhost:8081"
    "ambulance" = "http://localhost:8082"
    "tracking" = "http://localhost:8085"
}

function Test-Endpoint {
    param(
        [string]$Service,
        [string]$Method,
        [string]$Path,
        [string]$Role,
        [int]$ExpectedStatus,
        [string]$Body = $null
    )
    
    $url = "$($baseUrls[$Service])$Path"
    $headers = @{
        "X-User-Username" = "testuser"
        "X-User-Roles" = $Role
        "Content-Type" = "application/json"
    }
    
    try {
        $params = @{
            Uri = $url
            Method = $Method
            Headers = $headers
            ErrorAction = "Stop"
        }
        
        if ($Body) {
            $params.Body = $Body
        }
        
        $response = Invoke-WebRequest @params
        $actualStatus = $response.StatusCode
    }
    catch {
        $actualStatus = $_.Exception.Response.StatusCode.value__
    }
    
    $testName = "$Method $Path with $Role"
    if ($actualStatus -eq $ExpectedStatus) {
        Write-Host "[PASS] $testName (Expected: $ExpectedStatus, Got: $actualStatus)" -ForegroundColor Green
        return $true
    }
    else {
        Write-Host "[FAIL] $testName (Expected: $ExpectedStatus, Got: $actualStatus)" -ForegroundColor Red
        return $false
    }
}

# Test counters
$passed = 0
$failed = 0

Write-Host "Testing Emergency Service Authorization..." -ForegroundColor Yellow
Write-Host ""

# Emergency Service Tests
$emergencyBody = '{"emergencyId":"EMG-TEST-001","lat":18.5204,"lon":73.8567,"priority":"HIGH"}'

# POST /emergency - DISPATCHER should succeed
if (Test-Endpoint -Service "emergency" -Method "POST" -Path "/emergency" -Role "DISPATCHER" -ExpectedStatus 200 -Body $emergencyBody) { $passed++ } else { $failed++ }

# POST /emergency - ADMIN should succeed
if (Test-Endpoint -Service "emergency" -Method "POST" -Path "/emergency" -Role "ADMIN" -ExpectedStatus 200 -Body $emergencyBody) { $passed++ } else { $failed++ }

# POST /emergency - AMBULANCE_DRIVER should fail (403)
if (Test-Endpoint -Service "emergency" -Method "POST" -Path "/emergency" -Role "AMBULANCE_DRIVER" -ExpectedStatus 403 -Body $emergencyBody) { $passed++ } else { $failed++ }

# GET /emergency/pending - DISPATCHER should succeed
if (Test-Endpoint -Service "emergency" -Method "GET" -Path "/emergency/pending" -Role "DISPATCHER" -ExpectedStatus 200) { $passed++ } else { $failed++ }

# GET /emergency/pending - AMBULANCE_DRIVER should fail (403)
if (Test-Endpoint -Service "emergency" -Method "GET" -Path "/emergency/pending" -Role "AMBULANCE_DRIVER" -ExpectedStatus 403) { $passed++ } else { $failed++ }

Write-Host ""
Write-Host "Testing Ambulance Service Authorization..." -ForegroundColor Yellow
Write-Host ""

# Ambulance Service Tests
# GET /diagnostic/fleet-status - ADMIN should succeed
if (Test-Endpoint -Service "ambulance" -Method "GET" -Path "/diagnostic/fleet-status" -Role "ADMIN" -ExpectedStatus 200) { $passed++ } else { $failed++ }

# GET /diagnostic/fleet-status - DISPATCHER should fail (403)
if (Test-Endpoint -Service "ambulance" -Method "GET" -Path "/diagnostic/fleet-status" -Role "DISPATCHER" -ExpectedStatus 403) { $passed++ } else { $failed++ }

# GET /diagnostic/fleet-status - AMBULANCE_DRIVER should fail (403)
if (Test-Endpoint -Service "ambulance" -Method "GET" -Path "/diagnostic/fleet-status" -Role "AMBULANCE_DRIVER" -ExpectedStatus 403) { $passed++ } else { $failed++ }

Write-Host ""
Write-Host "Testing Tracking Service Authorization..." -ForegroundColor Yellow
Write-Host ""

# Tracking Service Tests
# Note: GET endpoints are public for development, so we only test POST authorization
# GET /tracking/ambulances - Public (no auth required)
Write-Host "[INFO] GET /tracking/ambulances is public for development - skipping auth test" -ForegroundColor Cyan

# POST /tracking/location - AMBULANCE_DRIVER should succeed
$locationBody = '{"ambulanceId":"AMB-TEST","latitude":18.5204,"longitude":73.8567,"speed":45.5,"heading":90.0}'
if (Test-Endpoint -Service "tracking" -Method "POST" -Path "/tracking/location" -Role "AMBULANCE_DRIVER" -ExpectedStatus 200 -Body $locationBody) { $passed++ } else { $failed++ }

# POST /tracking/location - DISPATCHER should fail (403)
if (Test-Endpoint -Service "tracking" -Method "POST" -Path "/tracking/location" -Role "DISPATCHER" -ExpectedStatus 403 -Body $locationBody) { $passed++ } else { $failed++ }

# POST /tracking/location - ADMIN should succeed
if (Test-Endpoint -Service "tracking" -Method "POST" -Path "/tracking/location" -Role "ADMIN" -ExpectedStatus 200 -Body $locationBody) { $passed++ } else { $failed++ }

Write-Host ""
Write-Host "Testing Missing Headers..." -ForegroundColor Yellow
Write-Host ""

# Test missing roles header
try {
    $response = Invoke-WebRequest -Uri "$($baseUrls['emergency'])/emergency/pending" -Method GET -Headers @{"X-User-Username"="testuser"} -ErrorAction Stop
    Write-Host "[FAIL] Missing roles header should return 403 (Got: $($response.StatusCode))" -ForegroundColor Red
    $failed++
}
catch {
    $actualStatus = $_.Exception.Response.StatusCode.value__
    if ($actualStatus -eq 403) {
        Write-Host "[PASS] Missing roles header returns 403" -ForegroundColor Green
        $passed++
    }
    else {
        Write-Host "[FAIL] Missing roles header should return 403 (Got: $actualStatus)" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red
Write-Host "Total: $($passed + $failed)"
Write-Host ""

if ($failed -eq 0) {
    Write-Host "All tests passed! ✓" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "Some tests failed! ✗" -ForegroundColor Red
    exit 1
}
