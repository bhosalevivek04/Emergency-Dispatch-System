package com.vivek.api_gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Check if a request is allowed based on rate limit
     * Uses Redis INCR with TTL for distributed rate limiting
     */
    public Mono<Boolean> isAllowed(String username, String path, RateLimitConfig config) {
        String key = buildKey(username, path);
        
        return redisTemplate.opsForValue()
            .increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    // First request in window, set expiration
                    return redisTemplate.expire(key, Duration.ofSeconds(config.getWindowSeconds()))
                        .thenReturn(true);
                } else if (count <= config.getLimit()) {
                    return Mono.just(true);
                } else {
                    return Mono.just(false);
                }
            })
            .onErrorResume(e -> {
                log.error("Redis error during rate limit check for user '{}': {}", username, e.getMessage());
                // Fail open - allow request if Redis is down
                return Mono.just(true);
            });
    }

    /**
     * Get remaining requests in current window
     */
    public Mono<Long> getRemainingRequests(String username, String path, RateLimitConfig config) {
        String key = buildKey(username, path);
        
        return redisTemplate.opsForValue()
            .get(key)
            .map(value -> {
                long current = Long.parseLong(value);
                long remaining = config.getLimit() - current;
                return Math.max(0, remaining);
            })
            .defaultIfEmpty((long) config.getLimit())
            .onErrorReturn((long) config.getLimit());
    }

    /**
     * Build Redis key for rate limiting
     * Format: ratelimit:{username}:{path}
     */
    private String buildKey(String username, String path) {
        // Normalize path to avoid key explosion
        String normalizedPath = normalizePath(path);
        return String.format("ratelimit:%s:%s", username, normalizedPath);
    }

    /**
     * Normalize path to group similar endpoints
     * Example: /api/emergencies/123 -> /api/emergencies/{id}
     */
    private String normalizePath(String path) {
        // Remove query parameters
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }
        
        // Replace UUIDs and IDs with placeholders
        path = path.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{id}");
        path = path.replaceAll("/[A-Z]+-[0-9]+", "/{id}");
        path = path.replaceAll("/\\d+", "/{id}");
        
        return path;
    }
}
