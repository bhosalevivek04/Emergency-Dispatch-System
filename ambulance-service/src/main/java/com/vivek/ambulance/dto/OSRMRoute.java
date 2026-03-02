package com.vivek.ambulance.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OSRMRoute {
	private double distance; // meters
	private double duration; // seconds
	private Geometry geometry;
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geometry {
		private String type;
		private List<List<Double>> coordinates; // [[lon, lat], [lon, lat], ...]
	}
}
