package com.vivek.tracking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.tracking.service.TrackingCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for TrackingLocationListener Kafka exception handling.
 *
 * This topic fires ~1 message/second per ambulance. A single bad message that
 * causes an infinite retry loop would block the consumer thread and stall all
 * location updates for every ambulance — hence the strict isolation requirement.
 */
class TrackingLocationListenerTest {

    private TrackingLocationListener listener;
    private TrackingCacheService cacheService;
    private SimpMessagingTemplate messagingTemplate;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        cacheService      = mock(TrackingCacheService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        meterRegistry     = new SimpleMeterRegistry();

        listener = new TrackingLocationListener(
                new ObjectMapper(), cacheService, messagingTemplate, meterRegistry);
    }

    @Test
    @DisplayName("consumeAmbulanceLocation() does NOT throw on malformed JSON")
    void consumeAmbulanceLocation_malformedJson_doesNotThrow() {
        assertThatCode(() -> listener.consumeAmbulanceLocation("{ bad json"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("consumeAmbulanceLocation() does NOT call cache or WebSocket on malformed JSON")
    void consumeAmbulanceLocation_malformedJson_noDownstreamCalls() {
        listener.consumeAmbulanceLocation("not-valid-json");

        verify(cacheService, never()).saveLatest(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    @DisplayName("consumeAmbulanceLocation() increments parse_error metric on bad JSON")
    void consumeAmbulanceLocation_malformedJson_incrementsParseErrorMetric() {
        double before = meterRegistry.counter("tracking.location.parse_error.total").count();

        listener.consumeAmbulanceLocation("totally-broken");

        double after = meterRegistry.counter("tracking.location.parse_error.total").count();
        assertThat(after - before).isEqualTo(1.0);
    }

    @Test
    @DisplayName("consumeAmbulanceLocation() processes valid JSON, calls cache and WebSocket")
    void consumeAmbulanceLocation_validJson_callsCacheAndWebSocket() {
        String valid = """
            {
              "ambulanceId": "AMB-101",
              "latitude": 18.5204,
              "longitude": 73.8567,
              "speed": 45.0,
              "heading": 90.0,
              "timestamp": 1700000000000
            }
            """;

        assertThatCode(() -> listener.consumeAmbulanceLocation(valid))
                .doesNotThrowAnyException();

        verify(cacheService, times(1)).saveLatest(any());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/location"), (Object) any());

        double consumed = meterRegistry.counter("tracking.location.consumed.total").count();
        assertThat(consumed).isEqualTo(1.0);
    }

    @Test
    @DisplayName("consumeAmbulanceLocation() continues broadcasting even if Redis cache fails")
    void consumeAmbulanceLocation_cacheFails_stillBroadcastsToWebSocket() {
        // Cache throws but WebSocket must still get the message
        doThrow(new RuntimeException("Redis connection refused"))
                .when(cacheService).saveLatest(any());

        String valid = """
            {"ambulanceId":"AMB-102","latitude":18.5,"longitude":73.8,
             "speed":30.0,"heading":180.0,"timestamp":1700000000000}
            """;

        assertThatCode(() -> listener.consumeAmbulanceLocation(valid))
                .doesNotThrowAnyException();

        // WebSocket broadcast must still happen despite cache failure
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/location"), (Object) any());
    }
}
