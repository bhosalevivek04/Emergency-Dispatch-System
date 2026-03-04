package com.vivek.auth.config;

import com.vivek.auth.entity.User;
import com.vivek.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DataInitializer - Creates default users for testing
 * This runs once on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Check if users already exist
        if (userRepository.count() > 0) {
            log.info("Users already exist in database. Skipping initialization.");
            return;
        }

        log.info("Initializing default users...");

        // Create Admin user
        User admin = User.builder()
                .username("admin")
                .email("admin@emergency-dispatch.com")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Created admin user: username=admin, password=admin123");

        // Create Dispatcher user
        User dispatcher = User.builder()
                .username("dispatcher")
                .email("dispatcher@emergency-dispatch.com")
                .password(passwordEncoder.encode("dispatcher123"))
                .roles("DISPATCHER")
                .enabled(true)
                .build();
        userRepository.save(dispatcher);
        log.info("Created dispatcher user: username=dispatcher, password=dispatcher123");

        // Create Driver users
        for (int i = 1; i <= 3; i++) {
            User driver = User.builder()
                    .username("driver" + i)
                    .email("driver" + i + "@emergency-dispatch.com")
                    .password(passwordEncoder.encode("driver123"))
                    .roles("AMBULANCE_DRIVER")
                    .enabled(true)
                    .build();
            userRepository.save(driver);
            log.info("Created driver user: username=driver{}, password=driver123", i);
        }

        log.info("Default users initialized successfully!");
        log.info("=".repeat(60));
        log.info("LOGIN CREDENTIALS:");
        log.info("Admin:      username=admin,      password=admin123");
        log.info("Dispatcher: username=dispatcher, password=dispatcher123");
        log.info("Driver 1:   username=driver1,    password=driver123");
        log.info("Driver 2:   username=driver2,    password=driver123");
        log.info("Driver 3:   username=driver3,    password=driver123");
        log.info("=".repeat(60));
    }
}
