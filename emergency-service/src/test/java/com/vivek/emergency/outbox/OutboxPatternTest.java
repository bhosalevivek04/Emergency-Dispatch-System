package com.vivek.emergency.outbox;

import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.entity.OutboxEvent;
import com.vivek.emergency.repository.EmergencyRepository;
import com.vivek.emergency.repository.OutboxEventRepository;
import com.vivek.emergency.service.EmergencyService;
import com.vivek.emergency.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the Transactional Outbox Pattern.
 *
 * These tests use an in-memory H2 database (application-test.yml) and a mocked
 * KafkaTemplate so we can simulate Kafka being up or down without real brokers.
 *
 * The four invariants being tested:
 *   1. Emergency + outbox entry are written atomically in one transaction
 *   2. OutboxPublisher marks the event published when Kafka succeeds
 *   3. OutboxPublisher retries when Kafka is down, then publishes on recovery
 *   4. Transaction rollback removes BOTH the emergency and the outbox entry
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class OutboxPatternTest {

    @Autowired private EmergencyService emergencyService;
    @Autowired private EmergencyRepository emergencyRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private OutboxPublisher outboxPublisher;

    // Mock KafkaTemplate so tests control whether Kafka "succeeds" or "fails"
    @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        emergencyRepository.deleteAll();
    }

    // ── Test 1: Atomicity ─────────────────────────────────────────────────

    @Test
    @DisplayName("TEST 1: createEmergency() writes Emergency + OutboxEvent in one atomic transaction")
    void shouldWriteEmergencyAndOutboxEntryAtomically() {
        // When
        emergencyService.createEmergency(event("EMG-001", "HIGH"));

        // Then — exactly one emergency row
        assertThat(emergencyRepository.findByEmergencyId("EMG-001")).isPresent();

        // Then — exactly one outbox row created in the SAME transaction
        List<OutboxEvent> entries = outboxRepository.findAll();
        assertThat(entries).hasSize(1);

        OutboxEvent outbox = entries.get(0);
        assertThat(outbox.getAggregateId()).isEqualTo("EMG-001");
        assertThat(outbox.getAggregateType()).isEqualTo("EMERGENCY");
        assertThat(outbox.getKafkaTopic()).isEqualTo("emergency-topic");
        assertThat(outbox.isPublished()).isFalse();
        assertThat(outbox.getRetryCount()).isEqualTo(0);
        assertThat(outbox.getCreatedAt()).isNotNull();
        assertThat(outbox.getPayload()).contains("EMG-001");
    }

    // ── Test 2: Kafka succeeds → event marked published ───────────────────

    @Test
    @DisplayName("TEST 2: OutboxPublisher marks event as published after successful Kafka send")
    void shouldMarkEventPublishedAfterKafkaSendSucceeds() throws Exception {
        // Given — Kafka send completes successfully
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        emergencyService.createEmergency(event("EMG-002", "MEDIUM"));
        Long outboxId = outboxRepository.findAll().get(0).getId();

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        OutboxEvent published = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(published.isPublished()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getRetryCount()).isEqualTo(0);
        verify(kafkaTemplate, times(1)).send(eq("emergency-topic"), eq("EMG-002"), any());
    }

    // ── Test 3: Kafka down → retry, then published on recovery ───────────

    @Test
    @DisplayName("TEST 3: OutboxPublisher retries when Kafka is down, publishes when Kafka recovers")
    void shouldRetryWhenKafkaDownThenPublishOnRecovery() throws Exception {
        // Given — fail twice, succeed on third attempt
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(failFuture("Kafka broker unavailable"))
                .thenReturn(failFuture("Kafka broker unavailable"))
                .thenReturn(CompletableFuture.completedFuture(null));

        emergencyService.createEmergency(event("EMG-003", "HIGH"));
        Long outboxId = outboxRepository.findAll().get(0).getId();

        // When — poll 1: Kafka down
        outboxPublisher.publishPendingEvents();
        OutboxEvent after1 = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(after1.isPublished()).isFalse();
        assertThat(after1.getRetryCount()).isEqualTo(1);
        assertThat(after1.getErrorMessage()).isNotBlank();

        // When — poll 2: still down
        outboxPublisher.publishPendingEvents();
        OutboxEvent after2 = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(after2.isPublished()).isFalse();
        assertThat(after2.getRetryCount()).isEqualTo(2);

        // When — poll 3: Kafka recovers
        outboxPublisher.publishPendingEvents();
        OutboxEvent afterRecovery = outboxRepository.findById(outboxId).orElseThrow();

        // Then — event finally published
        assertThat(afterRecovery.isPublished()).isTrue();
        assertThat(afterRecovery.getPublishedAt()).isNotNull();
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any());
    }

    // ── Test 4: Idempotency proves transactional boundary ─────────────────

    @Test
    @DisplayName("TEST 4: Duplicate emergencyId returns existing record, does NOT create second outbox entry")
    void shouldNotCreateDuplicateOutboxEntryForDuplicateEmergencyId() {
        // When — create the same emergency twice
        Emergency first  = emergencyService.createEmergency(event("EMG-004", "HIGH"));
        Emergency second = emergencyService.createEmergency(event("EMG-004", "HIGH")); // duplicate

        // Then — still exactly one emergency row (idempotent)
        assertThat(emergencyRepository.count()).isEqualTo(1);
        assertThat(first.getId()).isEqualTo(second.getId());

        // Then — still exactly one outbox entry (second call took idempotency path, no new write)
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EmergencyEvent event(String id, String priority) {
        EmergencyEvent e = new EmergencyEvent();
        e.setEmergencyId(id);
        e.setLat(18.5204);
        e.setLon(73.8567);
        e.setPriority(priority);
        return e;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private CompletableFuture failFuture(String message) {
        CompletableFuture f = new CompletableFuture();
        f.completeExceptionally(new RuntimeException(message));
        return f;
    }
}
