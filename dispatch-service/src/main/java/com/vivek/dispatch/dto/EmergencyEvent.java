package com.vivek.dispatch.dto;

import lombok.Data;

@Data
public class EmergencyEvent {
	private String emergencyId;
	private double lat;
	private double lon;
	private String priority;
}
