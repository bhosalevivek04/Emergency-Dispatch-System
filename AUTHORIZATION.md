# Service-Level Authorization

This document describes the role-based access control (RBAC) implementation for the Emergency Dispatch System microservices.

## Overview

The system implements authorization at the microservice level using Spring Security's method-level security with `@PreAuthorize` annotations. The API Gateway validates JWT tokens and forwards user context via HTTP headers (`X-User-Username`, `X-User-Roles`), and each microservice enforces authorization based on these headers.

## Roles

The system supports three roles:

### ADMIN
- **Capabilities**: Full system access including all operational endpoints, metrics, diagnostics, and resource management
- **Use Case**: System administrators and operations team

### DISPATCHER
- **Capabilities**: Create emergencies, manage dispatch operations, view tracking data
- **Use Case**: Emergency dispatch operators

### AMBULANCE_DRIVER
- **Capabilities**: View assigned emergencies, update ambulance location, access tracking data
- **Use Case**: Ambulance drivers using mobile app

## Authorization Matrix

### Emergency Service (Port 8081)

| Endpoint | Method | ADMIN | DISPATCHER | AMBULANCE_DRIVER |
|----------|--------|-------|------------|------------------|
| `/emergency` | POST | ✓ | ✓ | ✗ |
| `/emergency/{id}` | GET | ✓ | ✓ | ✓ |
| `/emergency/status/{status}` | GET | ✓ | ✓ | ✗ |
| `/emergency/pending` | GET | ✓ | ✓ | ✗ |

### Ambulance Service (Port 8082)

| Endpoint | Method | ADMIN | DISPATCHER | AMBULANCE_DRIVER |
|----------|--------|-------|------------|------------------|
| `/ambulance` | POST | ✓ | ✗ | ✓ |
| `/diagnostic/init-fleet` | POST | ✓ | ✗ | ✗ |
| `/diagnostic/fleet-status` | GET | ✓ | ✗ | ✗ |

### Tracking Service (Port 8085)

| Endpoint | Method | ADMIN | DISPATCHER | AMBULANCE_DRIVER | Notes |
|----------|--------|-------|------------|------------------|-------|
| `/tracking/ambulances` | GET | ✓ | ✓ | ✓ | Public (no auth required for development) |
| `/tracking/ambulances/{id}` | GET | ✓ | ✓ | ✓ | Public (no auth required for development) |
| `/tracking/location` | POST | ✓ | ✗ | ✓ | |
| `/ws` (WebSocket) | CONNECT | ✓ | ✓ | ✓ | |

### Internal Services (No Authorization)

The following services are internal-only and do not require authorization headers:
- **Dispatch Service** (Port 8083) - Called by other microservices for dispatch logic
- **Notification Service** (Port 8084) - Called by other microservices for notifications

These services are not exposed through the API Gateway and should only be accessible within the private network.

## Testing Authorization

### Using curl

#### Test DISPATCHER creating emergency (should succeed)
```bash
curl -X POST http://localhost:8081/emergency \
  -H "X-User-Username: dispatcher1" \
  -H "X-User-Roles: DISPATCHER" \
  -H "Content-Type: application/json" \
  -d '{"emergencyId":"EMG-TEST-001","lat":18.5204,"lon":73.8567,"priority":"HIGH"}'
```

#### Test AMBULANCE_DRIVER creating emergency (should fail with 403)
```bash
curl -X POST http://localhost:8081/emergency \
  -H "X-User-Username: driver1" \
  -H "X-User-Roles: AMBULANCE_DRIVER" \
  -H "Content-Type: application/json" \
  -d '{"emergencyId":"EMG-TEST-002","lat":18.5204,"lon":73.8567,"priority":"HIGH"}'
```

#### Test ADMIN accessing diagnostic endpoint (should succeed)
```bash
curl -X GET http://localhost:8082/diagnostic/fleet-status \
  -H "X-User-Username: admin1" \
  -H "X-User-Roles: ADMIN"
```

#### Test DISPATCHER accessing diagnostic endpoint (should fail with 403)
```bash
curl -X GET http://localhost:8082/diagnostic/fleet-status \
  -H "X-User-Username: dispatcher1" \
  -H "X-User-Roles: DISPATCHER"
```

#### Test AMBULANCE_DRIVER updating location (should succeed)
```bash
curl -X POST http://localhost:8085/tracking/location \
  -H "X-User-Username: driver1" \
  -H "X-User-Roles: AMBULANCE_DRIVER" \
  -H "Content-Type: application/json" \
  -d '{"ambulanceId":"AMB-001","latitude":18.5200,"longitude":73.8560,"speed":45.5,"heading":90.0}'
```

#### Test missing roles header (should fail with 403)
```bash
curl -X GET http://localhost:8081/emergency/pending \
  -H "X-User-Username: user1"
```

### Using PowerShell

Run the automated test script:
```powershell
.\test-authorization.ps1
```

This script tests all authorization rules across all services and provides a summary of passed/failed tests.

### Using Postman

1. Create a new request
2. Set the HTTP method and URL
3. Add headers:
   - `X-User-Username`: Your username (e.g., "testuser")
   - `X-User-Roles`: Your role (e.g., "DISPATCHER")
   - `Content-Type`: application/json (for POST requests)
4. Add request body (for POST requests)
5. Send the request

Expected responses:
- **200 OK**: Authorization succeeded, operation completed
- **403 Forbidden**: User lacks required role for this operation
- **401 Unauthorized**: Missing or invalid authentication (handled by API Gateway)

