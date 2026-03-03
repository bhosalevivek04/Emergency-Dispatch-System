#!/bin/bash

# Auth Service Manual Testing Script
# This script tests all auth-service endpoints

BASE_URL="http://localhost:8086"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Auth Service Testing Script"
echo "=========================================="
echo ""

# Test 1: Register Admin User
echo -e "${YELLOW}Test 1: Register Admin User${NC}"
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "email": "admin@example.com",
    "password": "Admin@123",
    "role": "ADMIN"
  }')

if echo "$REGISTER_RESPONSE" | grep -q "accessToken"; then
    echo -e "${GREEN}✓ Admin registration successful${NC}"
    ADMIN_ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    ADMIN_REFRESH_TOKEN=$(echo "$REGISTER_RESPONSE" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    echo "Access Token: ${ADMIN_ACCESS_TOKEN:0:50}..."
    echo "Refresh Token: $ADMIN_REFRESH_TOKEN"
else
    echo -e "${RED}✗ Admin registration failed${NC}"
    echo "$REGISTER_RESPONSE"
fi
echo ""

# Test 2: Register Dispatcher User
echo -e "${YELLOW}Test 2: Register Dispatcher User${NC}"
DISPATCHER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "dispatcher1",
    "email": "dispatcher@example.com",
    "password": "Dispatch@123",
    "role": "DISPATCHER"
  }')

if echo "$DISPATCHER_RESPONSE" | grep -q "accessToken"; then
    echo -e "${GREEN}✓ Dispatcher registration successful${NC}"
    DISPATCHER_ACCESS_TOKEN=$(echo "$DISPATCHER_RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    echo "Access Token: ${DISPATCHER_ACCESS_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Dispatcher registration failed${NC}"
    echo "$DISPATCHER_RESPONSE"
fi
echo ""

# Test 3: Register Ambulance Driver
echo -e "${YELLOW}Test 3: Register Ambulance Driver${NC}"
DRIVER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "driver1",
    "email": "driver@example.com",
    "password": "Driver@123",
    "role": "AMBULANCE_DRIVER"
  }')

if echo "$DRIVER_RESPONSE" | grep -q "accessToken"; then
    echo -e "${GREEN}✓ Ambulance driver registration successful${NC}"
    DRIVER_ACCESS_TOKEN=$(echo "$DRIVER_RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    echo "Access Token: ${DRIVER_ACCESS_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Ambulance driver registration failed${NC}"
    echo "$DRIVER_RESPONSE"
fi
echo ""

# Test 4: Duplicate Username (Should Fail)
echo -e "${YELLOW}Test 4: Duplicate Username (Should Fail)${NC}"
DUPLICATE_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "email": "admin2@example.com",
    "password": "Admin@123",
    "role": "ADMIN"
  }')

if echo "$DUPLICATE_RESPONSE" | grep -q "already exists"; then
    echo -e "${GREEN}✓ Duplicate username correctly rejected${NC}"
else
    echo -e "${RED}✗ Duplicate username not rejected${NC}"
    echo "$DUPLICATE_RESPONSE"
fi
echo ""

# Test 5: Login with Valid Credentials
echo -e "${YELLOW}Test 5: Login with Valid Credentials${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "password": "Admin@123"
  }')

if echo "$LOGIN_RESPONSE" | grep -q "accessToken"; then
    echo -e "${GREEN}✓ Login successful${NC}"
    LOGIN_ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    LOGIN_REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    echo "New Access Token: ${LOGIN_ACCESS_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Login failed${NC}"
    echo "$LOGIN_RESPONSE"
fi
echo ""

# Test 6: Login with Invalid Password (Should Fail)
echo -e "${YELLOW}Test 6: Login with Invalid Password (Should Fail)${NC}"
INVALID_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "password": "WrongPassword"
  }')

if echo "$INVALID_LOGIN" | grep -q "Invalid username or password"; then
    echo -e "${GREEN}✓ Invalid password correctly rejected${NC}"
else
    echo -e "${RED}✗ Invalid password not rejected${NC}"
    echo "$INVALID_LOGIN"
fi
echo ""

# Test 7: Validate Token
echo -e "${YELLOW}Test 7: Validate Token${NC}"
if [ -n "$ADMIN_ACCESS_TOKEN" ]; then
    VALIDATE_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/validate" \
      -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")
    
    if echo "$VALIDATE_RESPONSE" | grep -q '"valid":true'; then
        echo -e "${GREEN}✓ Token validation successful${NC}"
        echo "$VALIDATE_RESPONSE" | grep -o '"username":"[^"]*' | cut -d'"' -f4
        echo "$VALIDATE_RESPONSE" | grep -o '"roles":"[^"]*' | cut -d'"' -f4
    else
        echo -e "${RED}✗ Token validation failed${NC}"
        echo "$VALIDATE_RESPONSE"
    fi
