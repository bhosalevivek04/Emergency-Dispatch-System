package com.vivek.emergency.controller;

import jakarta.validation.Valid;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.service.EmergencyProducer;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
public class EmergencyController {
	private final EmergencyProducer producer;
	private final MeterRegistry meterRegistry;

	@PostMapping
	public String createEmergency(@Valid @RequestBody EmergencyEvent event) {
		meterRegistry.counter("emergency.requests.total").increment();
		producer.sendEmergency(event);
		return "Emergency event sent successfully!";
	}
}
