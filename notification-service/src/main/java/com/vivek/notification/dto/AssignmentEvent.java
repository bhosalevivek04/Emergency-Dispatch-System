package com.vivek.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignmentEvent {
	private String emergencyId;
	private String ambulanceId;
	private double distanceKm;
	private long version;
}
