package com.vivek.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.dispatch.dto.EmergencyEvent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * TEST 6: When an assignment ACK comes back REJECTED, the emergency must be
 * re-enqueued to its correct priority queue — not orphaned.
 *
 * Before the fix, requeueEmergency() only deleted the idempotency key but the
 * emergency had already been dequeued from Redis. It was permanently lost.
 *
 * After the fix:
 *   - enqueueEmergency() stores a copy at  emergency:payload:{id}
 *   - requeueEmergency() retrieves that copy and re-pushes to the priority queue
 */
class DispatchEngineRequeueTest {

    private DispatchEngine dispatchEngine;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ListOperations<String, String> listOps;
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        redisTemplate  = mock(StringRedisTemplate.class);
        valueOps       = mockValueOps();
        listOps        = mockListOps();
        meterRegistry  = new SimpleMeterRegistry();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        // Mock dependencies - use proper generic types
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, com.vivek.dispatch.dto.AssignmentEvent> kafkaTemplate = 
            (KafkaTemplate<String, com.vivek.dispatch.dto.AssignmentEvent>) mock(KafkaTemplate.class);
        OSRMService osrmService = mock(OSRMService.class);
        AssignmentHistoryService historyService = mock(AssignmentHistoryService.class);

        // Use reflection to create DispatchEngine since it uses @RequiredArgsConstructor
        try {
            java.lang.reflect.Constructor<DispatchEngine> constructor = 
                DispatchEngine.class.getDeclaredConstructor(
                    KafkaTemplate.class,
                    StringRedisTemplate.class,
                    ObjectMapper.class,
                    MeterRegistry.class,
                    OSRMService.class,
                    AssignmentHistoryService.class
                );
            constructor.setAccessible(true);
            dispatchEngine = constructor.newInstance(
                kafkaTemplate,
                redisTemplate,
                objectMapper,
                meterRegistry,
                osrmService,
                historyService
            );
            
            // Manually call @PostConstruct method to initialize metrics
            java.lang.reflect.Method initMethod = DispatchEngine.class.getDeclaredMethod("initializeMetrics");
            initMethod.setAccessible(true);
            initMethod.invoke(dispatchEngine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DispatchEngine for testing", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOps() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private ListOperations<String, String> mockListOps() {
        return (ListOperations<String, String>) mock(ListOperations.class);
    }

    // ── Test 6a: Payload saved at enqueue time ────────────────────────────

    @Test
    @DisplayName("TEST 6a: enqueueEmergency() stores payload copy keyed by emergencyId")
    void enqueueEmergency_storesPayloadCopyInRedis() throws Exception {
        EmergencyEvent event = event("EMG-REQ-001", "HIGH");

        // Trigger via handleEmergency (the public entry point)
        when(valueOps.setIfAbsent(eq("idempotency:emergency:EMG-REQ-001"), any(), any()))
                .thenReturn(true);

        dispatchEngine.handleEmergency(event);

        // Verify payload saved with the correct key and 2h TTL
        verify(valueOps).set(
                eq("emergency:payload:EMG-REQ-001"),
                argThat((String payload) -> payload.contains("EMG-REQ-001")),
                eq(java.time.Duration.ofHours(2))
        );

        // Verify pushed to HIGH priority queue
        verify(listOps).rightPush(eq("dispatch:queue:HIGH"), anyString());
    }

    // ── Test 6b: Requeue retrieves payload and re-pushes to queue ─────────

    @Test
    @DisplayName("TEST 6b: requeueEmergency() re-pushes to correct priority queue after REJECTED ACK")
    void requeueEmergency_repushesToCorrectPriorityQueue() throws Exception {
        String payload = objectMapper.writeValueAsString(event("EMG-REQ-002", "HIGH"));

        // Payload is present in Redis (was stored at enqueue time)
        when(valueOps.get("emergency:payload:EMG-REQ-002")).thenReturn(payload);

        dispatchEngine.requeueEmergency("EMG-REQ-002");

        // Idempotency key must be cleared so the message can be re-processed
        verify(redisTemplate).delete("idempotency:emergency:EMG-REQ-002");

        // Emergency must be re-pushed to the HIGH priority queue
        verify(listOps).rightPush(eq("dispatch:queue:HIGH"), anyString());
    }

    @Test
    @DisplayName("TEST 6c: requeueEmergency() re-pushes to MEDIUM queue for MEDIUM priority")
    void requeueEmergency_respectsPriorityQueueOnRequeue() throws Exception {
        String payload = objectMapper.writeValueAsString(event("EMG-REQ-003", "MEDIUM"));
        when(valueOps.get("emergency:payload:EMG-REQ-003")).thenReturn(payload);

        dispatchEngine.requeueEmergency("EMG-REQ-003");

        verify(listOps).rightPush(eq("dispatch:queue:MEDIUM"), anyString());
    }

    // ── Test 6d: Payload not found → error metric, no exception ──────────

    @Test
    @DisplayName("TEST 6d: requeueEmergency() logs error and increments metric when payload not found")
    void requeueEmergency_payloadNotFound_incrementsLostMetric() {
        when(valueOps.get("emergency:payload:EMG-LOST-001")).thenReturn(null);

        // Must not throw — the dispatcher loop must continue running
        org.assertj.core.api.Assertions.assertThatCode(
                () -> dispatchEngine.requeueEmergency("EMG-LOST-001"))
                .doesNotThrowAnyException();

        // Emergency must NOT be pushed anywhere (there's nothing to requeue)
        verify(listOps, never()).rightPush(anyString(), anyString());

        // Lost metric must be recorded
        double lostCount = meterRegistry.counter("dispatch.emergencies.requeue.lost.total").count();
        assertThat(lostCount).isEqualTo(1.0);
    }

    private EmergencyEvent event(String id, String priority) {
        EmergencyEvent e = new EmergencyEvent();
        e.setEmergencyId(id);
        e.setLat(18.5204);
        e.setLon(73.8567);
        e.setPriority(priority);
        return e;
    }
}
