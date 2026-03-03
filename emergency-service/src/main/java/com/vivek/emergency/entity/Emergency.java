package com.vivek.emergency.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Emergency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", unique = true, nullable = false, length = 50)
    private String emergencyId;
    
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;
    
    @Column(nullable = false, length = 20)
    private String priority;
    
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    
    @Column(name = "assigned_ambulance_id", length = 50)
    private String assignedAmbulanceId;
    
    @Column(name = "assignment_timestamp")
    private LocalDateTime assignmentTimestamp;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "caller_phone", length = 20)
    private String callerPhone;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper method to set lat/lon
    public void setCoordinates(double lat, double lon) {
        this.latitude = BigDecimal.valueOf(lat);
        this.longitude = BigDecimal.valueOf(lon);
    }
}
