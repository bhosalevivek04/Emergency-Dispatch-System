package com.vivek.ambulance.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.model.AmbulanceStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AmbulanceMovementSimulator {

	@Value("${ambulance.fleet.ids:AMB-101,AMB-102,AMB-103}")
	private String fleetIdsConfig;
	
	private String[] ambulanceIds;

	private final AmbulanceProducer producer;
	private final AmbulanceStateTracker stateTracker;
	private final OSRMService osrmService;

	// Store current location and destination for each ambulance
	private final Map<String, Location> currentLocations = new ConcurrentHashMap<>();
	private final Map<String, Location> destinations = new ConcurrentHashMap<>();
	
	// Store route waypoints for each ambulance
	private final Map<String, List<double[]>> routeWaypoints = new ConcurrentHashMap<>();
	private final Map<String, Integer> currentWaypointIndex = new ConcurrentHashMap<>();
	
	// Store route duration (in seconds) for each ambulance
	private final Map<String, Double> routeDurations = new ConcurrentHashMap<>();
	
	// Track last broadcast time for each ambulance
	private final Map<String, Long> lastBroadcastTime = new ConcurrentHashMap<>();

	// Movement speed: ~0.00002 degrees per update (~2 meters per second for realistic visualization)
	// At 1 second update rate, this gives ~7.2 km/h walking speed for demo purposes
	private static final double MOVEMENT_SPEED = 0.00002;
	
	// Broadcast interval for moving ambulances (3 seconds)
	private static final long MOVING_BROADCAST_INTERVAL = 3000;
	
	// Broadcast interval for stationary ambulances (10 seconds)
	private static final long STATIONARY_BROADCAST_INTERVAL = 10000;

	// Initial ambulance positions (Pune area) - will be initialized from config
	private static final double[][] INITIAL_POSITIONS = {
		{18.5204, 73.8567},  // Position for first ambulance
		{18.5300, 73.8600},  // Position for second ambulance
		{18.5100, 73.8500}   // Position for third ambulance
	};
	
	@PostConstruct
	public void initializeFleet() {
		try {
			// Parse fleet IDs from configuration
			if (fleetIdsConfig == null || fleetIdsConfig.trim().isEmpty()) {
				log.error("Fleet IDs configuration is null or empty! Using default.");
				fleetIdsConfig = "AMB-101,AMB-102,AMB-103";
			}
			
			ambulanceIds = fleetIdsConfig.split(",");
			for (int i = 0; i < ambulanceIds.length; i++) {
				ambulanceIds[i] = ambulanceIds[i].trim();
			}
			
			log.info("Initializing movement simulator for fleet: {}", String.join(", ", ambulanceIds));
		} catch (Exception e) {
			log.error("Failed to initialize movement simulator fleet", e);
			throw new RuntimeException("Movement simulator fleet initialization failed", e);
		}
	}

	@Scheduled(fixedRate = 1000) // Update every 1 second for smooth animation
	public void updateAmbulanceLocations() {
		// Initialize ambulances if not present (using configured fleet)
		for (int i = 0; i < ambulanceIds.length; i++) {
			String ambulanceId = ambulanceIds[i];
			// Use initial position if available, otherwise use default
			double[] position = i < INITIAL_POSITIONS.length ? INITIAL_POSITIONS[i] : INITIAL_POSITIONS[0];
			initializeAmbulance(ambulanceId, position[0], position[1]);
		}

		// Update each ambulance
		for (String ambulanceId : currentLocations.keySet()) {
			updateAmbulance(ambulanceId);
		}
	}

	private void initializeAmbulance(String ambulanceId, double lat, double lon) {
		if (!currentLocations.containsKey(ambulanceId)) {
			currentLocations.put(ambulanceId, new Location(lat, lon));
			log.info("Initialized ambulance {} at ({}, {})", ambulanceId, lat, lon);
		}
	}

	private void updateAmbulance(String ambulanceId) {
		Location current = currentLocations.get(ambulanceId);
		Location destination = destinations.get(ambulanceId);

		AmbulanceStatus status = stateTracker.getStatus(ambulanceId);

		log.debug("Updating {} - status: {}, hasDestination: {}, currentPos: ({}, {})", 
			ambulanceId, status, (destination != null), current.lat, current.lon);

		// If ambulance is assigned/on_route, move towards destination
		if ((status == AmbulanceStatus.ASSIGNED || status == AmbulanceStatus.ON_ROUTE) && destination != null) {
			// Check if we have route waypoints
			List<double[]> waypoints = routeWaypoints.get(ambulanceId);
			
			if (waypoints != null && !waypoints.isEmpty()) {
				// Follow route waypoints
				moveAlongRoute(ambulanceId, current, waypoints);
			} else {
				// Fallback to straight line movement
				moveStraightLine(ambulanceId, current, destination);
			}
		} else if (status == AmbulanceStatus.AVAILABLE) {
			// Available ambulances stay at current location (no movement)
			// Broadcast less frequently (every 10 seconds) to save resources
			long currentTime = System.currentTimeMillis();
			Long lastBroadcast = lastBroadcastTime.get(ambulanceId);
			
			if (lastBroadcast == null || (currentTime - lastBroadcast) >= STATIONARY_BROADCAST_INTERVAL) {
				publishLocation(ambulanceId, current.lat, current.lon, 0.0, 0.0);
				lastBroadcastTime.put(ambulanceId, currentTime);
				
				log.debug("Ambulance {} is AVAILABLE and stationary at ({}, {})", 
					ambulanceId, 
					String.format("%.6f", current.lat), 
					String.format("%.6f", current.lon));
			}
		} else {
			// Other statuses (ARRIVED, COMPLETED) - stay at current position
			publishLocation(ambulanceId, current.lat, current.lon, 0.0, 0.0);
			log.debug("Ambulance {} at status {} - staying at ({}, {})", 
				ambulanceId, status, current.lat, current.lon);
		}
	}

	/**
	 * Move ambulance along OSRM route waypoints
	 * Uses iteration instead of recursion to avoid stack overflow on dense routes
	 */
	private void moveAlongRoute(String ambulanceId, Location current, List<double[]> waypoints) {
		Integer waypointIdx = currentWaypointIndex.getOrDefault(ambulanceId, 0);
		
		if (waypointIdx >= waypoints.size()) {
			// Reached end of route
			double[] lastPoint = waypoints.get(waypoints.size() - 1);
			current.lat = lastPoint[0];
			current.lon = lastPoint[1];
			publishLocation(ambulanceId, current.lat, current.lon, 0.0, 0.0);
			lastBroadcastTime.put(ambulanceId, System.currentTimeMillis());
			log.info("Ambulance {} reached destination (following route)", ambulanceId);
			return;
		}
		
		// Get target waypoint
		double[] targetPoint = waypoints.get(waypointIdx);
		double targetLat = targetPoint[0];
		double targetLon = targetPoint[1];
		
		// Calculate distance to target waypoint
		double latDiff = targetLat - current.lat;
		double lonDiff = targetLon - current.lon;
		double distance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
		
		if (distance > MOVEMENT_SPEED) {
			// Move towards current waypoint
			double ratio = MOVEMENT_SPEED / distance;
			current.lat += latDiff * ratio;
			current.lon += lonDiff * ratio;
			
			// Calculate heading
			double heading = Math.toDegrees(Math.atan2(lonDiff, latDiff));
			if (heading < 0) heading += 360;
			
			// Calculate speed
			double speed = 40.0 + (Math.random() * 20); // 40-60 km/h
			
			// Only broadcast if enough time has passed (throttle to every 3 seconds)
			long currentTime = System.currentTimeMillis();
			Long lastBroadcast = lastBroadcastTime.get(ambulanceId);
			if (lastBroadcast == null || (currentTime - lastBroadcast) >= MOVING_BROADCAST_INTERVAL) {
				publishLocation(ambulanceId, current.lat, current.lon, speed, heading);
				lastBroadcastTime.put(ambulanceId, currentTime);
				
				log.info("Moving {} along route: waypoint {}/{}, distance to waypoint: {}",
						ambulanceId, waypointIdx + 1, waypoints.size(), String.format("%.4f", distance));
			}
		} else {
			// Reached current waypoint, move to next
			current.lat = targetLat;
			current.lon = targetLon;
			currentWaypointIndex.put(ambulanceId, waypointIdx + 1);
			
			log.info("Ambulance {} reached waypoint {}/{}", ambulanceId, waypointIdx + 1, waypoints.size());
			
			// Process at most one waypoint per tick to avoid recursion
			// The next tick will continue to the next waypoint
		}
	}

	/**
	 * Fallback: Move in straight line (when OSRM unavailable)
	 */
	private void moveStraightLine(String ambulanceId, Location current, Location destination) {
		double latDiff = destination.lat - current.lat;
		double lonDiff = destination.lon - current.lon;
		double distance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);

		if (distance > MOVEMENT_SPEED) {
			// Move towards destination
			double ratio = MOVEMENT_SPEED / distance;
			current.lat += latDiff * ratio;
			current.lon += lonDiff * ratio;

			// Calculate heading (direction in degrees)
			double heading = Math.toDegrees(Math.atan2(lonDiff, latDiff));
			if (heading < 0) heading += 360;

			// Calculate speed (km/h) - simulated
			double speed = 40.0 + (Math.random() * 20); // 40-60 km/h

			// Only broadcast if enough time has passed (throttle to every 3 seconds)
			long currentTime = System.currentTimeMillis();
			Long lastBroadcast = lastBroadcastTime.get(ambulanceId);
			if (lastBroadcast == null || (currentTime - lastBroadcast) >= MOVING_BROADCAST_INTERVAL) {
				publishLocation(ambulanceId, current.lat, current.lon, speed, heading);
				lastBroadcastTime.put(ambulanceId, currentTime);
				
				log.info("Moving {} towards emergency (straight line): ({}, {}) -> ({}, {}) distance: {}",
						ambulanceId, 
						String.format("%.6f", current.lat), 
						String.format("%.6f", current.lon), 
						String.format("%.6f", destination.lat), 
						String.format("%.6f", destination.lon), 
						String.format("%.4f", distance));
			}
		} else {
			// Reached destination
			current.lat = destination.lat;
			current.lon = destination.lon;
			
			// Broadcast if enough time has passed
			long currentTime = System.currentTimeMillis();
			Long lastBroadcast = lastBroadcastTime.get(ambulanceId);
			if (lastBroadcast == null || (currentTime - lastBroadcast) >= MOVING_BROADCAST_INTERVAL) {
				publishLocation(ambulanceId, current.lat, current.lon, 0.0, 0.0);
				lastBroadcastTime.put(ambulanceId, currentTime);
			}
			
			log.info("Ambulance {} reached destination ({}, {})", ambulanceId, current.lat, current.lon);
		}
	}

	private void publishLocation(String ambulanceId, double lat, double lon, double speed, double heading) {
		// Get route info for visualization
		List<double[]> waypoints = routeWaypoints.get(ambulanceId);
		Integer currentIdx = currentWaypointIndex.get(ambulanceId);
		
		double[][] routeCoords = null;
		Double progress = null;
		
		if (waypoints != null && !waypoints.isEmpty()) {
			// Convert waypoints to double[][] for JSON serialization
			routeCoords = waypoints.toArray(new double[0][]);
			
			// Calculate progress (0.0 to 1.0)
			if (currentIdx != null && waypoints.size() > 0) {
				progress = (double) currentIdx / waypoints.size();
			}
		}
		
		AmbulanceLocationEvent event = AmbulanceLocationEvent.builder()
				.ambulanceId(ambulanceId)
				.latitude(lat)
				.longitude(lon)
				.speed(speed)
				.heading(heading)
				.timestamp(System.currentTimeMillis())
				.routeCoordinates(routeCoords)
				.progress(progress)
				.build();
		producer.sendLocation(event);
	}

	/**
	 * Set destination for ambulance when assigned to emergency
	 * Fetches route from OSRM if available
	 * Returns estimated duration in seconds (or 0 if using straight line)
	 */
	public double setDestination(String ambulanceId, double lat, double lon) {
		destinations.put(ambulanceId, new Location(lat, lon));
		
		// Get current location
		Location current = currentLocations.get(ambulanceId);
		if (current != null) {
			// Fetch route from OSRM
			OSRMService.RouteInfo routeInfo = osrmService.getRouteInfo(current.lat, current.lon, lat, lon);
			
			if (routeInfo != null && routeInfo.waypoints != null && !routeInfo.waypoints.isEmpty() 
				&& routeInfo.durationSeconds > 0) {
				// OSRM returned valid route with duration
				routeWaypoints.put(ambulanceId, routeInfo.waypoints);
				currentWaypointIndex.put(ambulanceId, 0);
				routeDurations.put(ambulanceId, routeInfo.durationSeconds);
				
				log.info("Set destination for {} to ({}, {}) with {} waypoints from OSRM, ETA: {} minutes", 
					ambulanceId, lat, lon, routeInfo.waypoints.size(), routeInfo.durationSeconds / 60.0);
				
				return routeInfo.durationSeconds;
			} else {
				// OSRM failed or returned invalid data - create straight line route
				// Create a simple 2-point route: start -> end
				List<double[]> straightLineRoute = new ArrayList<>();
				straightLineRoute.add(new double[]{current.lat, current.lon}); // Start point
				straightLineRoute.add(new double[]{lat, lon}); // End point
				
				routeWaypoints.put(ambulanceId, straightLineRoute);
				currentWaypointIndex.put(ambulanceId, 0);
				
				// Calculate straight-line distance using Haversine formula
				double latDiff = lat - current.lat;
				double lonDiff = lon - current.lon;
				double straightLineDistance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
				
				// Convert to approximate meters (rough approximation: 1 degree ≈ 111km)
				double distanceMeters = straightLineDistance * 111000;
				
				// Calculate duration assuming 40 km/h average speed in city
				// Add 30% for road curves and traffic
				double estimatedSeconds = (distanceMeters / 1000.0) / 40.0 * 3600.0 * 1.3;
				
				routeDurations.put(ambulanceId, estimatedSeconds);
				
				log.info("Set destination for {} to ({}, {}) - using straight line (OSRM unavailable), distance: {}m, estimated: {} seconds ({} minutes)", 
					ambulanceId, lat, lon, distanceMeters, estimatedSeconds, estimatedSeconds / 60.0);
				
				return estimatedSeconds;
			}
		} else {
			log.warn("Cannot set destination for {} - current location unknown", ambulanceId);
			return 0;
		}
	}

	/**
	 * Clear destination when trip is completed
	 */
	public void clearDestination(String ambulanceId) {
		destinations.remove(ambulanceId);
		routeWaypoints.remove(ambulanceId);
		currentWaypointIndex.remove(ambulanceId);
		routeDurations.remove(ambulanceId);
		log.info("Cleared destination and route for {}", ambulanceId);
	}

	/**
	 * Get current location of ambulance
	 */
	public Location getCurrentLocation(String ambulanceId) {
		return currentLocations.get(ambulanceId);
	}

	// Inner class to store location
	public static class Location {
		public double lat;
		public double lon;

		public Location(double lat, double lon) {
			this.lat = lat;
			this.lon = lon;
		}
	}
}
