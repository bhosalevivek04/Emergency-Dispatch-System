# API Gateway JWT Integration Testing

## Prerequisites

Before running tests, ensure the following services are running:

1. **PostgreSQL** (port 5432) - Database for auth-service
2. **Redis** (port 6379) - For caching and state management
3. **Kafka** (port 9092) - Message broker
4. **Auth Service** (port 8086) - JWT authentication service
5. **Emergency Service** (port 8081) - For testing protected endpoints
6. **API Gateway** (port 8080) - The service being tested

## Quick Start

### Start Infrastructure (Docker)

```bash
# Start PostgreSQL, Redis, Kafka
docker-compose up -d postgres redis kafka
```

### Start Services (STS)

1. Start auth-service on port 8086
2. Start emergency-service on port 8081
3. Start api-gateway on port 8080

### Run Tests

**PowerShell:**
```powershell
.\api-gateway\test-gateway-jwt.ps1
```

**Bash:**
```bash
./api-gateway/test-gateway-jwt.sh
```

## What the Tests Cover

### 1. Public Endpoints (No JWT Required)
- ✅ Health check (`/actuator/health`)
- ✅ User registration (`/auth/register`)
- ✅ User login (`/auth/login`)
- ✅ Public key fetch (`/auth/public-key`)

### 2. Protected Endpoints (JWT Required)
- ✅ Emergency service endpoints (`/api/emergencies`)
- ✅ Ambulance service endpoints (`/api/ambulances`)
- ✅ Tracking service endpoints (`/api/tracking`)
- ✅ Notification service endpoints (`/api/notifications`)

### 3. JWT Validation
- ✅ Valid token accepted
- ✅ Invalid token rejected (401)
- ✅ Missing token rejected (401)
- ✅ Expired token rejected (401)

### 4. Token Lifecycle
- ✅ Token generation (login)
- ✅ Token validation
- ✅ Token refresh
- ✅ Token revocation (logout)
- ✅ Revoked token rejected

### 5. User Context Forwarding
- ✅ `X-User-Username` header added
- ✅ `X-User-Roles` header added
- ✅ Downstream services receive user context

## Test Scenarios

### Test 1: Health Check
```bash
curl http://localhost:8080/actuator/health
```
Expected: `200 OK` with status "UP"

### Test 2: Register User
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test@123","role":"DISPATCHER"}'
```
Expected: `200 OK` with user details and access token

### Test 3: Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test@123"}'
```
Expected: `200 OK` with access token and refresh token

### Test 4: Access Protected Endpoint (No Token)
```bash
curl http://localhost:8080/api/emergencies
```
Expected: `401 Unauthorized`

### Test 5: Access Protected Endpoint (With Token)
```bash
TOKEN="<your-access-token>"
curl http://localhost:8080/api/emergencies \
  -H "Authorization: Bearer $TOKEN"
```
Expected: `200 OK` with emergency data

### Test 6: Invalid Token
```bash
curl http://localhost:8080/api/emergencies \
  -H "Authorization: Bearer invalid.token.here"
```
Expected: `401 Unauthorized`

### Test 7: Validate Token
```bash
curl -X POST http://localhost:8080/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```
Expected: `200 OK` with username and roles

### Test 8: Refresh Token
```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your-refresh-token>"}'
```
Expected: `200 OK` with new access token

### Test 9: Logout
```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your-refresh-token>"}'
```
Expected: `200 OK` with logout confirmation

### Test 10: Use Revoked Token
```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<revoked-refresh-token>"}'
```
Expected: `401 Unauthorized`

## Expected Test Results

All 12 tests should pass:

```
✓ Test 1: Health Check (Public Endpoint)
✓ Test 2: Register User via Gateway
✓ Test 3: Login via Gateway
✓ Test 4: Access Protected Endpoint WITHOUT Token (Should Fail)
✓ Test 5: Access Protected Endpoint WITH Valid Token
✓ Test 6: Access Protected Endpoint WITH Invalid Token (Should Fail)
✓ Test 7: Validate Token via Gateway
✓ Test 8: Refresh Token via Gateway
✓ Test 9: Use Refreshed Token on Protected Endpoint
✓ Test 10: Logout via Gateway
✓ Test 11: Use Revoked Token After Logout (Should Fail)
✓ Test 12: Fetch Public Key via Gateway
```

## Troubleshooting

### Issue: "Failed to fetch public key from auth-service"
**Solution:** Ensure auth-service is running on port 8086

### Issue: "Connection refused"
**Solution:** Check if API Gateway is running on port 8080

### Issue: All requests return 401
**Solution:** 
1. Check if auth-service is running
2. Verify public key is accessible at `http://localhost:8086/auth/public-key`
3. Check API Gateway logs for JWT validation errors

### Issue: Protected endpoints return 500
**Solution:** Check if downstream services (emergency-service, etc.) are running

### Issue: Token validation fails
**Solution:** 
1. Verify tokens are RS256 (not HS256)
2. Check if private/public keys match
3. Verify token hasn't expired (15 min TTL)

## Manual Testing with Postman/Insomnia

### 1. Create Collection
- Base URL: `http://localhost:8080`

### 2. Register User
```
POST /auth/register
Body: {"username":"admin1","password":"Admin@123","role":"ADMIN"}
```

### 3. Login
```
POST /auth/login
Body: {"username":"admin1","password":"Admin@123"}
```
Save the `accessToken` from response.

### 4. Access Protected Endpoint
```
GET /api/emergencies
Headers: Authorization: Bearer <accessToken>
```

### 5. Validate Token
```
POST /auth/validate
Headers: Authorization: Bearer <accessToken>
```

## Monitoring

### Check Gateway Logs
Look for these log messages:
- `Public key fetched successfully from auth-service`
- `Authenticated request: username=..., roles=..., path=...`
- `Token validation error for path ...`

### Check Auth Service Logs
- `RSA keys loaded successfully for RS256 JWT signing`
- `Token generated for user: ...`
- `Token validated for user: ...`

## Security Verification

### ✅ RS256 Algorithm
```bash
# Decode JWT header (should show "alg":"RS256")
echo "<token>" | cut -d'.' -f1 | base64 -d
```

### ✅ Token Expiration
Tokens expire after 15 minutes. Test by:
1. Login and get token
2. Wait 16 minutes
3. Try to use token (should fail with 401)

### ✅ Public Key Verification
```bash
curl http://localhost:8080/auth/public-key
```
Should return RSA public key in X.509 format.

### ✅ User Context Headers
Check downstream service logs for:
- `X-User-Username: admin1`
- `X-User-Roles: ADMIN`

## Performance Metrics

- **Public Key Fetch:** Once at startup (~100ms)
- **Token Validation:** ~1-2ms per request
- **No Database Calls:** Stateless verification
- **Scalable:** No session storage

## Next Steps After Testing

1. ✅ Verify all tests pass
2. ✅ Check logs for errors
3. ✅ Test with different roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER)
4. ✅ Test token expiration handling
5. ✅ Test concurrent requests
6. ⬜ Add service-level authorization (@PreAuthorize)
7. ⬜ Add rate limiting
8. ⬜ Set up monitoring and alerts

## Production Checklist

- [ ] Configure HTTPS/TLS
- [ ] Set production CORS origins
- [ ] Enable request logging
- [ ] Configure rate limiting
- [ ] Set up monitoring alerts
- [ ] Document API endpoints
- [ ] Test token expiration handling
- [ ] Verify WebSocket authentication
- [ ] Load testing
- [ ] Security audit

