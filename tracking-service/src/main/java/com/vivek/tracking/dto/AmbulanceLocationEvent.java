package com.vivek.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmbulanceLocationEvent {
	private String ambulanceId;
	private double latitude;
	private double longitude;
	private double speed;
	private double heading;
	private long timestamp;
}
