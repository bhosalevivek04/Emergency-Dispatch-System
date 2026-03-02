package com.vivek.ambulance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmbulanceRegistrationRequest {
	private String ambulanceId;
	private Double latitude;
	private Double longitude;
	private String status;
}
