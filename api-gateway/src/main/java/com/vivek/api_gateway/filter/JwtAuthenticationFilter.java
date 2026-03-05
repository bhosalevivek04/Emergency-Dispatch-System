package com.vivek.api_gateway.filter;

import com.vivek.api_gateway.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;

    // Paths that don't require authentication
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/auth/",
            "/actuator/health",
            "/actuator/info",
            "/ws/ws-sockjs/info",  // SockJS handshake endpoint
            "/ws/ws-sockjs/",      // SockJS WebSocket endpoints
            "/ws/ws"               // Native WebSocket endpoint
    );

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Skip authentication for excluded paths
        if (isExcludedPath(path)) {
            log.debug("Skipping authentication for path: {}", path);
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            // Validate token
            if (!jwtService.isTokenValid(token)) {
                log.warn("Invalid or expired token for path: {}", path);
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            // Extract claims
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String roles = claims.get("roles", String.class);
            String ambulanceId = claims.get("ambulanceId", String.class);

            log.debug("Authenticated request: username={}, roles={}, ambulanceId={}, path={}",
                    username, roles, ambulanceId, path);

            // Add user context to request headers for downstream services
            ServerHttpRequest.Builder requestBuilder = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Username");
                        headers.remove("X-User-Roles");
                        headers.remove("X-User-Ambulance-Id");
                    })
                    .header("X-User-Username", username)
                    .header("X-User-Roles", roles);

            if (ambulanceId != null && !ambulanceId.isBlank()) {
                requestBuilder.header("X-User-Ambulance-Id", ambulanceId);
            }

            ServerHttpRequest modifiedRequest = requestBuilder.build();

            // Set username attribute for rate limiting
            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();
            modifiedExchange.getAttributes().put("username", username);

            return chain.filter(modifiedExchange);

        } catch (Exception e) {
            log.error("Token validation error for path {}: {}", path, e.getMessage());
            return onError(exchange, "Token validation failed", HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorBody = String.format("{\"error\":\"%s\",\"status\":%d}", message, status.value());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100; // High priority - run before other filters
    }
}

