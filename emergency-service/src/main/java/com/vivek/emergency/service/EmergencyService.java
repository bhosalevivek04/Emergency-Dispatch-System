package com.vivek.emergency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.entity.OutboxEvent;
import com.vivek.emergency.repository.EmergencyRepository;
import com.vivek.emergency.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyService {
    
    private static final String EMERGENCY_TOPIC = "emergency-topic";
    
    private final EmergencyRepository emergencyRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    /**
     * Saves emergency + outbox entry in ONE transaction.
     * The OutboxPublisher will pick up the outbox entry and push to Kafka.
     * This guarantees no emergency is ever saved to DB but silently lost from Kafka.
     */
    @Transactional
    public Emergency createEmergency(EmergencyEvent event) {
        log.info("Creating emergency: {}", event.getEmergencyId());

        // Check for duplicates
        if (emergencyRepository.findByEmergencyId(event.getEmergencyId()).isPresent()) {
            log.warn("Duplicate emergencyId received, returning existing: {}", event.getEmergencyId());
            return emergencyRepository.findByEmergencyId(event.getEmergencyId()).get();
        }

        // 1. Save emergency to PostgreSQL
        Emergency emergency = new Emergency();
        emergency.setEmergencyId(event.getEmergencyId());
        emergency.setCoordinates(event.getLat(), event.getLon());
        emergency.setPriority(event.getPriority());
        emergency.setStatus("PENDING");
        Emergency saved = emergencyRepository.save(emergency);

        // 2. Write outbox entry in THE SAME TRANSACTION — atomic with the save above
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.of("EMERGENCY", saved.getEmergencyId(), EMERGENCY_TOPIC, payload);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            // Throwing here rolls back BOTH the emergency save and the outbox write — correct behaviour
            throw new RuntimeException("Failed to serialize emergency event for outbox: " + event.getEmergencyId(), e);
        }

        meterRegistry.counter("emergency.created.total").increment();
        log.info("Emergency + outbox entry saved atomically: {}", saved.getEmergencyId());
        return saved;
    }
    
    @Transactional
    public void updateStatus(String emergencyId, String status, String assignedAmbulanceId) {
        emergencyRepository.findByEmergencyId(emergencyId).ifPresentOrElse(e -> {
            e.setStatus(status);
            if (assignedAmbulanceId != null) {
                e.setAssignedAmbulanceId(assignedAmbulanceId);
                if ("ASSIGNED".equals(status)) {
                    e.setAssignmentTimestamp(java.time.LocalDateTime.now());
                }
            }
            if ("COMPLETED".equals(status)) {
                e.setCompletedAt(java.time.LocalDateTime.now());
            }
            emergencyRepository.save(e);
            log.info("Emergency status updated: {} -> {}", emergencyId, status);
        }, () -> log.warn("Emergency not found for status update: {}", emergencyId));
    }
    
    public Optional<Emergency> findByEmergencyId(String emergencyId) {
        return emergencyRepository.findByEmergencyId(emergencyId);
    }
    
    public List<Emergency> findByStatus(String status) {
        return emergencyRepository.findByStatus(status);
    }
    
    public List<Emergency> findPendingEmergenciesByPriority() {
        return emergencyRepository.findPendingEmergenciesByPriority("PENDING");
    }
    
    public long countByStatus(String status) {
        return emergencyRepository.countByStatus(status);
    }
}
