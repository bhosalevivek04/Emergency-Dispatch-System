package com.vivek.ambulance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmbulanceLocationEvent {
	private String ambulanceId;
	private double lat;
	private double lon;
}
