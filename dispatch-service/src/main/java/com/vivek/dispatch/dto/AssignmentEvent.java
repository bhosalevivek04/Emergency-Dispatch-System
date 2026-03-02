package com.vivek.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentEvent {
	private String emergencyId;
	private String ambulanceId;
	private double distanceKm;
	private long version;
	private double emergencyLat;
	private double emergencyLon;
}
