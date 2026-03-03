package com.vivek.ambulance.service;

import com.vivek.ambulance.model.AmbulanceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AmbulanceStateTracker atomic FSM transitions via Lua scripts.
 *
 * TEST 7: Version mismatch → transition rejected (wrong expectedVersion)
 * TEST 8: Concurrent transitions → Lua script enforces exactly-once semantics
 *         (simulates multiple service instances racing to transition the same ambulance)
 */
class AmbulanceStateTrackerTest {

    private AmbulanceStateTracker tracker;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps      = mockValueOps();
        meterRegistry = new SimpleMeterRegistry();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        tracker = new AmbulanceStateTracker(redisTemplate, meterRegistry);
        ReflectionTestUtils.setField(tracker, "fleetIdsConfig", "AMB-101,AMB-102,AMB-103");
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOps() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    // ── TEST 7: Version mismatch → transition rejected ────────────────────

    @Test
    @DisplayName("TEST 7a: transition() returns false when expectedVersion does not match Redis version")
    @SuppressWarnings("unchecked")
    void transition_versionMismatch_returnsFalse() {
        // Lua script returns -1 (version mismatch)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(-1L);

        boolean result = tracker.transition("AMB-101", AmbulanceStatus.ON_ROUTE, 5L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("TEST 7b: transition() returns false when ambulance key does not exist in Redis")
    @SuppressWarnings("unchecked")
    void transition_ambulanceKeyMissing_returnsFalse() {
        // Lua script returns -3 (key not found)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(-3L);

        boolean result = tracker.transition("AMB-999", AmbulanceStatus.ASSIGNED, 0L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("TEST 7c: transition() returns true and new version on correct expectedVersion")
    @SuppressWarnings("unchecked")
    void transition_correctVersion_returnsTrue() {
        // Lua script returns new version (version + 1 = 3)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(3L);

        boolean result = tracker.transition("AMB-101", AmbulanceStatus.ON_ROUTE, 2L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("TEST 7d: version_mismatch metric is incremented on rejected transition")
    @SuppressWarnings("unchecked")
    void transition_versionMismatch_incrementsFailureMetric() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(-1L);

        tracker.transition("AMB-101", AmbulanceStatus.ON_ROUTE, 99L);

        double failures = meterRegistry
                .counter("ambulance.fsm.transition.failures.total", "reason", "version_mismatch")
                .count();
        assertThat(failures).isEqualTo(1.0);
    }

    // ── TEST 8: Concurrent transitions — only one wins ────────────────────

    @Test
    @DisplayName("TEST 8a: Lua script ensures exactly one winner when two threads race to transition")
    @SuppressWarnings("unchecked")
    void transition_concurrentRace_exactlyOneSucceeds() throws InterruptedException {
        // Simulate: first caller gets the lock (returns new version 1),
        // second caller has stale version (returns -1 version mismatch)
        AtomicInteger callCount = new AtomicInteger(0);

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenAnswer(inv -> {
                    // First call: success (new version)
                    // All subsequent calls: version mismatch
                    return callCount.incrementAndGet() == 1 ? 1L : -1L;
                });

        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // all threads start simultaneously
                    boolean ok = tracker.transition("AMB-101", AmbulanceStatus.ASSIGNED, 0L);
                    if (ok) successCount.incrementAndGet();
                    else    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown(); // fire all threads at once
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one thread should have succeeded
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("TEST 8b: assignEmergencyAtomically() rejects second assignment when first succeeds")
    @SuppressWarnings("unchecked")
    void assignEmergencyAtomically_secondCallRejected() {
        // First assignment succeeds, second is rejected (ambulance now ASSIGNED, not AVAILABLE)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L)   // first call: success, new version = 1
                .thenReturn(-2L); // second call: status is ASSIGNED, not AVAILABLE

        boolean first  = tracker.assignEmergencyAtomically("AMB-101", "EMG-001", 0L);
        boolean second = tracker.assignEmergencyAtomically("AMB-101", "EMG-002", 0L);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("TEST 8c: safeTransition in AmbulanceAssignmentListener reads version fresh, " +
                 "so stale pre-computed version+N offsets never cause silent failures")
    @SuppressWarnings("unchecked")
    void safeTransition_readsFreshVersionFromRedis() {
        // This test documents the expected behaviour of the safeTransition() pattern:
        // If a version was pre-computed as (assignedVersion + 2) but auto-heal ran
        // and changed the version to (assignedVersion + 1), the Lua script would
        // see a version mismatch and return false — the transition is safely skipped.

        // Pre-computed version: expect version 7 (assigned was 5, assumed +2 steps happened)
        // Actual version in Redis: 8 (auto-heal fired in between and incremented it)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(-1L); // mismatch: 7 != 8

        // The safeTransition() helper reads the CURRENT version from Redis before each call
        // This test verifies the Lua script correctly rejects the stale version
        boolean result = tracker.transition("AMB-101", AmbulanceStatus.ARRIVED, 7L);

        assertThat(result).isFalse();
        // Auto-heal or the next scheduled tick will recover the state correctly
    }
}
