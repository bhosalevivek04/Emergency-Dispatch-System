package com.vivek.ambulance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompletionEvent {
	private String ambulanceId;
	private String emergencyId;
	private String status;
	private long version;
}
