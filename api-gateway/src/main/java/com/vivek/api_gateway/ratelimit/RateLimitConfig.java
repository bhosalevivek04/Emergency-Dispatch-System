package com.vivek.api_gateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitConfig {
    private int limit;          // Maximum number of requests
    private int windowSeconds;  // Time window in seconds
}
