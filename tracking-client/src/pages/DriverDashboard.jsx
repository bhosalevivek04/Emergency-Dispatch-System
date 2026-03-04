import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { emergencyApi, trackingApi, ambulanceApi } from '../services/api';
import { wsService } from '../services/websocketService';
import MapComponent from '../components/map/MapComponent';
import EmergencyMarker from '../components/map/EmergencyMarker';
import AmbulanceMarker from '../components/map/AmbulanceMarker';
import RoutePolyline from '../components/map/RoutePolyline';
import LoadingSpinner from '../components/LoadingSpinner';
import ConnectionStatus from '../components/ConnectionStatus';
import './DriverDashboard.css';

/**
 * Calculate distance between two coordinates using Haversine formula
 * @param {number} lat1 - Latitude of point 1
 * @param {number} lon1 - Longitude of point 1
 * @param {number} lat2 - Latitude of point 2
 * @param {number} lon2 - Longitude of point 2
 * @returns {number} Distance in kilometers
 */
const calculateDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371; // Earth's radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

/**
 * Calculate ETA based on distance and average speed
 * @param {number} distance - Distance in kilometers
 * @param {number} speed - Speed in km/h (defaults to 40 km/h if not provided)
 * @returns {number} ETA in minutes
 */
const calculateETA = (distance, speed = 40) => {
  const effectiveSpeed = speed > 0 ? speed : 40; // Use 40 km/h as default
  return Math.ceil((distance / effectiveSpeed) * 60);
};

const SIMULATION_TICK_SECONDS = 5;
const ARRIVAL_SNAP_DISTANCE_KM = 0.03; // 30 meters
const MANUAL_LOCATION_LOCK_MS = 15000;

const DEFAULT_LAT = 18.5204;
const DEFAULT_LON = 73.8567;

const toValidLatitude = (value, fallback = DEFAULT_LAT) => (
  Number.isFinite(Number(value)) ? Number(value) : fallback
);

const toValidLongitude = (value, fallback = DEFAULT_LON) => (
  Number.isFinite(Number(value)) ? Number(value) : fallback
);

const normalizeEmergency = (emergency) => {
  if (!emergency) return null;
  return {
    ...emergency,
    id: emergency.id || emergency.emergencyId,
    latitude: toValidLatitude(emergency.latitude ?? emergency.lat),
    longitude: toValidLongitude(emergency.longitude ?? emergency.lon),
    assignedAmbulanceId: emergency.assignedAmbulanceId || emergency.ambulanceId,
  };
};

/**
 * DriverDashboard component
 * Mobile-first dashboard for ambulance drivers showing active mission, map, and status controls
 */
