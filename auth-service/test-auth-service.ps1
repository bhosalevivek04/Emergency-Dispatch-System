# Auth Service Manual Testing Script (PowerShell)
# This script tests all auth-service endpoints

$BaseUrl = "http://localhost:8086"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Auth Service Testing Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Register Admin User
Write-Host "Test 1: Register Admin User" -ForegroundColor Yellow
$registerBody = @{
    username = "admin1"
    email = "admin@example.com"
    password = "Admin@123"
    role = "ADMIN"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $registerBody -ContentType "application/json"
    Write-Host "✓ Admin registration successful" -ForegroundColor Green
    $adminAccessToken = $registerResponse.accessToken
    $adminRefreshToken = $registerResponse.refreshToken
    Write-Host "Access Token: $($adminAccessToken.Substring(0, 50))..."
    Write-Host "Refresh Token: $adminRefreshToken"
} catch {
    Write-Host "✗ Admin registration failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

# Test 2: Register Dispatcher User
Write-Host "Test 2: Register Dispatcher User" -ForegroundColor Yellow
$dispatcherBody = @{
    username = "dispatcher1"
    email = "dispatcher@example.com"
    password = "Dispatch@123"
    role = "DISPATCHER"
} | ConvertTo-Json

try {
    $dispatcherResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $dispatcherBody -ContentType "application/json"
    Write-Host "✓ Dispatcher registration successful" -ForegroundColor Green
    $dispatcherAccessToken = $dispatcherResponse.accessToken
    Write-Host "Access Token: $($dispatcherAccessToken.Substring(0, 50))..."
} catch {
    Write-Host "✗ Dispatcher registration failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

# Test 3: Register Ambulance Driver
Write-Host "Test 3: Register Ambulance Driver" -ForegroundColor Yellow
$driverBody = @{
    username = "driver1"
    email = "driver@example.com"
    password = "Driver@123"
    role = "AMBULANCE_DRIVER"
} | ConvertTo-Json

try {
    $driverResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $driverBody -ContentType "application/json"
    Write-Host "✓ Ambulance driver registration successful" -ForegroundColor Green
    $driverAccessToken = $driverResponse.accessToken
    Write-Host "Access Token: $($driverAccessToken.Substring(0, 50))..."
} catch {
    Write-Host "✗ Ambulance driver registration failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

# Test 4: Duplicate Username (Should Fail)
Write-Host "Test 4: Duplicate Username (Should Fail)" -ForegroundColor Yellow
$duplicateBody = @{
    username = "admin1"
    email = "admin2@example.com"
    password = "Admin@123"
    role = "ADMIN"
} | ConvertTo-Json

try {
    $duplicateResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $duplicateBody -ContentType "application/json"
    Write-Host "✗ Duplicate username not rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Message -match "already exists") {
        Write-Host "✓ Duplicate username correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
}
Write-Host ""

# Test 5: Login with Valid Credentials
Write-Host "Test 5: Login with Valid Credentials" -ForegroundColor Yellow
$loginBody = @{
    username = "admin1"
    password = "Admin@123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    Write-Host "✓ Login successful" -ForegroundColor Green
    $loginAccessToken = $loginResponse.accessToken
    $loginRefreshToken = $loginResponse.refreshToken
    Write-Host "New Access Token: $($loginAccessToken.Substring(0, 50))..."
} catch {
    Write-Host "✗ Login failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

# Test 6: Login with Invalid Password (Should Fail)
Write-Host "Test 6: Login with Invalid Password (Should Fail)" -ForegroundColor Yellow
$invalidLoginBody = @{
    username = "admin1"
    password = "WrongPassword"
} | ConvertTo-Json

try {
    $invalidLoginResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $invalidLoginBody -ContentType "application/json"
    Write-Host "✗ Invalid password not rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Message -match "Invalid username or password") {
        Write-Host "✓ Invalid password correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
}
Write-Host ""

# Test 7: Validate Token
Write-Host "Test 7: Validate Token" -ForegroundColor Yellow
if ($adminAccessToken) {
    try {
        $headers = @{
            "Authorization" = "Bearer $adminAccessToken"
        }
        $validateResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/validate" -Method Post -Headers $headers
        
        if ($validateResponse.valid -eq $true) {
            Write-Host "✓ Token validation successful" -ForegroundColor Green
            Write-Host "Username: $($validateResponse.username)"
            Write-Host "Roles: $($validateResponse.roles)"
        } else {
            Write-Host "✗ Token validation failed" -ForegroundColor Red
        }
    } catch {
        Write-Host "✗ Token validation error" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
} else {
    Write-Host "✗ No token available for validation" -ForegroundColor Red
}
Write-Host ""

# Test 8: Validate Invalid Token (Should Fail)
Write-Host "Test 8: Validate Invalid Token (Should Fail)" -ForegroundColor Yellow
try {
    $headers = @{
        "Authorization" = "Bearer invalid.token.here"
    }
    $invalidTokenResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/validate" -Method Post -Headers $headers
    
    if ($invalidTokenResponse.valid -eq $false) {
        Write-Host "✓ Invalid token correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Invalid token not rejected" -ForegroundColor Red
    }
} catch {
    Write-Host "✓ Invalid token correctly rejected" -ForegroundColor Green
}
Write-Host ""

# Test 9: Refresh Token
Write-Host "Test 9: Refresh Token" -ForegroundColor Yellow
if ($adminRefreshToken) {
    $refreshBody = @{
        refreshToken = $adminRefreshToken
    } | ConvertTo-Json
    
    try {
        $refreshResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/refresh" -Method Post -Body $refreshBody -ContentType "application/json"
        Write-Host "✓ Token refresh successful" -ForegroundColor Green
        Write-Host "New Access Token: $($refreshResponse.accessToken.Substring(0, 50))..."
    } catch {
        Write-Host "✗ Token refresh failed" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
} else {
    Write-Host "✗ No refresh token available" -ForegroundColor Red
}
Write-Host ""

# Test 10: Logout
Write-Host "Test 10: Logout" -ForegroundColor Yellow
if ($loginRefreshToken) {
    $logoutBody = @{
        refreshToken = $loginRefreshToken
    } | ConvertTo-Json
    
    try {
        $logoutResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/logout" -Method Post -Body $logoutBody -ContentType "application/json"
        Write-Host "✓ Logout successful" -ForegroundColor Green
    } catch {
        Write-Host "✗ Logout failed" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
} else {
    Write-Host "✗ No refresh token available for logout" -ForegroundColor Red
}
Write-Host ""

# Test 11: Use Revoked Token (Should Fail)
Write-Host "Test 11: Use Revoked Token (Should Fail)" -ForegroundColor Yellow
if ($loginRefreshToken) {
    $revokedBody = @{
        refreshToken = $loginRefreshToken
    } | ConvertTo-Json
    
    try {
        $revokedResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/refresh" -Method Post -Body $revokedBody -ContentType "application/json"
        Write-Host "✗ Revoked token not rejected" -ForegroundColor Red
    } catch {
        Write-Host "✓ Revoked token correctly rejected" -ForegroundColor Green
    }
}
Write-Host ""

# Test 12: Invalid Role (Should Fail)
Write-Host "Test 12: Invalid Role (Should Fail)" -ForegroundColor Yellow
$invalidRoleBody = @{
    username = "invalidrole"
    email = "invalid@example.com"
    password = "Test@123"
    role = "INVALID_ROLE"
} | ConvertTo-Json

try {
    $invalidRoleResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $invalidRoleBody -ContentType "application/json"
    Write-Host "✗ Invalid role not rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Message -match "Role must be") {
        Write-Host "✓ Invalid role correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
}
Write-Host ""

# Test 13: Weak Password (Should Fail)
Write-Host "Test 13: Weak Password (Should Fail)" -ForegroundColor Yellow
$weakPasswordBody = @{
    username = "weakpass"
    email = "weak@example.com"
    password = "123"
    role = "DISPATCHER"
} | ConvertTo-Json

try {
    $weakPasswordResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $weakPasswordBody -ContentType "application/json"
    Write-Host "✗ Weak password not rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Message -match "at least 8 characters") {
        Write-Host "✓ Weak password correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
}
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Testing Complete!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary of Created Users:"
Write-Host "1. admin1 (ADMIN) - Password: Admin@123"
Write-Host "2. dispatcher1 (DISPATCHER) - Password: Dispatch@123"
Write-Host "3. driver1 (AMBULANCE_DRIVER) - Password: Driver@123"
Write-Host ""
Write-Host "Tokens saved for further testing:"
Write-Host "ADMIN_ACCESS_TOKEN=$adminAccessToken"
Write-Host "DISPATCHER_ACCESS_TOKEN=$dispatcherAccessToken"
Write-Host "DRIVER_ACCESS_TOKEN=$driverAccessToken"
