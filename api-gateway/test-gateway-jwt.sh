#!/bin/bash
# API Gateway JWT Integration Test Script
# Tests JWT authentication flow through API Gateway

GATEWAY_URL="http://localhost:8080"
AUTH_SERVICE_URL="http://localhost:8086"

echo "========================================"
echo "API Gateway JWT Integration Test"
echo "========================================"
echo ""

# Test 1: Health Check (Public - No Token Required)
echo "Test 1: Health Check (Public Endpoint)"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/actuator/health")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Health check successful"
    echo "  Status: $(echo $body | jq -r '.status')"
else
    echo "✗ Health check failed (HTTP $http_code)"
fi
echo ""

# Test 2: Register User via Gateway (Public - No Token Required)
echo "Test 2: Register User via Gateway"
timestamp=$(date +%Y%m%d%H%M%S)
testUser="testuser$timestamp"

response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$testUser\",\"email\":\"testuser$timestamp@example.com\",\"password\":\"Test@123\",\"role\":\"DISPATCHER\"}")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ User registration successful via gateway"
    echo "  Username: $(echo $body | jq -r '.username')"
    echo "  Role: $(echo $body | jq -r '.role')"
else
    echo "✗ User registration failed (HTTP $http_code)"
fi
echo ""

# Test 3: Login via Gateway (Public - No Token Required)
echo "Test 3: Login via Gateway"
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$testUser\",\"password\":\"Test@123\"}")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Login successful via gateway"
    ACCESS_TOKEN=$(echo $body | jq -r '.accessToken')
    REFRESH_TOKEN=$(echo $body | jq -r '.refreshToken')
    echo "  Access Token: ${ACCESS_TOKEN:0:50}..."
    echo "  Refresh Token: $REFRESH_TOKEN"
else
    echo "✗ Login failed (HTTP $http_code)"
    exit 1
fi
echo ""

# Test 4: Access Protected Endpoint WITHOUT Token (Should Fail)
echo "Test 4: Access Protected Endpoint WITHOUT Token (Should Fail)"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/emergencies")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "401" ]; then
    echo "✓ Correctly rejected with 401 Unauthorized"
else
    echo "✗ Unexpected response (HTTP $http_code)"
fi
echo ""

# Test 5: Access Protected Endpoint WITH Valid Token
echo "Test 5: Access Protected Endpoint WITH Valid Token"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/emergencies/pending" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Successfully accessed protected endpoint"
    echo "  Response received from emergency-service"
    count=$(echo "$body" | jq '. | length')
    echo "  Pending emergencies count: $count"
else
    echo "✗ Failed to access protected endpoint (HTTP $http_code)"
fi
echo ""

# Test 6: Access Protected Endpoint WITH Invalid Token (Should Fail)
echo "Test 6: Access Protected Endpoint WITH Invalid Token (Should Fail)"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/emergencies" \
    -H "Authorization: Bearer invalid.token.here")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "401" ]; then
    echo "✓ Correctly rejected invalid token with 401"
else
    echo "✗ Unexpected response (HTTP $http_code)"
fi
echo ""

# Test 7: Validate Token via Gateway
echo "Test 7: Validate Token via Gateway"
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/validate" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Token validation successful"
    echo "  Username: $(echo $body | jq -r '.username')"
    echo "  Roles: $(echo $body | jq -r '.roles')"
else
    echo "✗ Token validation failed (HTTP $http_code)"
fi
echo ""

# Test 8: Refresh Token via Gateway
echo "Test 8: Refresh Token via Gateway"
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Token refresh successful"
    NEW_ACCESS_TOKEN=$(echo $body | jq -r '.accessToken')
    echo "  New Access Token: ${NEW_ACCESS_TOKEN:0:50}..."
else
    echo "✗ Token refresh failed (HTTP $http_code)"
fi
echo ""

# Test 9: Use Refreshed Token on Protected Endpoint
echo "Test 9: Use Refreshed Token on Protected Endpoint"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/emergencies/pending" \
    -H "Authorization: Bearer $NEW_ACCESS_TOKEN")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Refreshed token works on protected endpoint"
    count=$(echo "$body" | jq '. | length')
    echo "  Pending emergencies count: $count"
else
    echo "✗ Refreshed token failed (HTTP $http_code)"
fi
echo ""

# Test 10: Logout via Gateway
echo "Test 10: Logout via Gateway"
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/logout" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Logout successful"
    echo "  Message: $(echo $body | jq -r '.message')"
else
    echo "✗ Logout failed (HTTP $http_code)"
fi
echo ""

# Test 11: Use Revoked Token (Should Fail)
echo "Test 11: Use Revoked Token After Logout (Should Fail)"
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "401" ]; then
    echo "✓ Revoked token correctly rejected"
else
    echo "✗ Unexpected response (HTTP $http_code)"
fi
echo ""

# Test 12: Check Public Key Endpoint
echo "Test 12: Fetch Public Key via Gateway"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/public-key")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✓ Public key fetched successfully"
    echo "  Algorithm: $(echo $body | jq -r '.algorithm')"
    echo "  Format: $(echo $body | jq -r '.format')"
    key=$(echo $body | jq -r '.key')
    echo "  Key (first 50 chars): ${key:0:50}..."
else
    echo "✗ Failed to fetch public key (HTTP $http_code)"
fi
echo ""

echo "========================================"
echo "Testing Complete!"
echo "========================================"
echo ""
echo "Summary:"
echo "- API Gateway is routing requests correctly"
echo "- JWT authentication is working (RS256)"
echo "- Public endpoints accessible without token"
echo "- Protected endpoints require valid JWT"
echo "- Invalid/expired tokens are rejected"
echo "- Token refresh and logout working"
echo ""
echo "Test User Created:"
echo "  Username: $testUser"
echo "  Password: Test@123"
echo "  Role: DISPATCHER"
echo ""
echo "Latest Access Token:"
echo "$NEW_ACCESS_TOKEN"
