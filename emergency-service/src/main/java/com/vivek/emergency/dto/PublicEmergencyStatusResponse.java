package com.vivek.emergency.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicEmergencyStatusResponse {
	private String emergencyId;
	private String status;
	private String priority;
	private String assignedAmbulanceId;
	private Double latitude;
	private Double longitude;
	private String createdAt;
	private String updatedAt;
}
