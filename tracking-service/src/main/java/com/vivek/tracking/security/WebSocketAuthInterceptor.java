package com.vivek.tracking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final String ROLES_HEADER = "X-User-Roles";
    private static final List<String> ALLOWED_ROLES =
            List.of("ADMIN", "DISPATCHER", "AMBULANCE_DRIVER");

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String rolesHeader = httpRequest.getHeader(ROLES_HEADER);

            // If no roles header, allow connection (will be protected by API Gateway in production)
            // This allows direct browser connections for development/testing
            if (rolesHeader == null || rolesHeader.isBlank()) {
                log.info("WebSocket connection without roles header - allowing for development");
                attributes.put("roles", List.of("ANONYMOUS"));
                return true;
            }

            List<String> roles = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .toList();

            boolean hasAllowedRole = roles.stream()
                    .anyMatch(ALLOWED_ROLES::contains);

            if (!hasAllowedRole) {
                log.warn("WebSocket connection rejected: No valid roles in {}", rolesHeader);
                return false;
            }

            attributes.put("roles", roles);
            log.info("WebSocket connection authorized with roles: {}", roles);
        }

        return true;
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
