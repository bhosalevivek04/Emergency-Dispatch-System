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
  const [currentLocation, setCurrentLocation] = useState({ latitude: 18.5204, longitude: 73.8567 });
  const [ambulanceData, setAmbulanceData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [locationTracking, setLocationTracking] = useState(false);
  const [useSimulation, setUseSimulation] = useState(true);
  const [wsConnectionState, setWsConnectionState] = useState('disconnected');
  
  // Refs
  const locationIntervalRef = useRef(null);
  const wsUnsubscribeRef = useRef(null);

  // Extract ambulance ID from username (assuming format like "driver1", "driver2", etc.)
  useEffect(() => {
    if (user?.username) {
      // Try to extract ambulance ID from username
      const match = user.username.match(/driver(\d+)/i);
      if (match) {
        setAmbulanceId(`AMB-${match[1].padStart(3, '0')}`);
      } else {
        // Default to AMB-001 if pattern doesn't match
        setAmbulanceId('AMB-001');
      }
    }
  }, [user]);

  // Fetch ambulance data and current mission
  const fetchAmbulanceData = useCallback(async () => {
    if (!ambulanceId) return;
    
    try {
      const ambulance = await ambulanceApi.getById(ambulanceId);
      setAmbulanceData(ambulance);
      
      // Update current location from ambulance data
      setCurrentLocation({
        latitude: ambulance.latitude,
        longitude: ambulance.longitude,
      });
      
      // Fetch mission if ambulance is assigned
      if (ambulance.assignedEmergencyId) {
        const emergency = await emergencyApi.getById(ambulance.assignedEmergencyId);
        setCurrentMission(emergency);
      } else {
        setCurrentMission(null);
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
            setCurrentMission(data);
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
    if (!ambulanceId || !locationTracking) return;

    const sendLocationUpdate = async (lat, lng) => {
      try {
        await trackingApi.updateLocation({
          ambulanceId,
          latitude: lat,
          longitude: lng,
          timestamp: new Date().toISOString(),
        });
      } catch (error) {
        console.error('Error sending location update:', error);
      }
    };

    if (useSimulation) {
      // Simulated movement towards emergency
      locationIntervalRef.current = setInterval(() => {
        if (currentMission) {
          setCurrentLocation(prev => {
            // Move slightly towards emergency
            const dx = (currentMission.latitude - prev.latitude) * 0.1;
            const dy = (currentMission.longitude - prev.longitude) * 0.1;
            const newLat = prev.latitude + dx;
            const newLng = prev.longitude + dy;
            
            sendLocationUpdate(newLat, newLng);
            
            return {
              latitude: newLat,
              longitude: newLng,
            };
          });
        }
      }, 5000); // Update every 5 seconds
    } else {
      // Real GPS tracking
      if ('geolocation' in navigator) {
        const watchId = navigator.geolocation.watchPosition(
          (position) => {
            const { latitude, longitude } = position.coords;
            setCurrentLocation({ latitude, longitude });
            sendLocationUpdate(latitude, longitude);
          },
          (error) => {
            console.error('Geolocation error:', error);
            showToast('Failed to get location', 'error');
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
  }, [ambulanceId, locationTracking, currentMission, useSimulation, showToast]);

  // Auto-start location tracking when mission is active
  useEffect(() => {
    if (currentMission && ambulanceData?.status !== 'AVAILABLE') {
      setLocationTracking(true);
    } else {
      setLocationTracking(false);
    }
  }, [currentMission, ambulanceData]);

  // Handle status update
  const handleStatusUpdate = async (newStatus) => {
    if (!currentMission) return;
    
    setIsUpdatingStatus(true);
    try {
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
        currentMission.latitude,
        currentMission.longitude
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
                    latitude: currentMission.latitude,
                    longitude: currentMission.longitude,
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
              <div className="tracking-status">
                <span className={`tracking-indicator ${locationTracking ? 'active' : ''}`}>
                  {locationTracking ? '📡 Tracking Active' : '📡 Tracking Inactive'}
                </span>
              </div>
              <div className="simulation-toggle">
                <label>
                  <input
                    type="checkbox"
                    checked={useSimulation}
                    onChange={(e) => setUseSimulation(e.target.checked)}
                  />
                  Use Simulated Movement
                </label>
              </div>
            </section>
          </>
        ) : (
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
        )}
      </main>
    </div>
  );
};

export default DriverDashboard;
