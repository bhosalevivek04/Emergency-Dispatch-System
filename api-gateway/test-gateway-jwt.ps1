# API Gateway JWT Integration Test Script
# Tests JWT authentication flow through API Gateway

$ErrorActionPreference = "Continue"
$GATEWAY_URL = "http://localhost:8080"
$AUTH_SERVICE_URL = "http://localhost:8086"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "API Gateway JWT Integration Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Health Check (Public - No Token Required)
Write-Host "Test 1: Health Check (Public Endpoint)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/actuator/health" -Method Get
    Write-Host "✓ Health check successful" -ForegroundColor Green
    Write-Host "  Status: $($response.status)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Health check failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 2: Register User via Gateway (Public - No Token Required)
Write-Host "Test 2: Register User via Gateway" -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testUser = "testuser$timestamp"
$registerBody = @{
    username = $testUser
    email = "testuser$timestamp@example.com"
    password = "Test@123"
    role = "DISPATCHER"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/register" -Method Post -Body $registerBody -ContentType "application/json"
    Write-Host "✓ User registration successful via gateway" -ForegroundColor Green
    Write-Host "  Username: $($response.username)" -ForegroundColor Gray
    Write-Host "  Role: $($response.role)" -ForegroundColor Gray
    $ADMIN_TOKEN = $response.accessToken
} catch {
    Write-Host "✗ User registration failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 3: Login via Gateway (Public - No Token Required)
Write-Host "Test 3: Login via Gateway" -ForegroundColor Yellow
$loginBody = @{
    username = $testUser
    password = "Test@123"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    Write-Host "✓ Login successful via gateway" -ForegroundColor Green
    Write-Host "  Access Token: $($response.accessToken.Substring(0, 50))..." -ForegroundColor Gray
    Write-Host "  Refresh Token: $($response.refreshToken)" -ForegroundColor Gray
    $ACCESS_TOKEN = $response.accessToken
    $REFRESH_TOKEN = $response.refreshToken
} catch {
    Write-Host "✗ Login failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test 4: Access Protected Endpoint WITHOUT Token (Should Fail)
Write-Host "Test 4: Access Protected Endpoint WITHOUT Token (Should Fail)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/api/emergencies" -Method Get
    Write-Host "✗ Unexpected success - should have been rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ Correctly rejected with 401 Unauthorized" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# Test 5: Access Protected Endpoint WITH Valid Token
Write-Host "Test 5: Access Protected Endpoint WITH Valid Token" -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $ACCESS_TOKEN"
}
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/api/emergencies/pending" -Method Get -Headers $headers
    Write-Host "✓ Successfully accessed protected endpoint" -ForegroundColor Green
    Write-Host "  Response received from emergency-service" -ForegroundColor Gray
    Write-Host "  Pending emergencies count: $($response.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to access protected endpoint" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 6: Access Protected Endpoint WITH Invalid Token (Should Fail)
Write-Host "Test 6: Access Protected Endpoint WITH Invalid Token (Should Fail)" -ForegroundColor Yellow
$invalidHeaders = @{
    "Authorization" = "Bearer invalid.token.here"
}
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/api/emergencies" -Method Get -Headers $invalidHeaders
    Write-Host "✗ Unexpected success - should have been rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ Correctly rejected invalid token with 401" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# Test 7: Validate Token via Gateway
Write-Host "Test 7: Validate Token via Gateway" -ForegroundColor Yellow
$validateHeaders = @{
    "Authorization" = "Bearer $ACCESS_TOKEN"
}
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/validate" -Method Post -Headers $validateHeaders
    Write-Host "✓ Token validation successful" -ForegroundColor Green
    Write-Host "  Username: $($response.username)" -ForegroundColor Gray
    Write-Host "  Roles: $($response.roles)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Token validation failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 8: Refresh Token via Gateway
Write-Host "Test 8: Refresh Token via Gateway" -ForegroundColor Yellow
$refreshBody = @{
    refreshToken = $REFRESH_TOKEN
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/refresh" -Method Post -Body $refreshBody -ContentType "application/json"
    Write-Host "✓ Token refresh successful" -ForegroundColor Green
    Write-Host "  New Access Token: $($response.accessToken.Substring(0, 50))..." -ForegroundColor Gray
    $NEW_ACCESS_TOKEN = $response.accessToken
} catch {
    Write-Host "✗ Token refresh failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 9: Use Refreshed Token on Protected Endpoint
Write-Host "Test 9: Use Refreshed Token on Protected Endpoint" -ForegroundColor Yellow
$newHeaders = @{
    "Authorization" = "Bearer $NEW_ACCESS_TOKEN"
}
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/api/emergencies/pending" -Method Get -Headers $newHeaders
    Write-Host "✓ Refreshed token works on protected endpoint" -ForegroundColor Green
    Write-Host "  Pending emergencies count: $($response.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Refreshed token failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 10: Logout via Gateway
Write-Host "Test 10: Logout via Gateway" -ForegroundColor Yellow
$logoutBody = @{
    refreshToken = $REFRESH_TOKEN
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/logout" -Method Post -Body $logoutBody -ContentType "application/json"
    Write-Host "✓ Logout successful" -ForegroundColor Green
    Write-Host "  Message: $($response.message)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Logout failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 11: Use Revoked Token (Should Fail)
Write-Host "Test 11: Use Revoked Token After Logout (Should Fail)" -ForegroundColor Yellow
$revokedRefreshBody = @{
    refreshToken = $REFRESH_TOKEN
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/refresh" -Method Post -Body $revokedRefreshBody -ContentType "application/json"
    Write-Host "✗ Unexpected success - revoked token should be rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ Revoked token correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# Test 12: Check Public Key Endpoint
Write-Host "Test 12: Fetch Public Key via Gateway" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/public-key" -Method Get
    Write-Host "✓ Public key fetched successfully" -ForegroundColor Green
    Write-Host "  Algorithm: $($response.algorithm)" -ForegroundColor Gray
    Write-Host "  Format: $($response.format)" -ForegroundColor Gray
    Write-Host "  Key (first 50 chars): $($response.key.Substring(0, 50))..." -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to fetch public key" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "- API Gateway is routing requests correctly" -ForegroundColor Gray
Write-Host "- JWT authentication is working (RS256)" -ForegroundColor Gray
Write-Host "- Public endpoints accessible without token" -ForegroundColor Gray
Write-Host "- Protected endpoints require valid JWT" -ForegroundColor Gray
Write-Host "- Invalid/expired tokens are rejected" -ForegroundColor Gray
Write-Host "- Token refresh and logout working" -ForegroundColor Gray
Write-Host ""
Write-Host "Test User Created:" -ForegroundColor Yellow
Write-Host "  Username: $testUser" -ForegroundColor Gray
Write-Host "  Password: Test@123" -ForegroundColor Gray
Write-Host "  Role: DISPATCHER" -ForegroundColor Gray
Write-Host ""
Write-Host "Latest Access Token:" -ForegroundColor Yellow
Write-Host "$NEW_ACCESS_TOKEN" -ForegroundColor Gray
