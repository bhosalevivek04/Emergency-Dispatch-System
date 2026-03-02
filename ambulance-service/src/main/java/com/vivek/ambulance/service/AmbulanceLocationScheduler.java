package com.vivek.ambulance.service;

import java.util.Random;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ambulance.scheduler.enabled", havingValue = "false")
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class AmbulanceLocationScheduler {
	private final AmbulanceProducer producer;
	private final Random random = new Random();
	private static final String[] AMBULANCES = { "A1", "A2", "A3" };

	@Scheduled(fixedRate = 5000)
	public void sendLocationUpdates() {
		for (String ambulanceId : AMBULANCES) {
			double latitude = 18.5204 + (random.nextDouble() - 0.5) / 100;
			double longitude = 73.8567 + (random.nextDouble() - 0.5) / 100;

			AmbulanceLocationEvent event = AmbulanceLocationEvent.builder()
					.ambulanceId(ambulanceId)
					.latitude(latitude)
					.longitude(longitude)
					.speed(50.0)
					.heading(0.0)
					.timestamp(System.currentTimeMillis())
					.build();
			producer.sendLocation(event);
			log.info("Location sent ambulanceId={} latitude={} longitude={}", event.getAmbulanceId(),
					event.getLatitude(),
					event.getLongitude());
		}
	}
}
