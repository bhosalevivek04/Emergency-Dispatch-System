package com.vivek.notification.observability;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StartupDependencyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyCheck.class);

    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    @Value("${startup.dependency-check.enabled:true}")
    private boolean enabled;

    @Value("${startup.dependency-check.timeout-seconds:5}")
    private long timeoutSeconds;

    @Value("${startup.dependency-check.kafka-enabled:true}")
    private boolean kafkaEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Startup dependency check is disabled");
            return;
        }

        try {
            checkKafka();
            log.info("Startup dependency check passed");
        } catch (Exception ex) {
            throw new IllegalStateException("Startup dependency check failed", ex);
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
