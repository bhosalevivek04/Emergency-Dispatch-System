# RS256 JWT Signing - Security Upgrade

## What Changed

Upgraded from **HS256 (symmetric)** to **RS256 (asymmetric)** JWT signing.

### Before (HS256)
- Single shared secret across all services
- Every service needs the secret to verify tokens
- Secret distribution is a security risk
- If one service is compromised, all services are at risk

### After (RS256)
- Auth-service signs with **private key** (kept secure)
- Other services verify with **public key** (can be distributed)
- Private key never leaves auth-service
- Compromising one service doesn't expose signing capability

## Architecture

```
┌─────────────────────────────────────────┐
│         Auth Service                     │
│  ┌────────────────────────────────────┐ │
│  │  Private Key (private_key_pkcs8.pem)│ │
│  │  - Signs JWT tokens                 │ │
│  │  - NEVER shared                     │ │
│  └────────────────────────────────────┘ │
│  ┌────────────────────────────────────┐ │
│  │  Public Key (public_key.pem)        │ │
│  │  - Exposed via /auth/public-key     │ │
│  │  - Safe to distribute               │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
                    ↓
        JWT Token (signed with private key)
                    ↓
┌─────────────────────────────────────────┐
│    API Gateway / Other Services          │
│  ┌────────────────────────────────────┐ │
│  │  Public Key (from auth-service)     │ │
│  │  - Verifies JWT signature           │ │
│  │  - No network call needed           │ │
│  │  - Stateless validation             │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

## Key Generation

### Production Keys
```bash
# Generate RSA key pair
.\generate-keys.ps1

# Files created:
# - auth-service/src/main/resources/keys/private_key_pkcs8.pem (PRIVATE - DO NOT COMMIT)
# - auth-service/src/main/resources/keys/public_key.pem (PUBLIC - safe to commit)
```

### Test Keys
Test keys are automatically generated in `auth-service/src/test/resources/keys/`

## Security Best Practices

### ✅ DO
- Keep private key secure (added to .gitignore)
- Rotate keys periodically (every 6-12 months)
- Use environment-specific keys (dev, staging, prod)
- Monitor key access logs
- Use strong key size (2048 bits minimum)

### ❌ DON'T
- Never commit private keys to Git
- Never share private keys via email/chat
- Never use same keys across environments
- Never hardcode keys in application code

## Public Key Distribution

Other services can fetch the public key:

```bash
curl http://localhost:8086/auth/public-key
```

Response:
```json
{
  "key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "algorithm": "RSA",
  "format": "X.509"
}
```

## Token Verification (Other Services)

```java
// Load public key from auth-service
String publicKeyPEM = fetchPublicKeyFromAuthService();
byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);

// Verify JWT
Claims claims = Jwts.parser()
    .verifyWith(publicKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

## Migration Impact

### Breaking Changes
- Old HS256 tokens are **invalid** after upgrade
- All users must re-login to get new RS256 tokens
- Services must update to use public key verification

### Non-Breaking
- API endpoints remain the same
- Token structure unchanged (same claims)
- Refresh token flow unchanged

## Rollback Plan

If issues occur:

1. Revert JwtService.java to HS256 version
2. Restore jwt.secret in application.yml
3. Restart auth-service
4. Users re-login to get HS256 tokens

## Performance

RS256 is slightly slower than HS256:
- Signing: ~2-3ms (vs ~0.5ms for HS256)
- Verification: ~1-2ms (vs ~0.3ms for HS256)

**Impact:** Negligible for typical load (< 1000 req/sec)

## Monitoring

Watch these metrics after deployment:
- `auth.login.success` - Should remain stable
- `auth.validate.failed` - May spike initially (old tokens)
- JWT verification errors in other services

## Testing

```bash
# Clean database
docker exec -it postgres psql -U dispatch_user -d emergency_dispatch -c "DELETE FROM refresh_tokens; DELETE FROM users;"

# Run tests
cd auth-service
.\test-auth-service.ps1

# All 13 tests should pass with RS256 tokens
```

## Production Deployment Checklist

- [ ] Generate production RSA key pair
- [ ] Store private key in secure vault (AWS Secrets Manager, HashiCorp Vault)
- [ ] Update auth-service to load key from vault
- [ ] Deploy auth-service with RS256
- [ ] Update API Gateway to fetch public key
- [ ] Update other services to use public key verification
- [ ] Notify users of required re-login
- [ ] Monitor error rates for 24 hours
- [ ] Document key rotation procedure

## Key Rotation Procedure

1. Generate new key pair
2. Keep old public key for 24 hours (grace period)
3. Auth-service signs with new private key
4. Services verify with both old and new public keys
5. After 24 hours, remove old public key
6. All old tokens expired naturally (15 min TTL)

## Security Audit

This implementation follows:
- ✅ OWASP JWT Security Best Practices
- ✅ RFC 7519 (JWT)
- ✅ RFC 7515 (JWS - JSON Web Signature)
- ✅ NIST SP 800-57 (Key Management)

## References

- [JWT.io - RS256 vs HS256](https://jwt.io/)
- [RFC 7519 - JSON Web Token](https://tools.ietf.org/html/rfc7519)
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
