package com.vivek.emergency.repository;

import com.vivek.emergency.entity.Emergency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class EmergencyRepositoryTest {

    @Autowired
    private EmergencyRepository emergencyRepository;

    @Test
    void shouldSaveEmergency() {
        // Given
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-TEST-001");
        emergency.setCoordinates(18.5204, 73.8567);
        emergency.setPriority("HIGH");
        emergency.setStatus("PENDING");
        emergency.setCallerPhone("+91-9876543210");
        emergency.setDescription("Test emergency");

        // When
        Emergency saved = emergencyRepository.save(emergency);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmergencyId()).isEqualTo("EMG-TEST-001");
        assertThat(saved.getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(18.5204));
        assertThat(saved.getLongitude()).isEqualByComparingTo(BigDecimal.valueOf(73.8567));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindByEmergencyId() {
        // Given
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-TEST-002");
        emergency.setCoordinates(18.5204, 73.8567);
        emergency.setPriority("MEDIUM");
        emergency.setStatus("PENDING");
        emergencyRepository.save(emergency);

        // When
        Optional<Emergency> found = emergencyRepository.findByEmergencyId("EMG-TEST-002");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmergencyId()).isEqualTo("EMG-TEST-002");
    }

    @Test
    void shouldFindByStatus() {
        // Given
        Emergency emergency1 = createEmergency("EMG-001", "PENDING");
        Emergency emergency2 = createEmergency("EMG-002", "ASSIGNED");
        Emergency emergency3 = createEmergency("EMG-003", "PENDING");
        
        emergencyRepository.saveAll(List.of(emergency1, emergency2, emergency3));

        // When
        List<Emergency> pending = emergencyRepository.findByStatus("PENDING");

        // Then
        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(Emergency::getStatus).containsOnly("PENDING");
    }

    @Test
    void shouldCountByStatus() {
        // Given
        emergencyRepository.saveAll(List.of(
            createEmergency("EMG-001", "PENDING"),
            createEmergency("EMG-002", "PENDING"),
            createEmergency("EMG-003", "ASSIGNED")
        ));

        // When
        long count = emergencyRepository.countByStatus("PENDING");

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldFindPendingEmergenciesByPriority() {
        // Given
        Emergency high = createEmergency("EMG-001", "PENDING");
        high.setPriority("HIGH");
        
        Emergency medium = createEmergency("EMG-002", "PENDING");
        medium.setPriority("MEDIUM");
        
        Emergency low = createEmergency("EMG-003", "PENDING");
        low.setPriority("LOW");
        
        emergencyRepository.saveAll(List.of(medium, low, high));

        // When
        List<Emergency> sorted = emergencyRepository.findPendingEmergenciesByPriority("PENDING");

        // Then
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).getPriority()).isEqualTo("HIGH");
        assertThat(sorted.get(1).getPriority()).isEqualTo("MEDIUM");
        assertThat(sorted.get(2).getPriority()).isEqualTo("LOW");
    }

    private Emergency createEmergency(String emergencyId, String status) {
        Emergency emergency = new Emergency();
        emergency.setEmergencyId(emergencyId);
        emergency.setCoordinates(18.5204, 73.8567);
        emergency.setPriority("MEDIUM");
        emergency.setStatus(status);
        return emergency;
    }
}
