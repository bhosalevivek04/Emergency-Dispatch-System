package com.vivek.ambulance.repository;

import com.vivek.ambulance.entity.Ambulance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmbulanceRepository extends JpaRepository<Ambulance, Long> {
    
    Optional<Ambulance> findByAmbulanceId(String ambulanceId);
    
    List<Ambulance> findByStatus(String status);
    
    @Query("SELECT a FROM Ambulance a WHERE a.status = 'AVAILABLE' ORDER BY a.lastLocationUpdate DESC")
    List<Ambulance> findAvailableAmbulances();
    
    @Query("SELECT COUNT(a) FROM Ambulance a WHERE a.status = :status")
    long countByStatus(@Param("status") String status);
    
    Optional<Ambulance> findByAssignedEmergencyId(String emergencyId);
}
