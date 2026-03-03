package com.vivek.ambulance.service;

import com.vivek.ambulance.entity.Ambulance;
import com.vivek.ambulance.repository.AmbulanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmbulancePersistenceService {
    
    private final AmbulanceRepository ambulanceRepository;
    
    @Transactional
    public Ambulance saveOrUpdate(Ambulance ambulance) {
        Optional<Ambulance> existing = ambulanceRepository.findByAmbulanceId(ambulance.getAmbulanceId());
        
        if (existing.isPresent()) {
            Ambulance existingAmbulance = existing.get();
            existingAmbulance.setLatitude(ambulance.getLatitude());
            existingAmbulance.setLongitude(ambulance.getLongitude());
            existingAmbulance.setLocation(ambulance.getLocation());
            existingAmbulance.setStatus(ambulance.getStatus());
            existingAmbulance.setAssignedEmergencyId(ambulance.getAssignedEmergencyId());
            existingAmbulance.setLastLocationUpdate(ambulance.getLastLocationUpdate());
            
            log.debug("Updating ambulance in PostgreSQL: {}", ambulance.getAmbulanceId());
            return ambulanceRepository.save(existingAmbulance);
        } else {
            log.info("Creating new ambulance in PostgreSQL: {}", ambulance.getAmbulanceId());
            return ambulanceRepository.save(ambulance);
        }
    }
    
    @Transactional
    public void updateStatus(String ambulanceId, String status) {
        ambulanceRepository.findByAmbulanceId(ambulanceId).ifPresent(ambulance -> {
            ambulance.setStatus(status);
            ambulanceRepository.save(ambulance);
            log.debug("Updated ambulance status in PostgreSQL: {} -> {}", ambulanceId, status);
        });
    }
    
    @Transactional
    public void updateLocation(String ambulanceId, double lat, double lon) {
        ambulanceRepository.findByAmbulanceId(ambulanceId).ifPresent(ambulance -> {
            ambulance.setCoordinates(lat, lon);
            ambulanceRepository.save(ambulance);
            log.debug("Updated ambulance location in PostgreSQL: {}", ambulanceId);
        });
    }
    
    @Transactional
    public void assignEmergency(String ambulanceId, String emergencyId) {
        ambulanceRepository.findByAmbulanceId(ambulanceId).ifPresent(ambulance -> {
            ambulance.setAssignedEmergencyId(emergencyId);
            ambulance.setStatus("ASSIGNED");
            ambulanceRepository.save(ambulance);
            log.info("Assigned emergency to ambulance in PostgreSQL: {} -> {}", ambulanceId, emergencyId);
        });
    }
    
    public Optional<Ambulance> findByAmbulanceId(String ambulanceId) {
        return ambulanceRepository.findByAmbulanceId(ambulanceId);
    }
    
    public List<Ambulance> findAvailableAmbulances() {
        return ambulanceRepository.findAvailableAmbulances();
    }
    
    public List<Ambulance> findAll() {
        return ambulanceRepository.findAll();
    }
}
