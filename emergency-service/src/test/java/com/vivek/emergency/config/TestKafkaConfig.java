package com.vivek.emergency.config;

import com.vivek.emergency.dto.EmergencyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock Kafka beans.
 * This allows tests to run without a real Kafka broker.
 */
@TestConfiguration
public class TestKafkaConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, EmergencyEvent> emergencyEventKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
