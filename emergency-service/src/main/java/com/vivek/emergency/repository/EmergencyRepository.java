package com.vivek.emergency.repository;

import com.vivek.emergency.entity.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    
    Optional<Emergency> findByEmergencyId(String emergencyId);
    
    List<Emergency> findByStatus(String status);
    
    @Query("SELECT e FROM Emergency e WHERE e.status = :status " +
           "ORDER BY CASE e.priority " +
           "WHEN 'HIGH' THEN 1 " +
           "WHEN 'MEDIUM' THEN 2 " +
           "WHEN 'LOW' THEN 3 " +
           "ELSE 4 END, e.createdAt ASC")
    List<Emergency> findPendingEmergenciesByPriority(@Param("status") String status);
    
    @Query("SELECT COUNT(e) FROM Emergency e WHERE e.status = :status")
    long countByStatus(@Param("status") String status);
}
