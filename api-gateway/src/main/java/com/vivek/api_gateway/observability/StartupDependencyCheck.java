package com.vivek.api_gateway.observability;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class StartupDependencyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyCheck.class);

    private final ObjectProvider<ReactiveRedisTemplate<String, String>> redisTemplateProvider;

    @Value("${startup.dependency-check.enabled:true}")
    private boolean enabled;

    @Value("${startup.dependency-check.timeout-seconds:5}")
    private long timeoutSeconds;

    @Value("${startup.dependency-check.redis-enabled:true}")
    private boolean redisEnabled;

    public StartupDependencyCheck(ObjectProvider<ReactiveRedisTemplate<String, String>> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Startup dependency check is disabled");
            return;
        }

        try {
            checkRedis();
            log.info("Startup dependency check passed");
        } catch (Exception ex) {
            throw new IllegalStateException("Startup dependency check failed", ex);
        }
    }

    private void checkRedis() {
        if (!redisEnabled) {
            return;
        }
        ReactiveRedisTemplate<String, String> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        Boolean reachable = redisTemplate.hasKey("__startup_probe__")
                .block(Duration.ofSeconds(timeoutSeconds));
        if (reachable == null) {
            throw new IllegalStateException("Redis connectivity check timed out");
        }
    }
}
