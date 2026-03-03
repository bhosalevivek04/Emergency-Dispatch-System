package com.vivek.ambulance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.ambulance.service.AmbulanceStateTracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/diagnostic")
@RequiredArgsConstructor
@Slf4j
public class DiagnosticController {

    private final AmbulanceStateTracker stateTracker;

    @PostMapping("/init-fleet")
    public ResponseEntity<String> initializeFleet() {
        try {
            log.info("Manual fleet initialization triggered via REST endpoint");
            stateTracker.initializeAmbulances();
            return ResponseEntity.ok("Fleet initialized successfully");
        } catch (Exception e) {
            log.error("Manual fleet initialization failed", e);
            return ResponseEntity.internalServerError()
                .body("Fleet initialization failed: " + e.getMessage());
        }
    }
    
    @GetMapping("/fleet-status")
    public ResponseEntity<String> getFleetStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Ambulance Fleet Status:\n");
            
            String[] ambulances = {"AMB-101", "AMB-102", "AMB-103"};
            for (String ambulanceId : ambulances) {
                try {
                    var ambulanceStatus = stateTracker.getStatus(ambulanceId);
                    var version = stateTracker.getVersion(ambulanceId);
                    status.append(String.format("%s: status=%s, version=%d\n", 
                        ambulanceId, ambulanceStatus, version));
                } catch (Exception e) {
                    status.append(String.format("%s: ERROR - %s\n", 
                        ambulanceId, e.getMessage()));
                }
            }
            
            return ResponseEntity.ok(status.toString());
        } catch (Exception e) {
            log.error("Failed to get fleet status", e);
            return ResponseEntity.internalServerError()
                .body("Failed to get fleet status: " + e.getMessage());
        }
    }
}
