package com.vivek.dispatch.dto;

import lombok.Data;

@Data
public class AmbulanceLocationEvent {
	private String ambulanceId;
	private double lat;
	private double lon;
}
