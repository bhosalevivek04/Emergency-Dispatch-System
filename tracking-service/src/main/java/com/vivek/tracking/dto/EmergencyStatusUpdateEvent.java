package com.vivek.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyStatusUpdateEvent {
	private String emergencyId;
	private String status;
	private String ambulanceId;
	private long timestamp;
}
