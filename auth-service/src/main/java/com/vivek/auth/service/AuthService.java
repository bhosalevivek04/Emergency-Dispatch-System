package com.vivek.auth.service;

import com.vivek.auth.dto.*;
import com.vivek.auth.entity.RefreshToken;
import com.vivek.auth.entity.User;
import com.vivek.auth.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final MeterRegistry meterRegistry;
    private final AmbulanceProvisioningService ambulanceProvisioningService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            meterRegistry.counter("auth.register.failed", "reason", "username_exists").increment();
            throw new RuntimeException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            meterRegistry.counter("auth.register.failed", "reason", "email_exists").increment();
            throw new RuntimeException("Email already exists");
        }

        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(request.getRole())
                .ambulanceId(request.getAmbulanceId())
                .enabled(true)
                .build();

        // Keep user/ambulance mapping consistent at registration time.
        if ("AMBULANCE_DRIVER".equals(request.getRole()) && request.getAmbulanceId() != null && !request.getAmbulanceId().isBlank()) {
            ambulanceProvisioningService.ensureAmbulanceRegistered(request.getAmbulanceId(), request.getUsername());
        }

        user = userRepository.save(user);
        log.info("User registered successfully: username={} role={}", user.getUsername(), user.getRoles());
        meterRegistry.counter("auth.register.success").increment();

        // Generate tokens
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getAmbulanceId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .username(user.getUsername())
                .roles(user.getRoles())
                .ambulanceId(user.getAmbulanceId())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.login.failed", "reason", "user_not_found").increment();
                    return new RuntimeException("Invalid username or password");
                });

        // Check if user is enabled
        if (!user.getEnabled()) {
            meterRegistry.counter("auth.login.failed", "reason", "user_disabled").increment();
            throw new RuntimeException("User account is disabled");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            meterRegistry.counter("auth.login.failed", "reason", "invalid_password").increment();
            throw new RuntimeException("Invalid username or password");
        }

        log.info("User logged in successfully: username={}", user.getUsername());
        meterRegistry.counter("auth.login.success").increment();

        // Generate tokens
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getAmbulanceId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .username(user.getUsername())
                .roles(user.getRoles())
                .ambulanceId(user.getAmbulanceId())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.refresh.failed", "reason", "token_not_found").increment();
                    return new RuntimeException("Invalid refresh token");
                });

        // Verify expiration
        refreshToken = refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();

        // Generate new access token
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getAmbulanceId());

        log.info("Token refreshed successfully: username={}", user.getUsername());
        meterRegistry.counter("auth.refresh.success").increment();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .username(user.getUsername())
                .roles(user.getRoles())
                .ambulanceId(user.getAmbulanceId())
                .build();
    }

    public TokenValidationResponse validateToken(String token) {
        try {
            if (jwtService.validateToken(token)) {
                String username = jwtService.extractUsername(token);
                String roles = jwtService.extractRoles(token);

                meterRegistry.counter("auth.validate.success").increment();

                return TokenValidationResponse.builder()
                        .valid(true)
                        .username(username)
                        .roles(roles)
                        .message("Token is valid")
                        .build();
            } else {
                meterRegistry.counter("auth.validate.failed", "reason", "invalid_token").increment();
                return TokenValidationResponse.builder()
                        .valid(false)
                        .message("Token is invalid or expired")
                        .build();
            }
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            meterRegistry.counter("auth.validate.failed", "reason", "exception").increment();
            return TokenValidationResponse.builder()
                    .valid(false)
                    .message("Token validation failed: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
        meterRegistry.counter("auth.logout.success").increment();
        log.info("User logged out successfully");
    }
}
