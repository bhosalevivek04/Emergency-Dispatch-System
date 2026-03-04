package com.vivek.emergency.integration;

import com.vivek.emergency.config.TestKafkaConfig;
import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.repository.EmergencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestKafkaConfig.class)
class EmergencyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmergencyRepository emergencyRepository;

    @BeforeEach
    void setUp() {
        emergencyRepository.deleteAll();
    }

    @Test
    void shouldCreateEmergencyAndSaveToDatabase() {
        // Given
        EmergencyEvent event = new EmergencyEvent();
        event.setEmergencyId("EMG-INT-001");
        event.setLatitude(18.5204);
        event.setLongitude(73.8567);
        event.setPriority("HIGH");

        // When
        ResponseEntity<Emergency> response = restTemplate.postForEntity(
                "/emergency",
                event,
                Emergency.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmergencyId()).isEqualTo("EMG-INT-001");
        assertThat(response.getBody().getId()).isNotNull();

        // Verify in database
        Optional<Emergency> saved = emergencyRepository.findByEmergencyId("EMG-INT-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("PENDING");
        assertThat(saved.get().getPriority()).isEqualTo("HIGH");
    }

    @Test
    void shouldRetrieveEmergencyById() {
        // Given
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-INT-002");
        emergency.setCoordinates(18.5204, 73.8567);
        emergency.setPriority("MEDIUM");
        emergency.setStatus("PENDING");
        emergencyRepository.save(emergency);

        // When
        ResponseEntity<Emergency> response = restTemplate.getForEntity(
                "/emergency/EMG-INT-002",
                Emergency.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmergencyId()).isEqualTo("EMG-INT-002");
    }

    @Test
    void shouldReturnNotFoundForNonExistentEmergency() {
        // When
        ResponseEntity<Emergency> response = restTemplate.getForEntity(
                "/emergency/NON-EXISTENT",
                Emergency.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
