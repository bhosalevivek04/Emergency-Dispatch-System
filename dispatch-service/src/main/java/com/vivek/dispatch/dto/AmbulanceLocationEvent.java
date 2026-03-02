package com.vivek.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AmbulanceLocationEvent {
	private String ambulanceId;
	private double latitude;
	private double longitude;
	private double speed;
	private double heading;
	private long timestamp;
}
