package com.vivek.api_gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip rate limiting for health checks and public endpoints
        if (path.startsWith("/actuator") || path.equals("/auth/public-key")) {
            return chain.filter(exchange);
        }

        // Extract username from JWT (set by JwtAuthenticationFilter)
        String usernameAttr = exchange.getAttribute("username");
        final String username = (usernameAttr != null) ? usernameAttr : "anonymous";

        // Determine rate limit based on endpoint
        RateLimitConfig config = getRateLimitConfig(path);
        
        return rateLimitService.isAllowed(username, path, config)
            .flatMap(allowed -> {
                if (allowed) {
                    return rateLimitService.getRemainingRequests(username, path, config)
                        .flatMap(remaining -> {
                            // Add rate limit headers
                            exchange.getResponse().getHeaders().add("X-RateLimit-Limit", 
                                String.valueOf(config.getLimit()));
                            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", 
                                String.valueOf(remaining));
                            exchange.getResponse().getHeaders().add("X-RateLimit-Reset", 
                                String.valueOf(System.currentTimeMillis() + config.getWindowSeconds() * 1000));
                            
                            return chain.filter(exchange);
                        });
                } else {
                    log.warn("Rate limit exceeded for user '{}' on path '{}'", username, path);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", 
                        String.valueOf(config.getLimit()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
                    exchange.getResponse().getHeaders().add("Retry-After", 
                        String.valueOf(config.getWindowSeconds()));
                    
                    return exchange.getResponse().setComplete();
                }
            });
    }

    private RateLimitConfig getRateLimitConfig(String path) {
        // Authentication endpoints - strict limits
        if (path.startsWith("/auth/login") || path.startsWith("/auth/register")) {
            return new RateLimitConfig(5, 60); // 5 requests per minute
        }
        
        // Emergency creation - moderate limits
        if (path.startsWith("/api/emergencies") && path.split("/").length == 3) {
            return new RateLimitConfig(10, 60); // 10 requests per minute
        }
        
        // Location updates - generous limits (1 per second)
        if (path.contains("/location")) {
            return new RateLimitConfig(60, 60); // 60 requests per minute
        }
        
        // Read operations - very generous
        if (path.contains("/ambulances") || path.contains("/tracking")) {
            return new RateLimitConfig(100, 60); // 100 requests per minute
        }
        
        // Default rate limit
        return new RateLimitConfig(50, 60); // 50 requests per minute
    }

    @Override
    public int getOrder() {
        return 2; // Run after JWT filter (order 1)
    }
}
