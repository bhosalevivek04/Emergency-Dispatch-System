package com.vivek.emergency.service;

import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.repository.EmergencyRepository;
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
    
    private final EmergencyRepository emergencyRepository;
    private final EmergencyProducer emergencyProducer;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public Emergency createEmergency(EmergencyEvent event) {
        log.info("Creating emergency: {}", event.getEmergencyId());
        
        // 1. Save to PostgreSQL
        Emergency emergency = new Emergency();
        emergency.setEmergencyId(event.getEmergencyId());
        emergency.setCoordinates(event.getLat(), event.getLon());
        emergency.setPriority(event.getPriority());
        emergency.setStatus("PENDING");
        
        Emergency saved = emergencyRepository.save(emergency);
        log.info("Emergency saved to PostgreSQL: {}", saved.getId());
        
        // 2. Publish to Kafka (dual-write pattern)
        try {
            emergencyProducer.sendEmergency(event);
            log.info("Emergency published to Kafka: {}", event.getEmergencyId());
        } catch (Exception e) {
            log.error("Failed to publish emergency to Kafka: {}", event.getEmergencyId(), e);
            meterRegistry.counter("emergency.kafka.publish.failed").increment();
            // Note: In Phase 2, we'll use outbox pattern to handle this properly
        }
        
        meterRegistry.counter("emergency.created.total").increment();
        return saved;
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
