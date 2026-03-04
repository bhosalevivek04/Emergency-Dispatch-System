package com.vivek.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmbulanceProvisioningService {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${ambulance.service.url:http://localhost:8082}")
    private String ambulanceServiceUrl;

    public void ensureAmbulanceRegistered(String ambulanceId, String driverUsername) {
        if (ambulanceId == null || ambulanceId.isBlank()) {
            return;
        }

        RestTemplate restTemplate = restTemplateBuilder.build();
        String url = ambulanceServiceUrl + "/ambulance";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Username", "auth-service");
        headers.set("X-User-Roles", "ADMIN");

        Map<String, Object> payload = new HashMap<>();
        payload.put("ambulanceId", ambulanceId.trim());
        payload.put("latitude", 18.5204);
        payload.put("longitude", 73.8567);
        payload.put("status", "AVAILABLE");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Ambulance service returned non-success status");
            }
            log.info("Provisioned ambulance {} for driver {}", ambulanceId, driverUsername);
        } catch (RestClientException e) {
            log.error("Failed to provision ambulance {} for driver {}: {}", ambulanceId, driverUsername, e.getMessage());
            throw new RuntimeException("Failed to provision ambulance. Please try again.");
        }
    }
}
