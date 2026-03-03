package com.vivek.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentAckEvent {
	private String emergencyId;
	private String ambulanceId;
	private String status; // "ACCEPTED" or "REJECTED"
	private long version;
	private long timestamp;
}
