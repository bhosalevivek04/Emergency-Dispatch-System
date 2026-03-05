package com.vivek.tracking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final String ROLES_HEADER = "X-User-Roles";
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "DISPATCHER", "AMBULANCE_DRIVER");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // Allow SockJS handshake endpoints (info, iframe) without authentication
            String path = httpRequest.getRequestURI();
            if (path.endsWith("/info") || path.contains("/iframe")) {
                log.debug("Allowing SockJS handshake endpoint: {}", path);
                return true;
            }

            // Read token from query parameter ?token=xxx
            String query = httpRequest.getQueryString();
            String token = null;
            if (query != null && query.contains("token=")) {
                // Parse token from query string
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6); // Remove "token=" prefix
                        break;
                    }
                }
            }

            // If no token in query, try X-User-Roles header for gateway-injected roles
            String rolesHeader = httpRequest.getHeader(ROLES_HEADER);

            if ((token == null || token.isBlank()) && (rolesHeader == null || rolesHeader.isBlank())) {
                log.warn("WebSocket connection rejected: Missing token parameter or X-User-Roles header for path: {}",
                        path);
                return false;
            }

            List<String> roles;

            // Use roles from gateway header if available (authenticated through gateway)
            if (rolesHeader != null && !rolesHeader.isBlank()) {
                roles = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isBlank())
                        .toList();
            } else if (token != null && !token.isBlank()) {
                roles = extractRolesFromTokenClaims(token);
                if (roles.isEmpty()) {
                    log.warn("WebSocket connection rejected: Token has no roles");
                    return false;
                }
            } else {
                log.warn("WebSocket connection rejected: Invalid authentication");
                return false;
            }

            boolean hasAllowedRole = roles.stream()
                    .anyMatch(ALLOWED_ROLES::contains);

            if (!hasAllowedRole) {
                log.warn("WebSocket connection rejected: No valid roles in {}", roles);
                return false;
            }

            attributes.put("roles", roles);
            log.info("WebSocket connection authorized with roles: {}", roles);
        }

        return true;
    }

    private List<String> extractRolesFromTokenClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Collections.emptyList();
            }

            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(decodedPayload, new TypeReference<>() {});

            Object expClaim = claims.get("exp");
            if (expClaim instanceof Number exp) {
                long expSeconds = exp.longValue();
                if (Instant.now().getEpochSecond() >= expSeconds) {
                    log.warn("WebSocket token is expired");
                    return Collections.emptyList();
                }
            }

            Object rolesClaim = claims.get("roles");
            if (rolesClaim instanceof String rolesString) {
                return Arrays.stream(rolesString.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toList());
            }

            if (rolesClaim instanceof Collection<?> rolesCollection) {
                return rolesCollection.stream()
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Unable to parse WebSocket token claims: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No action needed
    }
}
