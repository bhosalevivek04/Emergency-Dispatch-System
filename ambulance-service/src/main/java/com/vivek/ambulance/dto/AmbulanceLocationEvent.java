package com.vivek.ambulance.dto;

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
	
	// Route visualization fields for frontend
	private double[][] routeCoordinates; // Full route as [[lat, lon], [lat, lon], ...]
	private Double progress; // Progress along route (0.0 to 1.0)
}
