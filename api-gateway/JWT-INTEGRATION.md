## API Gateway JWT Integration

### Overview

API Gateway now validates JWT tokens for all incoming requests (except public endpoints) using RS256 asymmetric verification.

### Architecture

```
Client Request
     ↓
API Gateway (Port 8080)
     ├── JwtAuthenticationFilter (GlobalFilter)
     │   ├── Extract Authorization header
     │   ├── Validate JWT with public key (RS256)
     │   ├── Extract username & roles
     │   └── Add X-User-* headers
     ↓
Downstream Services
     └── Receive authenticated requests with user context
```

### Security Flow

1. **Client** sends request with `Authorization: Bearer <token>`
2. **JwtAuthenticationFilter** intercepts request
3. **JwtService** validates token using public key from auth-service
4. **User context** added to headers: `X-User-Username`, `X-User-Roles`
5. **Request forwarded** to downstream service

### Public Endpoints (No JWT Required)

- `/auth/**` - Authentication endpoints
- `/actuator/health` - Health check
- `/actuator/info` - Service info

### Protected Endpoints (JWT Required)

- `/api/emergencies/**` - Emergency service
- `/api/ambulances/**` - Ambulance service
- `/api/tracking/**` - Tracking service
- `/api/notifications/**` - Notification service
- `/api/dispatch/**` - Dispatch service
- `/ws/**` - WebSocket connections

### Configuration

```yaml
auth:
  service:
    url: http://localhost:8086  # Auth service URL
```

### JWT Validation

**Algorithm:** RS256 (RSA Signature with SHA-256)

**Validation Steps:**
1. Fetch public key from auth-service at startup
2. Parse JWT token from Authorization header
3. Verify signature using public key
4. Check expiration (15 min TTL)
5. Extract claims (username, roles)

### User Context Headers

Gateway adds these headers to downstream requests:

```
X-User-Username: admin1
X-User-Roles: ADMIN
```

Services can use these headers for authorization without validating JWT again.

### Error Responses

**401 Unauthorized:**
```json
{
  "error": "Missing or invalid Authorization header",
  "status": 401
}
```

```json
{
  "error": "Invalid or expired token",
  "status": 401
}
```

### Testing

#### 1. Test Public Endpoint (No Token)
```bash
curl http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"Admin@123"}'
```

#### 2. Test Protected Endpoint (With Token)
```bash
# Get token first
TOKEN=$(curl -s http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"Admin@123"}' \
  | jq -r '.accessToken')

# Access protected endpoint
curl http://localhost:8080/api/emergencies \
  -H "Authorization: Bearer $TOKEN"
```

#### 3. Test Invalid Token (Should Fail)
```bash
curl http://localhost:8080/api/emergencies \
  -H "Authorization: Bearer invalid.token.here"
```

Expected: `401 Unauthorized`

### Monitoring

**Logs:**
- Token validation success/failure
- Username and roles for each request
- Public key fetch status

**Metrics:**
- Request count by endpoint
- Authentication failures
- Token validation errors

### Security Features

✅ **Stateless Validation** - No database calls  
✅ **RS256 Asymmetric** - Public key verification  
✅ **Token Expiration** - 15 minute TTL  
✅ **User Context** - Forwarded to services  
✅ **CORS Protection** - Configured origins  
✅ **CSRF Disabled** - Stateless JWT auth  

### Performance

- **Public Key Fetch:** Once at startup
- **Token Validation:** ~1-2ms per request
- **No Network Calls:** Stateless verification
- **Scalable:** No session storage

### Troubleshooting

**Issue:** Gateway returns 401 for all requests  
**Solution:** Check if auth-service is running on port 8086

**Issue:** "Failed to fetch public key"  
**Solution:** Verify `auth.service.url` in application.yml

**Issue:** Token validation fails  
**Solution:** Ensure token is RS256 (not HS256)

### Next Steps

1. **Service-Level Authorization**
   - Add `@PreAuthorize` in services
   - Use `X-User-Roles` header for role checks

2. **Rate Limiting**
   - Add rate limiting filter
   - Protect against brute force

3. **API Key Support**
   - Add API key authentication
   - For service-to-service calls

### Production Checklist

- [ ] Configure CORS for production origins
- [ ] Set up HTTPS/TLS
- [ ] Enable request logging
- [ ] Configure rate limiting
- [ ] Set up monitoring alerts
- [ ] Document API endpoints
- [ ] Test token expiration handling
- [ ] Verify WebSocket authentication
