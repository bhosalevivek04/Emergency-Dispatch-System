package com.vivek.dispatch.service;

import com.vivek.dispatch.entity.AssignmentHistory;
import com.vivek.dispatch.repository.AssignmentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentHistoryService {
    
    private final AssignmentHistoryRepository assignmentHistoryRepository;
    
    @Transactional
    public AssignmentHistory recordAssignment(
            String emergencyId,
            String ambulanceId,
            double distanceKm,
            Integer assignmentVersion) {
        
        AssignmentHistory history = new AssignmentHistory();
        history.setEmergencyId(emergencyId);
        history.setAmbulanceId(ambulanceId);
        history.setDistanceKm(distanceKm);
        history.setAssignmentType("INITIAL");
        history.setAssignmentStatus("ACCEPTED");
        history.setAssignmentVersion(assignmentVersion);
        
        AssignmentHistory saved = assignmentHistoryRepository.save(history);
        log.info("Recorded assignment to PostgreSQL: emergency={} ambulance={} distance={}km", 
                emergencyId, ambulanceId, distanceKm);
        
        return saved;
    }
    
    @Transactional
    public void markCompleted(String emergencyId) {
        assignmentHistoryRepository.findByEmergencyIdAndAssignmentStatus(emergencyId, "ACCEPTED")
            .ifPresent(history -> {
                history.markCompleted();
                assignmentHistoryRepository.save(history);
                log.info("Marked assignment as completed: emergency={}", emergencyId);
            });
    }
    
    public List<AssignmentHistory> getAssignmentsByEmergency(String emergencyId) {
        return assignmentHistoryRepository.findByEmergencyId(emergencyId);
    }
    
    public List<AssignmentHistory> getAssignmentsByAmbulance(String ambulanceId) {
        return assignmentHistoryRepository.findByAmbulanceIdOrderByAssignedAtDesc(ambulanceId);
    }
    
    public List<AssignmentHistory> getRecentAssignments(int hours) {
        LocalDateTime startDate = LocalDateTime.now().minusHours(hours);
        return assignmentHistoryRepository.findRecentAssignments(startDate);
    }
    
    public Optional<Double> getAverageResponseTime() {
        return Optional.ofNullable(assignmentHistoryRepository.getAverageResponseTime());
    }
    
    public long countByAssignmentStatus(String assignmentStatus) {
        return assignmentHistoryRepository.countByAssignmentStatus(assignmentStatus);
    }
}
