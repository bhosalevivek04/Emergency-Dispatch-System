# Generate RSA Key Pair for JWT Signing
# This script generates private and public keys for RS256 JWT signing

Write-Host "Generating RSA Key Pair for JWT..." -ForegroundColor Cyan

# Create keys directory if it doesn't exist
$keysDir = "auth-service/src/main/resources/keys"
if (-not (Test-Path $keysDir)) {
    New-Item -ItemType Directory -Path $keysDir | Out-Null
}

# Generate private key (2048 bits)
Write-Host "Generating private key..." -ForegroundColor Yellow
openssl genrsa -out "$keysDir/private_key.pem" 2048

# Generate public key from private key
Write-Host "Generating public key..." -ForegroundColor Yellow
openssl rsa -in "$keysDir/private_key.pem" -pubout -out "$keysDir/public_key.pem"

# Convert to PKCS8 format (required by Java)
Write-Host "Converting to PKCS8 format..." -ForegroundColor Yellow
openssl pkcs8 -topk8 -inform PEM -outform PEM -in "$keysDir/private_key.pem" -out "$keysDir/private_key_pkcs8.pem" -nocrypt

Write-Host ""
Write-Host "✓ RSA Key Pair generated successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Files created:"
Write-Host "  - $keysDir/private_key_pkcs8.pem (for auth-service signing)" -ForegroundColor White
Write-Host "  - $keysDir/public_key.pem (for other services verification)" -ForegroundColor White
Write-Host ""
Write-Host "IMPORTANT: Add keys/ to .gitignore to keep private key secure!" -ForegroundColor Red
