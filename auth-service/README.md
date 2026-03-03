# Auth Service

JWT-based authentication and authorization service for the Emergency Dispatch System.

## Features

- User registration with role-based access control
- JWT access token (15 min expiry)
- Refresh token (7 days expiry)
- Token validation endpoint for API Gateway
- BCrypt password hashing
- Automatic cleanup of expired tokens
- Prometheus metrics

## User Roles

- `ADMIN` - Full system access
- `DISPATCHER` - Create emergencies, view assignments
- `AMBULANCE_DRIVER` - Update ambulance status, view assigned emergencies

## API Endpoints

### POST /auth/register
Register a new user.

**Request:**
```json
{
  "username": "dispatcher1",
  "email": "dispatcher@example.com",
  "password": "SecurePass123",
  "role": "DISPATCHER"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "dispatcher1",
  "roles": "DISPATCHER"
}
```

### POST /auth/login
Authenticate and get tokens.

**Request:**
```json
{
  "username": "dispatcher1",
  "password": "SecurePass123"
}
```

**Response:** Same as register

### POST /auth/refresh
Get new access token using refresh token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** Same as login

### POST /auth/validate
Validate access token (used by API Gateway).

**Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "valid": true,
  "username": "dispatcher1",
  "roles": "DISPATCHER",
  "message": "Token is valid"
}
```

### POST /auth/logout
Revoke refresh token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Configuration

### Environment Variables

- `JWT_SECRET` - Secret key for JWT signing (min 32 characters)
- `SPRING_DATASOURCE_URL` - PostgreSQL connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password

### application.yml

```yaml
jwt:
  secret: your-256-bit-secret-key
  expiration: 900000 # 15 minutes
  refresh-expiration: 604800000 # 7 days
```

## Database Schema

### users table
- id (BIGSERIAL PRIMARY KEY)
- username (VARCHAR(50) UNIQUE NOT NULL)
- email (VARCHAR(100) UNIQUE NOT NULL)
- password (VARCHAR(255) NOT NULL) - BCrypt hashed
- roles (VARCHAR(255) NOT NULL) - Comma-separated
- enabled (BOOLEAN NOT NULL DEFAULT true)
- created_at (TIMESTAMP NOT NULL)
- updated_at (TIMESTAMP)

### refresh_tokens table
- id (BIGSERIAL PRIMARY KEY)
- token (VARCHAR(500) UNIQUE NOT NULL)
- user_id (BIGINT NOT NULL FOREIGN KEY)
- expiry_date (TIMESTAMP NOT NULL)
- revoked (BOOLEAN NOT NULL DEFAULT false)
- created_at (TIMESTAMP NOT NULL)

## Metrics

- `auth.register.success` - Successful registrations
- `auth.register.failed` - Failed registrations (by reason)
- `auth.login.success` - Successful logins
- `auth.login.failed` - Failed logins (by reason)
- `auth.refresh.success` - Successful token refreshes
- `auth.refresh.failed` - Failed token refreshes
- `auth.validate.success` - Successful token validations
- `auth.validate.failed` - Failed token validations
- `auth.logout.success` - Successful logouts

## Running the Service

```bash
# Start PostgreSQL
docker-compose up postgres

# Run the service
cd auth-service
mvn spring-boot:run
```

Service runs on port **8086**.

## Testing

```bash
# Register a user
curl -X POST http://localhost:8086/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "email": "admin@example.com",
    "password": "Admin@123",
    "role": "ADMIN"
  }'

# Login
curl -X POST http://localhost:8086/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "password": "Admin@123"
  }'

# Validate token
curl -X POST http://localhost:8086/auth/validate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Security Considerations

1. **JWT Secret**: Use a strong, randomly generated secret (min 256 bits)
2. **HTTPS**: Always use HTTPS in production
3. **Token Storage**: Store refresh tokens securely (HttpOnly cookies recommended)
4. **Rate Limiting**: Implement rate limiting on login endpoint
5. **Password Policy**: Enforce strong password requirements
6. **Token Rotation**: Refresh tokens are single-use (revoked after use)
7. **Cleanup**: Expired tokens are automatically cleaned up daily at 2 AM
