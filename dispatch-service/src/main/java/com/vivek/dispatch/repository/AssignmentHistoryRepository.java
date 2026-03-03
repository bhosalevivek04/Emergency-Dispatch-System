package com.vivek.dispatch.repository;

import com.vivek.dispatch.entity.AssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {
    
    List<AssignmentHistory> findByEmergencyId(String emergencyId);
    
    List<AssignmentHistory> findByAmbulanceId(String ambulanceId);
    
    Optional<AssignmentHistory> findByEmergencyIdAndAssignmentStatus(String emergencyId, String assignmentStatus);
    
    @Query("SELECT a FROM AssignmentHistory a WHERE a.assignedAt >= :startDate ORDER BY a.assignedAt DESC")
    List<AssignmentHistory> findRecentAssignments(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(a) FROM AssignmentHistory a WHERE a.assignmentStatus = :assignmentStatus")
    long countByAssignmentStatus(@Param("assignmentStatus") String assignmentStatus);
    
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - assigned_at))) FROM assignment_history WHERE assignment_status = 'COMPLETED' AND completed_at IS NOT NULL", nativeQuery = true)
    Double getAverageResponseTime();
    
    @Query("SELECT a FROM AssignmentHistory a WHERE a.ambulanceId = :ambulanceId ORDER BY a.assignedAt DESC")
    List<AssignmentHistory> findByAmbulanceIdOrderByAssignedAtDesc(@Param("ambulanceId") String ambulanceId);
}
