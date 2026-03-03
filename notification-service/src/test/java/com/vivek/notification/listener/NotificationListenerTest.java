package com.vivek.notification.listener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for NotificationListener Kafka exception handling.
 * No Spring context needed — tests the listener class in isolation.
 */
class NotificationListenerTest {

    private NotificationListener listener;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new NotificationListener(new ObjectMapper(), meterRegistry);
    }

    @Test
    @DisplayName("consumeAssignment() does NOT throw on malformed JSON")
    void consumeAssignment_malformedJson_doesNotThrow() {
        assertThatCode(() -> listener.consumeAssignment("{ invalid json !!"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("consumeAssignment() increments parse_error metric on malformed JSON")
    void consumeAssignment_malformedJson_incrementsParseErrorMetric() {
        double before = meterRegistry.counter("notification.parse_error.total").count();

        listener.consumeAssignment("not-json");

        double after = meterRegistry.counter("notification.parse_error.total").count();
        assertThat(after - before).isEqualTo(1.0);
    }

    @Test
    @DisplayName("consumeAssignment() increments consumed metric on valid JSON")
    void consumeAssignment_validJson_incrementsConsumedMetric() {
        String valid = """
            {"emergencyId":"EMG-N-001","ambulanceId":"AMB-101","distanceKm":3.2,"version":1}
            """;

        assertThatCode(() -> listener.consumeAssignment(valid))
                .doesNotThrowAnyException();

        double consumed = meterRegistry.counter("notification.assignments.consumed.total").count();
        assertThat(consumed).isEqualTo(1.0);
    }

    @Test
    @DisplayName("consumeAssignment() handles null message gracefully")
    void consumeAssignment_nullMessage_doesNotThrow() {
        assertThatCode(() -> listener.consumeAssignment(null))
                .doesNotThrowAnyException();
    }
}