else
    echo -e "${RED}✗ No token available for validation${NC}"
fi
echo ""

# Test 8: Validate Invalid Token (Should Fail)
echo -e "${YELLOW}Test 8: Validate Invalid Token (Should Fail)${NC}"
INVALID_TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/validate" \
  -H "Authorization: Bearer invalid.token.here")

if echo "$INVALID_TOKEN_RESPONSE" | grep -q '"valid":false'; then
    echo -e "${GREEN}✓ Invalid token correctly rejected${NC}"
else
    echo -e "${RED}✗ Invalid token not rejected${NC}"
    echo "$INVALID_TOKEN_RESPONSE"
fi
echo ""

# Test 9: Refresh Token
echo -e "${YELLOW}Test 9: Refresh Token${NC}"
if [ -n "$ADMIN_REFRESH_TOKEN" ]; then
    REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/refresh" \
      -H "Content-Type: application/json" \
      -d "{
        \"refreshToken\": \"$ADMIN_REFRESH_TOKEN\"
      }")
    
    if echo "$REFRESH_RESPONSE" | grep -q "accessToken"; then
        echo -e "${GREEN}✓ Token refresh successful${NC}"
        NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
        echo "New Access Token: ${NEW_ACCESS_TOKEN:0:50}..."
    else
        echo -e "${RED}✗ Token refresh failed${NC}"
        echo "$REFRESH_RESPONSE"
    fi
else
    echo -e "${RED}✗ No refresh token available${NC}"
fi
echo ""

# Test 10: Logout
echo -e "${YELLOW}Test 10: Logout${NC}"
if [ -n "$LOGIN_REFRESH_TOKEN" ]; then
    LOGOUT_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/logout" \
      -H "Content-Type: application/json" \
      -d "{
        \"refreshToken\": \"$LOGIN_REFRESH_TOKEN\"
      }")
    
    if echo "$LOGOUT_RESPONSE" | grep -q "Logged out successfully"; then
        echo -e "${GREEN}✓ Logout successful${NC}"
    else
        echo -e "${RED}✗ Logout failed${NC}"
        echo "$LOGOUT_RESPONSE"
    fi
else
    echo -e "${RED}✗ No refresh token available for logout${NC}"
fi
echo ""

# Test 11: Use Revoked Token (Should Fail)
echo -e "${YELLOW}Test 11: Use Revoked Token (Should Fail)${NC}"
if [ -n "$LOGIN_REFRESH_TOKEN" ]; then
    REVOKED_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/refresh" \
      -H "Content-Type: application/json" \
      -d "{
        \"refreshToken\": \"$LOGIN_REFRESH_TOKEN\"
      }")
    
    if echo "$REVOKED_RESPONSE" | grep -q "error"; then
        echo -e "${GREEN}✓ Revoked token correctly rejected${NC}"
    else
        echo -e "${RED}✗ Revoked token not rejected${NC}"
        echo "$REVOKED_RESPONSE"
    fi
fi
echo ""

# Test 12: Invalid Role (Should Fail)
echo -e "${YELLOW}Test 12: Invalid Role (Should Fail)${NC}"
INVALID_ROLE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "invalidrole",
    "email": "invalid@example.com",
    "password": "Test@123",
    "role": "INVALID_ROLE"
  }')

if echo "$INVALID_ROLE" | grep -q "Role must be"; then
    echo -e "${GREEN}✓ Invalid role correctly rejected${NC}"
else
    echo -e "${RED}✗ Invalid role not rejected${NC}"
    echo "$INVALID_ROLE"
fi
echo ""

# Test 13: Weak Password (Should Fail)
echo -e "${YELLOW}Test 13: Weak Password (Should Fail)${NC}"
WEAK_PASSWORD=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "weakpass",
    "email": "weak@example.com",
    "password": "123",
    "role": "DISPATCHER"
  }')

if echo "$WEAK_PASSWORD" | grep -q "at least 8 characters"; then
    echo -e "${GREEN}✓ Weak password correctly rejected${NC}"
else
    echo -e "${RED}✗ Weak password not rejected${NC}"
    echo "$WEAK_PASSWORD"
fi
echo ""

echo "=========================================="
echo "Testing Complete!"
echo "=========================================="
echo ""
echo "Summary of Created Users:"
echo "1. admin1 (ADMIN) - Password: Admin@123"
echo "2. dispatcher1 (DISPATCHER) - Password: Dispatch@123"
echo "3. driver1 (AMBULANCE_DRIVER) - Password: Driver@123"
echo ""
echo "Tokens saved for further testing:"
echo "ADMIN_ACCESS_TOKEN=$ADMIN_ACCESS_TOKEN"
echo "DISPATCHER_ACCESS_TOKEN=$DISPATCHER_ACCESS_TOKEN"
echo "DRIVER_ACCESS_TOKEN=$DRIVER_ACCESS_TOKEN"
