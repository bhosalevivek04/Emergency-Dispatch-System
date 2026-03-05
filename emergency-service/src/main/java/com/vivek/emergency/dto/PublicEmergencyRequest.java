package com.vivek.emergency.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicEmergencyRequest {
	@DecimalMin(value = "-90.0", message = "latitude must be >= -90")
	@DecimalMax(value = "90.0", message = "latitude must be <= 90")
	private double latitude;

	@DecimalMin(value = "-180.0", message = "longitude must be >= -180")
	@DecimalMax(value = "180.0", message = "longitude must be <= 180")
	private double longitude;

	@NotBlank(message = "priority is required")
	@Pattern(regexp = "HIGH|MEDIUM|LOW", message = "priority must be HIGH, MEDIUM or LOW")
	private String priority;

	@Size(max = 20, message = "callerPhone max length is 20")
	private String callerPhone;

	@Size(max = 500, message = "description max length is 500")
	private String description;
}
