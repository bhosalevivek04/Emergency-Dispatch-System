package com.vivek.ambulance.service;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.vivek.ambulance.dto.OSRMResponse;
import com.vivek.ambulance.dto.OSRMRoute;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OSRMService {

	@Value("${osrm.url:http://localhost:5000}")
	private String osrmUrl;
	
	@Value("${osrm.enabled:true}")
	private boolean osrmEnabled;

	private final RestTemplate restTemplate;

	public OSRMService(RestTemplateBuilder restTemplateBuilder) {
		this.restTemplate = restTemplateBuilder
				.connectTimeout(Duration.ofSeconds(3))
				.readTimeout(Duration.ofSeconds(5))
				.build();
	}

	/**
	 * Get route information from OSRM
	 * Returns RouteInfo with waypoints and duration, or null if unavailable
	 */
	@CircuitBreaker(name = "osrm", fallbackMethod = "fallbackRouteInfo")
	@Retry(name = "osrm", fallbackMethod = "fallbackRouteInfo")
	public RouteInfo getRouteInfo(double fromLat, double fromLon, double toLat, double toLon) {
		if (!osrmEnabled) {
			log.debug("OSRM is disabled, skipping route fetch");
			return null;
		}
		
		try {
			String url = String.format(
					"%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
					osrmUrl, fromLon, fromLat, toLon, toLat);

			log.debug("Fetching route from OSRM: {} -> {}", 
				String.format("%.6f,%.6f", fromLat, fromLon),
				String.format("%.6f,%.6f", toLat, toLon));

			OSRMResponse response = restTemplate.getForObject(url, OSRMResponse.class);

			if (response != null && "Ok".equals(response.getCode()) && 
				response.getRoutes() != null && !response.getRoutes().isEmpty()) {
				
				OSRMRoute route = response.getRoutes().get(0);
				
				if (route.getGeometry() != null && route.getGeometry().getCoordinates() != null) {
					List<List<Double>> coordinates = route.getGeometry().getCoordinates();
					
					if (!coordinates.isEmpty()) {
						List<double[]> waypoints = new ArrayList<>();
						for (List<Double> point : coordinates) {
							// OSRM returns [lon, lat], we need [lat, lon]
							waypoints.add(new double[]{point.get(1), point.get(0)});
						}
						
						double durationSeconds = route.getDuration();
						double distanceMeters = route.getDistance();
						
						// If OSRM doesn't return duration (wrong map data), calculate from distance
						// Assume average speed of 40 km/h in city traffic
						if (durationSeconds == 0 && distanceMeters > 0) {
							durationSeconds = (distanceMeters / 1000.0) / 40.0 * 3600.0; // distance(km) / speed(km/h) * 3600(s/h)
							log.info("OSRM returned duration=0, calculated from distance: {} waypoints, distance: {}km ({}m), calculated duration: {}min ({}s)",
								waypoints.size(), distanceMeters / 1000.0, distanceMeters, durationSeconds / 60.0, durationSeconds);
						} else {
							log.info("OSRM route fetched: {} waypoints, distance: {}km ({}m), duration: {}min ({}s)",
								waypoints.size(), distanceMeters / 1000.0, distanceMeters, durationSeconds / 60.0, durationSeconds);
						}
						
						return new RouteInfo(waypoints, durationSeconds, distanceMeters);
					}
				}
			}

			log.warn("OSRM returned no route, falling back to straight line");
			return null;

		} catch (Exception e) {
			log.warn("OSRM route fetch failed: {}, falling back to straight line", e.getMessage());
			return null;
		}
	}

	private RouteInfo fallbackRouteInfo(double fromLat, double fromLon, double toLat, double toLon, Throwable throwable) {
		log.warn("OSRM circuit fallback triggered from=({},{}) to=({},{}) reason={}",
				fromLat, fromLon, toLat, toLon, throwable == null ? "unknown" : throwable.getMessage());
		return null;
	}
	
	/**
	 * Container for route information
	 */
	public static class RouteInfo {
		public final List<double[]> waypoints;
		public final double durationSeconds;
		public final double distanceMeters;
		
		public RouteInfo(List<double[]> waypoints, double durationSeconds, double distanceMeters) {
			this.waypoints = waypoints;
			this.durationSeconds = durationSeconds;
			this.distanceMeters = distanceMeters;
		}
	}
}