## Error Responses

When authorization fails, the service returns HTTP 403 Forbidden with a JSON error response:

```json
{
  "error": "Access denied",
  "message": "User 'dispatcher1' lacks required role for this operation",
  "status": 403
}
```

The error message includes:
- `error`: Brief error type
- `message`: Descriptive message with username (no sensitive data)
- `status`: HTTP status code (403)

## Architecture

### Request Flow

1. **Client** sends request with JWT token to API Gateway
2. **API Gateway** validates JWT and extracts username and roles
3. **API Gateway** forwards request with `X-User-Username` and `X-User-Roles` headers
4. **Microservice** extracts headers via `HeaderAuthenticationFilter`
5. **Spring Security** evaluates `@PreAuthorize` expression
6. **Controller** method executes if authorized, or returns 403 if not

### Security Components

Each external-facing service (Emergency, Ambulance, Tracking) includes:

- **HeaderAuthenticationFilter**: Extracts user context from headers and populates Spring Security context
- **SecurityConfiguration**: Enables method-level security and configures filter chain
- **CustomAccessDeniedHandler**: Provides consistent 403 error responses
- **@PreAuthorize annotations**: Declarative authorization rules on controller methods

### WebSocket Authorization

WebSocket connections are protected by `WebSocketAuthInterceptor`, which:
- Checks `X-User-Roles` header during handshake (when available)
- For development/testing: Allows direct browser connections without headers
- For production: Should be accessed through API Gateway which adds headers
- Allows connections with ADMIN, DISPATCHER, or AMBULANCE_DRIVER roles

**Note**: Direct browser WebSocket connections cannot easily send custom headers during the SockJS handshake. In production, WebSocket connections should go through the API Gateway, which will add the authorization headers. For development/testing, direct connections are allowed.

## Security Considerations

### Development vs Production

**Tracking Service GET Endpoints:**
For development convenience, the tracking service GET endpoints (`/tracking/ambulances` and `/tracking/ambulances/{id}`) are publicly accessible without authorization headers. This allows:
- Direct browser/frontend access during development
- Simplified testing and debugging
- Real-time map visualization without complex auth setup

**Production Recommendation:**
In production, these endpoints should be accessed through the API Gateway, which will add proper authorization headers. Alternatively, implement one of these approaches:
1. Network isolation - tracking service only accessible from private network
2. API key authentication for frontend clients
3. JWT token in frontend with proper token management

### Trust Model
- Microservices trust headers forwarded by the API Gateway
- API Gateway is the only entry point (enforced by network policies)
- Internal services remain accessible without headers for service-to-service calls

### Valid Roles
Only three roles are recognized: `ADMIN`, `DISPATCHER`, `AMBULANCE_DRIVER`
- Invalid role names are silently filtered out
- If all roles are invalid, protected endpoints return 403

### Missing Headers
- Missing `X-User-Username`: Defaults to "anonymous"
- Missing `X-User-Roles`: Empty authority collection, protected endpoints return 403
- Warning logged for debugging

### Logging
Authorization failures are logged with:
- Username
- HTTP method and endpoint
- Timestamp

Logs do not include:
- Sensitive data (passwords, tokens)
- Full request bodies
- Other users' information

## Production Deployment

### Network Security
- Deploy microservices in private network segments
- Use network policies to restrict access to API Gateway only
- Consider mutual TLS (mTLS) for service-to-service authentication

### Monitoring
Track these metrics:
- Authorization failure rate (403 responses)
- Missing header rate
- Invalid role rate
- Response time impact of authorization checks

### Alerts
Set up alerts for:
- Spike in 403 responses (may indicate misconfiguration)
- High rate of missing headers (gateway issue)
- Authorization filter exceptions

## Troubleshooting

### Issue: All requests return 403
**Cause**: API Gateway not forwarding headers
**Solution**: Verify `JwtAuthenticationFilter` in API Gateway is adding `X-User-Username` and `X-User-Roles` headers

### Issue: Specific role cannot access endpoint
**Cause**: Incorrect `@PreAuthorize` annotation
**Solution**: Check controller method annotation matches authorization matrix

### Issue: WebSocket connections rejected
**Cause**: Headers not included in WebSocket handshake
**Solution**: Ensure client includes `X-User-Roles` header when establishing WebSocket connection

### Issue: Internal service calls failing
**Cause**: Internal service accidentally has Spring Security enabled
**Solution**: Verify Dispatch and Notification services do not have `spring-boot-starter-security` dependency

## Development

### Adding New Endpoints

When adding a new protected endpoint:

1. Add `@PreAuthorize` annotation with appropriate roles:
```java
@GetMapping("/new-endpoint")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
public ResponseEntity<?> newEndpoint() {
    // Implementation
}
```

2. Update this documentation with the new endpoint
3. Add tests to verify authorization rules
4. Update the authorization matrix

### Adding New Roles

To add a new role:

1. Update `HeaderAuthenticationFilter.isValidRole()` in all services
2. Update `WebSocketAuthInterceptor.ALLOWED_ROLES` if WebSocket access needed
3. Add role to auth-service JWT generation
4. Update this documentation
5. Add comprehensive tests

## References

- [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [JWT Authentication Implementation](./API-GATEWAY-JWT-SUMMARY.md)
- [Service-Level Authorization Spec](./.kiro/specs/service-level-authorization/)
