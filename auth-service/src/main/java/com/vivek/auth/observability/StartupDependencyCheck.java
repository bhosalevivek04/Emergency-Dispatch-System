package com.vivek.auth.observability;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupDependencyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyCheck.class);

    private final ObjectProvider<DataSource> dataSourceProvider;

    @Value("${startup.dependency-check.enabled:true}")
    private boolean enabled;

    @Value("${startup.dependency-check.timeout-seconds:5}")
    private long timeoutSeconds;

    @Value("${startup.dependency-check.db-enabled:true}")
    private boolean dbEnabled;

    public StartupDependencyCheck(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Startup dependency check is disabled");
            return;
        }

        try {
            checkDatabase();
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
}
