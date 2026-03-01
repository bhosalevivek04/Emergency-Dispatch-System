package com.vivek.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignmentEvent {
	private String emergencyId;
	private String ambulanceId;
	private double distanceKm;
	private long version;
}
