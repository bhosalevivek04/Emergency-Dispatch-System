package com.vivek.ambulance.observability;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StartupDependencyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyCheck.class);

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    @Value("${startup.dependency-check.enabled:true}")
    private boolean enabled;

    @Value("${startup.dependency-check.timeout-seconds:5}")
    private long timeoutSeconds;

    @Value("${startup.dependency-check.db-enabled:true}")
    private boolean dbEnabled;

    @Value("${startup.dependency-check.redis-enabled:true}")
    private boolean redisEnabled;

    @Value("${startup.dependency-check.kafka-enabled:true}")
    private boolean kafkaEnabled;

    public StartupDependencyCheck(ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.dataSourceProvider = dataSourceProvider;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Startup dependency check is disabled");
            return;
        }

        try {
            checkDatabase();
            checkRedis();
            checkKafka();
            log.info("Startup dependency check passed");
        } catch (Exception ex) {
            throw new IllegalStateException("Startup dependency check failed", ex);
        }
    }

    private void checkDatabase() throws Exception {
        if (!dbEnabled) {
            return;
        }
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid((int) timeoutSeconds)) {
                throw new IllegalStateException("Database validation returned false");
            }
        }
    }

    private void checkRedis() {
        if (!redisEnabled) {
            return;
        }
        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory == null) {
            return;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis ping failed");
            }
        }
    }

    private void checkKafka() throws Exception {
        if (!kafkaEnabled) {
            return;
        }
        if (!StringUtils.hasText(kafkaBootstrapServers)) {
            return;
        }
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminConfig)) {
            adminClient.describeCluster().clusterId().get(timeoutSeconds, TimeUnit.SECONDS);
        }
    }
}
