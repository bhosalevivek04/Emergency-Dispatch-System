package com.vivek.ambulance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ambulances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ambulance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ambulance_id", unique = true, nullable = false, length = 50)
    private String ambulanceId;
    
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;
    
    @Column(nullable = true)
    @JsonIgnore
    private Point location;
    
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";
    
    @Column(name = "assigned_emergency_id", length = 50)
    private String assignedEmergencyId;
    
    @Column(name = "driver_name", length = 100)
    private String driverName;
    
    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastLocationUpdate = LocalDateTime.now();
        
        if (latitude != null && longitude != null && location == null) {
            try {
                location = createPoint(latitude, longitude);
            } catch (Exception e) {
                // Ignore in test environment
            }
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    private Point createPoint(BigDecimal lat, BigDecimal lon) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        return geometryFactory.createPoint(
            new Coordinate(lon.doubleValue(), lat.doubleValue())
        );
    }
    
    public void setCoordinates(double lat, double lon) {
        this.latitude = BigDecimal.valueOf(lat);
        this.longitude = BigDecimal.valueOf(lon);
        try {
            this.location = createPoint(this.latitude, this.longitude);
        } catch (Exception e) {
            // Ignore in test environment
        }
        this.lastLocationUpdate = LocalDateTime.now();
    }
}