const DriverDashboard = () => {
  const { user, logout, accessToken } = useAuth();
  const { showToast } = useToast();

  // State
  const [ambulanceId, setAmbulanceId] = useState(null);
  const [currentMission, setCurrentMission] = useState(null);
  const [currentLocation, setCurrentLocation] = useState({ latitude: DEFAULT_LAT, longitude: DEFAULT_LON });
  const [ambulanceData, setAmbulanceData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [locationTracking, setLocationTracking] = useState(false);
  const [useSimulation, setUseSimulation] = useState(false);
  const [gpsPermission, setGpsPermission] = useState('unknown');
  const [mapPickMode, setMapPickMode] = useState(false);
  const [isTrackingPaused, setIsTrackingPaused] = useState(false);
  const [manualLocationLockUntil, setManualLocationLockUntil] = useState(0);
  const [manualLocationMode, setManualLocationMode] = useState(false);
  const [wsConnectionState, setWsConnectionState] = useState('disconnected');

  // Refs
  const locationIntervalRef = useRef(null);
  const wsUnsubscribeRef = useRef(null);

  // Ambulance ID is supplied by the auth response.
  useEffect(() => {
    if (user?.ambulanceId) {
      setAmbulanceId(user.ambulanceId);
    } else {
      setAmbulanceId(null);
    }
  }, [user]);

  // Fetch ambulance data and current mission
  const fetchAmbulanceData = useCallback(async () => {
    if (!ambulanceId) return;

    try {
      const ambulance = await ambulanceApi.getById(ambulanceId);
      setAmbulanceData(ambulance);

      // /api/ambulances/{id} may not include coordinates; retain valid previous position.
      setCurrentLocation((prev) => ({
        latitude: toValidLatitude(ambulance.latitude, prev.latitude),
        longitude: toValidLongitude(ambulance.longitude, prev.longitude),
      }));

      // Resolve mission:
      // 1) direct linked emergency ID from ambulance response (if available)
      // 2) fallback scan of ASSIGNED emergencies by ambulance ID
      if (ambulance.assignedEmergencyId) {
        const emergency = await emergencyApi.getById(ambulance.assignedEmergencyId);
        setCurrentMission(normalizeEmergency(emergency));
      } else {
        const assigned = await emergencyApi.getByStatus('ASSIGNED');
        const matched = assigned.find((e) => {
          const emergency = normalizeEmergency(e);
          return emergency?.assignedAmbulanceId === ambulanceId;
        });
        setCurrentMission(matched ? normalizeEmergency(matched) : null);
      }
    } catch (error) {
      console.error('Error fetching ambulance data:', error);
      showToast('Failed to load ambulance data', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [ambulanceId, showToast]);

  // Initial data fetch
  useEffect(() => {
    if (ambulanceId) {
      fetchAmbulanceData();
    }
  }, [ambulanceId, fetchAmbulanceData]);

  useEffect(() => {
    if (!ambulanceId) return;

    if (!('geolocation' in navigator)) {
      setGpsPermission('unsupported');
      return;
    }

    if (!('permissions' in navigator) || !navigator.permissions?.query) {
      setGpsPermission('unknown');
      return;
    }

    let permissionStatusRef;

    navigator.permissions
      .query({ name: 'geolocation' })
      .then((permissionStatus) => {
        permissionStatusRef = permissionStatus;
        setGpsPermission(permissionStatus.state);
        permissionStatus.onchange = () => setGpsPermission(permissionStatus.state);
      })
      .catch(() => setGpsPermission('unknown'));

    return () => {
      if (permissionStatusRef) {
        permissionStatusRef.onchange = null;
      }
    };
  }, [ambulanceId]);

  // WebSocket connection for real-time updates
  useEffect(() => {
    if (!ambulanceId || !accessToken) return;

    const connectWebSocket = async () => {
      try {
        setWsConnectionState('connecting');
        await wsService.connect(accessToken);
        setWsConnectionState('connected');

        // Subscribe to driver mission updates
        const unsubscribe = wsService.subscribe(
          `/topic/driver/${ambulanceId}/mission`,
          (data) => {
            console.log('Received mission update:', data);
            setCurrentMission(normalizeEmergency(data));
          }
        );

        wsUnsubscribeRef.current = unsubscribe;
      } catch (error) {
        console.error('WebSocket connection failed:', error);
        setWsConnectionState('disconnected');
      }
    };

    connectWebSocket();

    return () => {
      if (wsUnsubscribeRef.current) {
        wsUnsubscribeRef.current();
      }
      setWsConnectionState('disconnected');
    };
  }, [ambulanceId, accessToken]);

  // Location tracking
  useEffect(() => {
    const isManualLockActive = Date.now() < manualLocationLockUntil;
    if (!ambulanceId || !locationTracking || mapPickMode || isTrackingPaused || isManualLockActive) return;

    const sendLocationUpdate = async (lat, lng, speedKmh = 0) => {
      try {
        await trackingApi.updateLocation({
          ambulanceId,
          latitude: lat,
          longitude: lng,
          speed: speedKmh,
          heading: ambulanceData?.heading ?? 0,
          timestamp: Date.now(),
        });
      } catch (error) {
        console.error('Error sending location update:', error);
      }
    };

    if (manualLocationMode) {
      locationIntervalRef.current = setInterval(() => {
        sendLocationUpdate(currentLocation.latitude, currentLocation.longitude, 0);
      }, 5000);
    } else if (useSimulation) {
      // Simulated movement towards emergency
      locationIntervalRef.current = setInterval(() => {
        setCurrentLocation(prev => {
          if (!currentMission) {
            sendLocationUpdate(prev.latitude, prev.longitude, 0);
            return prev;
          }

          const targetLat = toValidLatitude(currentMission.latitude ?? currentMission.lat, prev.latitude);
          const targetLng = toValidLongitude(currentMission.longitude ?? currentMission.lon, prev.longitude);
          const distanceToTargetKm = calculateDistance(
            prev.latitude,
            prev.longitude,
            targetLat,
            targetLng
          );

          // Snap to exact emergency point when close enough.
          if (distanceToTargetKm <= ARRIVAL_SNAP_DISTANCE_KM) {
            sendLocationUpdate(targetLat, targetLng, 0);
            return {
              latitude: targetLat,
              longitude: targetLng,
            };
          }

          // Move by speed-based step to avoid asymptotic behavior.
          const speedKmh = 40;
          const stepKm = speedKmh * (SIMULATION_TICK_SECONDS / 3600);
          const fraction = Math.min(1, stepKm / distanceToTargetKm);
          const newLat = prev.latitude + (targetLat - prev.latitude) * fraction;
          const newLng = prev.longitude + (targetLng - prev.longitude) * fraction;

          sendLocationUpdate(newLat, newLng, speedKmh);

          return {
            latitude: newLat,
            longitude: newLng,
          };
        });
      }, 5000); // Update every 5 seconds
    } else {
      // Real GPS tracking
      if ('geolocation' in navigator) {
        const watchId = navigator.geolocation.watchPosition(
          (position) => {
            const { latitude, longitude } = position.coords;
            setGpsPermission('granted');
            setCurrentLocation({ latitude, longitude });
            const gpsSpeed = position.coords.speed != null && Number.isFinite(position.coords.speed)
              ? Math.max(0, position.coords.speed * 3.6)
              : 0;
            sendLocationUpdate(latitude, longitude, gpsSpeed);
          },
          (error) => {
            console.error('Geolocation error:', error);
            if (error.code === error.PERMISSION_DENIED) {
              setGpsPermission('denied');
              showToast('Location permission denied. Enable GPS permission in browser settings.', 'error');
            } else {
              showToast('Failed to get location', 'error');
            }
          },
          {
            enableHighAccuracy: true,
            timeout: 5000,
            maximumAge: 0,
          }
        );

        locationIntervalRef.current = watchId;
      }
    }

    return () => {
      if (locationIntervalRef.current) {
        if (useSimulation) {
          clearInterval(locationIntervalRef.current);
        } else {
          navigator.geolocation.clearWatch(locationIntervalRef.current);
        }
      }
    };
  }, [ambulanceId, locationTracking, currentMission, useSimulation, showToast, ambulanceData, mapPickMode, isTrackingPaused, manualLocationLockUntil, manualLocationMode, currentLocation.latitude, currentLocation.longitude]);

  // Auto-start location tracking when mission is active
  useEffect(() => {
    if (ambulanceId) {
      setLocationTracking(true);
    }
  }, [ambulanceId]);

  // Manual fixed-position mode is only for pre-assignment staging.
  // Once a mission is assigned, force live/sim movement mode.
  useEffect(() => {
    if (!currentMission) {
      return;
    }
    if (manualLocationMode || mapPickMode || isTrackingPaused) {
      setManualLocationMode(false);
      setMapPickMode(false);
      setIsTrackingPaused(false);
      showToast('Mission assigned: switched from manual fixed location to live tracking', 'warning');
    }
  }, [currentMission, manualLocationMode, mapPickMode, isTrackingPaused, showToast]);

  const requestGpsPermission = () => {
    if (!('geolocation' in navigator)) {
      setGpsPermission('unsupported');
      showToast('Geolocation is not supported by this browser', 'error');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setManualLocationMode(false);
        setUseSimulation(false);
        setGpsPermission('granted');
        setCurrentLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        showToast('GPS permission granted', 'success');
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          setGpsPermission('denied');
          showToast('GPS permission denied. Please allow location access.', 'error');
        } else {
          showToast('Unable to get current location', 'error');
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  };

  const handleManualMapLocation = useCallback(async (lat, lon) => {
    if (!ambulanceId) return;

    const latitude = toValidLatitude(lat, currentLocation.latitude);
    const longitude = toValidLongitude(lon, currentLocation.longitude);

    setCurrentLocation({ latitude, longitude });

    try {
      await trackingApi.updateLocation({
        ambulanceId,
        latitude,
        longitude,
        speed: 0,
        heading: ambulanceData?.heading ?? 0,
        timestamp: Date.now(),
      });
      setMapPickMode(false);
      setIsTrackingPaused(false);
      setManualLocationLockUntil(Date.now() + MANUAL_LOCATION_LOCK_MS);
      setManualLocationMode(true);
      showToast('Start location updated from map (locked for 15s)', 'success');
    } catch (error) {
      console.error('Failed to update manual map location:', error);
      showToast('Failed to update location from map', 'error');
    }
  }, [ambulanceId, ambulanceData?.heading, currentLocation.latitude, currentLocation.longitude, showToast]);

  const toggleMapPickMode = () => {
    setMapPickMode((prev) => {
      const next = !prev;
      setIsTrackingPaused(next);
      return next;
    });
  };

  const resumeLiveTracking = () => {
    setManualLocationMode(false);
    showToast('Live location tracking resumed', 'success');
  };

  useEffect(() => {
    if (!manualLocationLockUntil) return;
    const remaining = manualLocationLockUntil - Date.now();
    if (remaining <= 0) {
      setManualLocationLockUntil(0);
      return;
    }

    const timer = setTimeout(() => setManualLocationLockUntil(0), remaining);
    return () => clearTimeout(timer);
  }, [manualLocationLockUntil]);

  // Handle status update
  const handleStatusUpdate = async (newStatus) => {
    if (!currentMission) return;

    setIsUpdatingStatus(true);
    try {
      if (newStatus === 'ARRIVED') {
        // Ensure backend and all dashboards see exact arrival coordinates.
        await trackingApi.updateLocation({
          ambulanceId,
          latitude: toValidLatitude(currentMission.latitude ?? currentMission.lat, currentLocation.latitude),
          longitude: toValidLongitude(currentMission.longitude ?? currentMission.lon, currentLocation.longitude),
          speed: 0,
          heading: ambulanceData?.heading ?? 0,
          timestamp: Date.now(),
        });
        setCurrentLocation({
          latitude: toValidLatitude(currentMission.latitude ?? currentMission.lat, currentLocation.latitude),
          longitude: toValidLongitude(currentMission.longitude ?? currentMission.lon, currentLocation.longitude),
        });
      }

      await emergencyApi.updateStatus(currentMission.id, newStatus);
      showToast(`Status updated to ${newStatus}`, 'success');

      // Refresh ambulance data
      await fetchAmbulanceData();
    } catch (error) {
      console.error('Error updating status:', error);
      showToast('Failed to update status', 'error');
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // Calculate distance and ETA
  const distance = currentMission
    ? calculateDistance(
      currentLocation.latitude,
      currentLocation.longitude,
      toValidLatitude(currentMission.latitude ?? currentMission.lat),
      toValidLongitude(currentMission.longitude ?? currentMission.lon)
    )
    : 0;

  const eta = currentMission ? calculateETA(distance, ambulanceData?.speed) : 0;

  if (isLoading) {
    return (
      <div className="driver-dashboard">
        <LoadingSpinner size="large" overlay />
      </div>
    );
  }

  if (!ambulanceId) {
    return (
      <div className="driver-dashboard">
        <header className="driver-header">
          <div className="driver-header-content">
            <h1>🚑 Driver Dashboard</h1>
            <div className="driver-header-actions">
              <span className="driver-username">{user?.username}</span>
              <button onClick={logout} className="logout-btn">
                Logout
              </button>
            </div>
          </div>
        </header>
        <main className="driver-main">
          <div className="no-mission">
            <div className="no-mission-icon">⚠️</div>
            <h2>No Ambulance Linked</h2>
            <p>Your driver account is not linked to an ambulance yet.</p>
            <p>Please contact admin to assign an ambulance ID.</p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="driver-dashboard">
      {/* Header */}
      <header className="driver-header">
        <div className="driver-header-content">
          <h1>🚑 Driver Dashboard</h1>
          <div className="driver-header-actions">
            <ConnectionStatus status={wsConnectionState} />
            <span className="driver-username">{user?.username}</span>
            <button onClick={logout} className="logout-btn">
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="driver-main">
        {currentMission ? (
          <>
            {/* Mission Info Card */}
            <section className="mission-info-card">
              <div className="mission-header">
                <h2>🚨 Active Mission</h2>
                <span className={`priority-badge priority-${currentMission.priority.toLowerCase()}`}>
                  {currentMission.priority}
                </span>
              </div>

              <div className="mission-details">
                <div className="mission-detail-item">
                  <span className="detail-label">Emergency ID:</span>
                  <span className="detail-value">{currentMission.id}</span>
                </div>
                <div className="mission-detail-item">
                  <span className="detail-label">Status:</span>
                  <span className="detail-value">{currentMission.status}</span>
                </div>
                <div className="mission-detail-item">
                  <span className="detail-label">Distance:</span>
                  <span className="detail-value">{distance.toFixed(2)} km</span>
                </div>
                <div className="mission-detail-item">
                  <span className="detail-label">ETA:</span>
                  <span className="detail-value eta-countdown">{eta} min</span>
                </div>
              </div>
            </section>

            {/* Mission Map */}
            <section className="mission-map">
              <MapComponent
                center={[currentLocation.latitude, currentLocation.longitude]}
                zoom={14}
                onMapClick={mapPickMode ? handleManualMapLocation : undefined}
                style={{ height: '100%', width: '100%' }}
              >
                <EmergencyMarker emergency={currentMission} />
                {ambulanceData && (
                  <AmbulanceMarker
                    ambulance={{
                      ...ambulanceData,
                      latitude: currentLocation.latitude,
                      longitude: currentLocation.longitude,
                    }}
                  />
                )}
                <RoutePolyline
                  ambulancePosition={currentLocation}
                  emergencyPosition={{
                    latitude: toValidLatitude(currentMission.latitude ?? currentMission.lat),
                    longitude: toValidLongitude(currentMission.longitude ?? currentMission.lon),
                  }}
                />
              </MapComponent>
            </section>

            {/* Status Controls */}
            <section className="status-controls">
              <h3>Update Mission Status</h3>
              <div className="status-buttons">
                {currentMission.status === 'ASSIGNED' && (
                  <button
                    onClick={() => handleStatusUpdate('ON_ROUTE')}
                    disabled={isUpdatingStatus}
                    className="status-btn status-btn-on-route"
                  >
                    {isUpdatingStatus ? 'Updating...' : '🚗 En Route'}
                  </button>
                )}
                {currentMission.status === 'ON_ROUTE' && (
                  <button
                    onClick={() => handleStatusUpdate('ARRIVED')}
                    disabled={isUpdatingStatus}
                    className="status-btn status-btn-arrived"
                  >
                    {isUpdatingStatus ? 'Updating...' : '📍 Arrived'}
                  </button>
                )}
                {currentMission.status === 'ARRIVED' && (
                  <button
                    onClick={() => {
                      if (window.confirm('Mark mission as completed?')) {
                        handleStatusUpdate('COMPLETED');
                      }
                    }}
                    disabled={isUpdatingStatus}
                    className="status-btn status-btn-completed"
                  >
                    {isUpdatingStatus ? 'Updating...' : '✅ Complete'}
                  </button>
                )}
              </div>
            </section>

            {/* Location Tracking Controls */}
            <section className="tracking-controls">
              <div className="simulation-toggle">
                <button
                  type="button"
                  onClick={toggleMapPickMode}
                  className="status-btn status-btn-arrived"
                >
                  {mapPickMode ? 'Cancel Location Pick' : 'Pick Location On Map'}
                </button>
              </div>
              {mapPickMode && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Tap on map to set start location</span>
                </div>
              )}
              {isTrackingPaused && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Live tracking paused while selecting location</span>
                </div>
              )}
              {Date.now() < manualLocationLockUntil && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Manual location lock active (15s)</span>
                </div>
              )}
              <div className="tracking-status">
                <span className={`tracking-indicator ${locationTracking ? 'active' : ''}`}>
                  {locationTracking ? '📡 Tracking Active' : '📡 Tracking Inactive'}
                </span>
              </div>
              <div className="tracking-status">
                <span className={`tracking-indicator ${gpsPermission === 'granted' ? 'active' : ''}`}>
                  GPS Permission: {gpsPermission}
                </span>
              </div>
              {manualLocationMode && (
                <div className="tracking-status">
                  <span className="tracking-indicator active">Manual location mode active (fixed position)</span>
                </div>
              )}
              {manualLocationMode && (
                <div className="simulation-toggle">
                  <button type="button" onClick={resumeLiveTracking} className="status-btn status-btn-on-route">
                    Resume Live Tracking
                  </button>
                </div>
              )}
              <div className="simulation-toggle">
                <button type="button" onClick={requestGpsPermission} className="status-btn status-btn-on-route">
                  Enable GPS Location
                </button>
              </div>
              <div className="simulation-toggle">
                <label>
                  <input
                    type="checkbox"
                    checked={useSimulation}
                    onChange={(e) => {
                      setManualLocationMode(false);
                      setUseSimulation(e.target.checked);
                    }}
                  />
                  Use Simulated Movement
                </label>
              </div>
            </section>
          </>
        ) : (
          <>
            <div className="no-mission">
              <div className="no-mission-icon">🚑</div>
              <h2>No Active Mission</h2>
              <p>Waiting for emergency assignment...</p>
              <div className="ambulance-status">
                <span>Status: </span>
                <span className="status-badge status-available">
                  {ambulanceData?.status || 'AVAILABLE'}
                </span>
              </div>
            </div>

            <section className="mission-map">
              <MapComponent
                center={[currentLocation.latitude, currentLocation.longitude]}
                zoom={14}
                onMapClick={mapPickMode ? handleManualMapLocation : undefined}
                style={{ height: '100%', width: '100%' }}
              >
                {ambulanceData && (
                  <AmbulanceMarker
                    ambulance={{
                      ...ambulanceData,
                      latitude: currentLocation.latitude,
                      longitude: currentLocation.longitude,
                    }}
                  />
                )}
              </MapComponent>
            </section>

            <section className="tracking-controls">
              <div className="simulation-toggle">
                <button
                  type="button"
                  onClick={toggleMapPickMode}
                  className="status-btn status-btn-arrived"
                >
                  {mapPickMode ? 'Cancel Location Pick' : 'Pick Location On Map'}
                </button>
              </div>
              {mapPickMode && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Tap on map to set start location</span>
                </div>
              )}
              {isTrackingPaused && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Live tracking paused while selecting location</span>
                </div>
              )}
              {Date.now() < manualLocationLockUntil && (
                <div className="tracking-status">
                  <span className="tracking-indicator">Manual location lock active (15s)</span>
                </div>
              )}
              <div className="tracking-status">
                <span className={`tracking-indicator ${locationTracking ? 'active' : ''}`}>
                  {locationTracking ? '📡 Tracking Active' : '📡 Tracking Inactive'}
                </span>
              </div>
              <div className="tracking-status">
                <span className={`tracking-indicator ${gpsPermission === 'granted' ? 'active' : ''}`}>
                  GPS Permission: {gpsPermission}
                </span>
              </div>
              {manualLocationMode && (
                <div className="tracking-status">
                  <span className="tracking-indicator active">Manual location mode active (fixed position)</span>
                </div>
              )}
              {manualLocationMode && (
                <div className="simulation-toggle">
                  <button type="button" onClick={resumeLiveTracking} className="status-btn status-btn-on-route">
                    Resume Live Tracking
                  </button>
                </div>
              )}
              <div className="simulation-toggle">
                <button type="button" onClick={requestGpsPermission} className="status-btn status-btn-on-route">
                  Enable GPS Location
                </button>
              </div>
              <div className="simulation-toggle">
                <label>
                  <input
                    type="checkbox"
                    checked={useSimulation}
                    onChange={(e) => {
                      setManualLocationMode(false);
                      setUseSimulation(e.target.checked);
                    }}
                  />
                  Use Simulated Movement
                </label>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
};

export default DriverDashboard;

