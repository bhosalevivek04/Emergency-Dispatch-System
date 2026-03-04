package com.vivek.emergency.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

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
	/**
	 * Business-facing identifier. Optional in requests; if absent the service
	 * will generate one. Validation is performed downstream, not here, so we
	 * don't annotate with @NotBlank.
	 */
	private String emergencyId;

	// The JSON payload sent by the frontend will normally use the more
	// descriptive names "latitude"/"longitude". We want to accept those,
	// but *serialize* events to Kafka using the legacy keys lat/lon so the
	// dispatch service (which still expects the old schema) continues to work.
	// Jackson annotations accomplish both goals.
	@JsonAlias({ "lat", "latitude" })
	@JsonProperty("lat")
	@DecimalMin(value = "-90.0", message = "lat must be >= -90")
	@DecimalMax(value = "90.0", message = "lat must be <= 90")
	private double latitude;

	@JsonAlias({ "lon", "longitude" })
	@JsonProperty("lon")
	@DecimalMin(value = "-180.0", message = "lon must be >= -180")
	@DecimalMax(value = "180.0", message = "lon must be <= 180")
	private double longitude;

	@NotBlank(message = "priority is required")
	@Pattern(regexp = "HIGH|MEDIUM|LOW", message = "priority must be HIGH, MEDIUM or LOW")
	private String priority;
}
