package com.vivek.api_gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Service
@Slf4j
public class JwtService {

    @Value("${auth.service.url:http://localhost:8086}")
    private String authServiceUrl;

    private PublicKey publicKey;
    private final WebClient webClient;

    public JwtService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostConstruct
    public void init() {
        int maxAttempts = 10;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                fetchPublicKey();
                log.info("Public key fetched successfully from auth-service on attempt {}", attempt);
                return;
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed to fetch public key: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(3000); // Wait 3 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry public key fetch", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Failed to fetch public key after " + maxAttempts + " attempts");
    }

    private void fetchPublicKey() throws Exception {
        log.info("Fetching public key from: {}/auth/public-key", authServiceUrl);
        
        PublicKeyResponse response = webClient.get()
                .uri(authServiceUrl + "/auth/public-key")
                .retrieve()
                .bodyToMono(PublicKeyResponse.class)
                .block();

        if (response == null || response.key() == null) {
            throw new RuntimeException("Failed to fetch public key from auth-service");
        }

        byte[] decoded = Base64.getDecoder().decode(response.key());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.publicKey = kf.generatePublic(spec);
        
        log.info("Public key loaded: algorithm={}, format={}", response.algorithm(), response.format());
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }

    public String extractRoles(String token) {
        return validateToken(token).get("roles", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = validateToken(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    record PublicKeyResponse(String key, String algorithm, String format) {}
}
