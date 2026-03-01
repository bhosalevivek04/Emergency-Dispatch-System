package com.vivek.ambulance.service;

import java.util.Random;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
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
			double lat = 18.5204 + (random.nextDouble() - 0.5) / 100;
			double lon = 73.8567 + (random.nextDouble() - 0.5) / 100;

			AmbulanceLocationEvent event = new AmbulanceLocationEvent(ambulanceId, lat, lon);
			producer.sendLocation(event);
			log.info("Location sent ambulanceId={} lat={} lon={}", event.getAmbulanceId(), event.getLat(),
					event.getLon());
		}
	}
}
