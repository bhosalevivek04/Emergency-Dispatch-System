package com.vivek.emergency.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmergencyEvent {
	@NotBlank(message = "emergencyId is required")
	private String emergencyId;

	@DecimalMin(value = "-90.0", message = "lat must be >= -90")
	@DecimalMax(value = "90.0", message = "lat must be <= 90")
	private double lat;

	@DecimalMin(value = "-180.0", message = "lon must be >= -180")
	@DecimalMax(value = "180.0", message = "lon must be <= 180")
	private double lon;

	@NotBlank(message = "priority is required")
	@Pattern(regexp = "HIGH|MEDIUM|LOW", message = "priority must be HIGH, MEDIUM or LOW")
	private String priority;
}
