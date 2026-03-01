package com.vivek.dispatch.dto;

import lombok.Data;

@Data
public class CompletionEvent {
	private String ambulanceId;
	private String emergencyId;
	private String status;
	private long version;
}
