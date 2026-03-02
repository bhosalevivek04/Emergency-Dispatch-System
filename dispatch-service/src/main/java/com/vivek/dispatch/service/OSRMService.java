package com.vivek.dispatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.vivek.dispatch.dto.OSRMResponse;
import com.vivek.dispatch.dto.OSRMRoute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class OSRMService {

	private final RestTemplate restTemplate;

	@Value("${osrm.base-url:http://router.project-osrm.org}")
	private String osrmBaseUrl;

	public OSRMRoute getRoute(double fromLat, double fromLon, double toLat, double toLon) {
		try {
			String url = String.format("%s/route/v1/driving/%f,%f;%f,%f?overview=false", 
				osrmBaseUrl, fromLon, fromLat, toLon, toLat);
			
			OSRMResponse response = restTemplate.getForObject(url, OSRMResponse.class);
			
			if (response != null && "Ok".equals(response.getCode()) && 
				response.getRoutes() != null && !response.getRoutes().isEmpty()) {
				return response.getRoutes().get(0);
			}
			
			log.warn("OSRM returned no route from=({},{}) to=({},{})", fromLat, fromLon, toLat, toLon);
			return null;
		} catch (Exception e) {
			log.error("OSRM request failed from=({},{}) to=({},{})", fromLat, fromLon, toLat, toLon, e);
			return null;
		}
	}
}
