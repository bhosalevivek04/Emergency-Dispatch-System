package com.vivek.emergency.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.emergency.service.EmergencyService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmergencyStatusListener {

    private final EmergencyService emergencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Listens to ambulance-assigned-topic to update emergency status to ASSIGNED
     */
    @KafkaListener(topics = "ambulance-assigned-topic", groupId = "emergency-status-group")
    public void handleAssignment(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String emergencyId = node.get("emergencyId").asText();
            String ambulanceId = node.get("ambulanceId").asText();
            
            emergencyService.updateStatus(emergencyId, "ASSIGNED", ambulanceId);
            meterRegistry.counter("emergency.status.updated.total", "status", "ASSIGNED").increment();
            
            log.info("Emergency status updated to ASSIGNED: emergencyId={} ambulanceId={}", 
                emergencyId, ambulanceId);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("emergency.status.parse_error.total").increment();
            log.error("Failed to parse assignment message: {}", message, e);
        } catch (Exception e) {
            meterRegistry.counter("emergency.status.update_error.total").increment();
            log.error("Failed to update emergency status: {}", message, e);
        }
    }

    /**
     * Listens to ambulance-completed-topic to update emergency status to COMPLETED
     */
    @KafkaListener(topics = "ambulance-completed-topic", groupId = "emergency-status-group")
    public void handleCompletion(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String emergencyId = node.get("emergencyId").asText();
            String ambulanceId = node.get("ambulanceId").asText();
            
            emergencyService.updateStatus(emergencyId, "COMPLETED", ambulanceId);
            meterRegistry.counter("emergency.status.updated.total", "status", "COMPLETED").increment();
            
            log.info("Emergency status updated to COMPLETED: emergencyId={} ambulanceId={}", 
                emergencyId, ambulanceId);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("emergency.status.parse_error.total").increment();
            log.error("Failed to parse completion message: {}", message, e);
        } catch (Exception e) {
            meterRegistry.counter("emergency.status.update_error.total").increment();
            log.error("Failed to update emergency status: {}", message, e);
        }
    }
}
