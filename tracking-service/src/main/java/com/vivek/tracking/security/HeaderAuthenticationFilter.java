package com.vivek.tracking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(1)
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String USERNAME_HEADER = "X-User-Username";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String username = request.getHeader(USERNAME_HEADER);
        String rolesHeader = request.getHeader(ROLES_HEADER);

        if (username == null) {
            username = "anonymous";
            log.warn("Missing {} header for request: {} {}",
                    USERNAME_HEADER, request.getMethod(), request.getRequestURI());
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(this::isValidRole)
                    .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                    .collect(Collectors.toList());
        } else {
            log.warn("Missing {} header for request: {} {}",
                    ROLES_HEADER, request.getMethod(), request.getRequestURI());
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isValidRole(String role) {
        return role.equals("ADMIN") ||
                role.equals("DISPATCHER") ||
                role.equals("AMBULANCE_DRIVER");
    }
}
