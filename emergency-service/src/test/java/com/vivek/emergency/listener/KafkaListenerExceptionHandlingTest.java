package com.vivek.emergency.listener;

import com.vivek.emergency.service.EmergencyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TEST 5: Malformed JSON must be silently discarded by all @KafkaListener methods.
 *
 * If a listener rethrows JsonProcessingException, Spring Kafka enters an infinite
 * retry loop for that message — especially catastrophic on the high-frequency
 * ambulance-location-topic (~1 msg/sec per ambulance).
 *
 * This test verifies:
 * - consumeAssignment() does NOT throw on malformed JSON
 * - consumeAssignment() does NOT call any downstream service on malformed JSON
 * - consumeAssignment() DOES call updateStatus() on valid JSON
 * - The parse_error metric is incremented on bad JSON
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class KafkaListenerExceptionHandlingTest {

    @Autowired private EmergencyStatusListener emergencyStatusListener;
    @MockBean  private EmergencyService emergencyService;
    @MockBean  private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private MeterRegistry meterRegistry;

    // ── Assignment listener ───────────────────────────────────────────────

    @Test
    @DisplayName("TEST 5a: EmergencyStatusListener.handleAssignment() swallows malformed JSON")
    void handleAssignment_malformedJson_doesNotThrow() {
        assertThatCode(() -> emergencyStatusListener.handleAssignment("{ broken !! }"))
                .doesNotThrowAnyException();

        verify(emergencyService, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("TEST 5b: EmergencyStatusListener.handleAssignment() processes valid JSON correctly")
    void handleAssignment_validJson_callsUpdateStatus() {
        String json = """
            {"emergencyId":"EMG-005","ambulanceId":"AMB-101","distanceKm":1.5,"version":1}
            """;

        assertThatCode(() -> emergencyStatusListener.handleAssignment(json))
                .doesNotThrowAnyException();

        verify(emergencyService).updateStatus("EMG-005", "ASSIGNED", "AMB-101");
    }

    @Test
    @DisplayName("TEST 5c: EmergencyStatusListener.handleCompletion() swallows malformed JSON")
    void handleCompletion_malformedJson_doesNotThrow() {
        assertThatCode(() -> emergencyStatusListener.handleCompletion("not-json-at-all"))
                .doesNotThrowAnyException();

        verify(emergencyService, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("TEST 5d: EmergencyStatusListener.handleCompletion() processes valid JSON correctly")
    void handleCompletion_validJson_updatesStatusToCompleted() {
        String json = """
            {"emergencyId":"EMG-006","ambulanceId":"AMB-102","status":"COMPLETED","version":5}
            """;

        assertThatCode(() -> emergencyStatusListener.handleCompletion(json))
                .doesNotThrowAnyException();

        verify(emergencyService).updateStatus("EMG-006", "COMPLETED", "AMB-102");
    }

    @Test
    @DisplayName("TEST 5e: parse_error metric is incremented when malformed JSON is received")
    void handleAssignment_malformedJson_incrementsParseErrorMetric() {
        double before = counter("emergency.status.parse_error.total");

        emergencyStatusListener.handleAssignment("{ bad json");

        assertThat(counter("emergency.status.parse_error.total") - before).isEqualTo(1.0);
    }

    private double counter(String name) {
        try { return meterRegistry.counter(name).count(); }
        catch (Exception e) { return 0.0; }
    }
}
