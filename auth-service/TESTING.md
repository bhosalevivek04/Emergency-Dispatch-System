# Auth Service Testing Guide

## Automated Integration Tests

Run the JUnit integration tests:

```bash
cd auth-service
mvn test
```

This will run 13 comprehensive integration tests covering:
- User registration (all roles)
- Login/logout flows
- Token refresh
- Token validation
- Duplicate username/email rejection
- Password validation
- Role validation
- Token revocation

## Manual Testing Scripts

### Option 1: PowerShell Script (Windows)

```powershell
cd auth-service
.\test-auth-service.ps1
```

### Option 2: Bash Script (Linux/Mac)

```bash
cd auth-service
chmod +x test-auth-service.sh
./test-auth-service.sh
```

## Prerequisites

1. **Start PostgreSQL**:
   ```bash
   docker-compose up postgres
   ```

2. **Start Auth Service**:
   ```bash
   cd auth-service
   mvn spring-boot:run
   ```

3. **Verify Service is Running**:
   ```bash
   curl http://localhost:8086/actuator/health
   ```

## Manual Testing with cURL

### 1. Register a User

```bash
curl -X POST http://localhost:8086/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "Test@1234",
    "role": "DISPATCHER"
  }'
```

Expected Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "testuser",
  "roles": "DISPATCHER"
}
```

### 2. Login

```bash
curl -X POST http://localhost:8086/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@1234"
  }'
```

### 3. Validate Token

```bash
curl -X POST http://localhost:8086/auth/validate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Expected Response:
```json
{
  "valid": true,
  "username": "testuser",
  "roles": "DISPATCHER",
  "message": "Token is valid"
}
```

### 4. Refresh Token

```bash
curl -X POST http://localhost:8086/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

### 5. Logout

```bash
curl -X POST http://localhost:8086/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

## Test Scenarios Covered

### ✅ Positive Tests
1. Register user with ADMIN role
2. Register user with DISPATCHER role
3. Register user with AMBULANCE_DRIVER role
4. Login with valid credentials
5. Validate valid token
6. Refresh token successfully
7. Logout successfully

### ✅ Negative Tests
8. Duplicate username rejection
9. Duplicate email rejection
10. Invalid password rejection
11. Invalid role rejection
12. Weak password rejection (< 8 characters)
13. Invalid username format rejection
14. Invalid token validation
15. Revoked token usage rejection

## Expected Test Results

All 13 integration tests should pass:
```
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

Manual script should show:
```
✓ Admin registration successful
✓ Dispatcher registration successful
✓ Ambulance driver registration successful
✓ Duplicate username correctly rejected
✓ Login successful
✓ Invalid password correctly rejected
✓ Token validation successful
✓ Invalid token correctly rejected
✓ Token refresh successful
✓ Logout successful
✓ Revoked token correctly rejected
✓ Invalid role correctly rejected
✓ Weak password correctly rejected
```

## Troubleshooting

### Service Not Starting
- Check if PostgreSQL is running: `docker ps | grep postgres`
- Check if port 8086 is available: `netstat -an | grep 8086`
- Check logs: `tail -f auth-service/logs/application.log`

### Database Connection Issues
- Verify PostgreSQL credentials in `application.yml`
- Ensure database `emergency_dispatch` exists
- Check user `dispatch_user` has proper permissions

### Token Validation Failing
- Ensure JWT secret is at least 32 characters
- Check token hasn't expired (15 min default)
- Verify Authorization header format: `Bearer <token>`

## Metrics

View auth metrics:
```bash
curl http://localhost:8086/actuator/metrics/auth.register.success
curl http://localhost:8086/actuator/metrics/auth.login.success
curl http://localhost:8086/actuator/prometheus
```

## Next Steps

After successful testing:
1. Integrate with API Gateway for request validation
2. Add authorization to other services
3. Implement rate limiting on login endpoint
4. Add audit logging for security events
