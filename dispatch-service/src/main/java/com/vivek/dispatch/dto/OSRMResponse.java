package com.vivek.dispatch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OSRMResponse {
	private String code;
	private List<OSRMRoute> routes;
}
