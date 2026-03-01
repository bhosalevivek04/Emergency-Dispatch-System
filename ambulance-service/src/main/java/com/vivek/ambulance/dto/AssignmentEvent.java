package com.vivek.ambulance.dto;

import lombok.Data;

@Data
public class AssignmentEvent {
	private String emergencyId;
	private String ambulanceId;
	private double distanceKm;
	private long version;
}
