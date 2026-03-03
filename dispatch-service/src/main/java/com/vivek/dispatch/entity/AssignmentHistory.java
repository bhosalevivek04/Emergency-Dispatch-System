package com.vivek.dispatch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_history", indexes = {
    @Index(name = "idx_emergency_id", columnList = "emergency_id"),
    @Index(name = "idx_ambulance_id", columnList = "ambulance_id"),
    @Index(name = "idx_assigned_at", columnList = "assigned_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", nullable = false, length = 50)
    private String emergencyId;
    
    @Column(name = "ambulance_id", nullable = false, length = 50)
    private String ambulanceId;
    
    @Column(name = "assignment_type", length = 20, nullable = false)
    private String assignmentType = "INITIAL";
    
    @Column(name = "assignment_status", length = 20, nullable = false)
    private String assignmentStatus = "ACCEPTED";
    
    @Column(name = "distance_km")
    private Double distanceKm;
    
    @Column(name = "estimated_eta_seconds")
    private Integer estimatedEtaSeconds;
    
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "assignment_version")
    private Integer assignmentVersion;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
    
    public void markCompleted() {
        this.completedAt = LocalDateTime.now();
        this.assignmentStatus = "COMPLETED";
    }
}
