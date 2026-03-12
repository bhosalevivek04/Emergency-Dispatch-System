import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { emergencyApi, ambulanceApi } from '../services/api';
import { wsService } from '../services/websocketService';
import { fetchOSRMRoute } from '../services/osrm';
import MapComponent from '../components/map/MapComponent';
import EmergencyMarker from '../components/map/EmergencyMarker';
import AmbulanceMarker from '../components/map/AmbulanceMarker';
import { Polyline } from 'react-leaflet';
import EmergencyQueuePanel from '../components/EmergencyQueuePanel';
import FleetStatusPanel from '../components/FleetStatusPanel';
import EmergencyCreationControl from '../components/EmergencyCreationControl';
import FleetManagementPanel from '../components/FleetManagementPanel';
import SystemHealthPanel from '../components/SystemHealthPanel';
import SkeletonLoader from '../components/SkeletonLoader';
import ConnectionStatus from '../components/ConnectionStatus';
import TrackingPanel from '../components/TrackingPanel';
import { useToast } from '../contexts/ToastContext';
import '../pages/DispatcherDashboard.css';

// Helper functions for route trimming (same as CitizenRequestPage)
const toRad = (deg) => (deg * Math.PI) / 180;

const distanceMeters = (a, b) => {
  const R = 6371000;
  const dLat = toRad(b[0] - a[0]);
  const dLng = toRad(b[1] - a[1]);
  const lat1 = toRad(a[0]);
  const lat2 = toRad(b[0]);
  const h =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.sin(dLng / 2) * Math.sin(dLng / 2) * Math.cos(lat1) * Math.cos(lat2);
  return 2 * R * Math.asin(Math.sqrt(h));
};

const nearestIndex = (points, target) => {
  if (!points?.length) return -1;
  let bestIdx = 0;
  let bestDist = Number.POSITIVE_INFINITY;
  points.forEach((pt, idx) => {
    const d = distanceMeters(pt, target);
    if (d < bestDist) {
      bestDist = d;
      bestIdx = idx;
    }
  });
  return bestIdx;
};

/**
 * AdminDashboard component
 * Inherits all DispatcherDashboard features and adds admin-specific panels
 * Includes: map, emergency queue, fleet status, emergency creation (from dispatcher)
 * Plus: fleet management panel, system health panel, and Grafana metrics link
 * 
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 13.1, 13.2, 13.3
 */
const AdminDashboard = () => {
  const { user, logout, accessToken } = useAuth();
  const { showToast } = useToast();

  // State for emergencies and ambulances (inherited from dispatcher)
  const [emergencies, setEmergencies] = useState([]);
  const [ambulances, setAmbulances] = useState([]);

  // Loading states
  const [isLoadingEmergencies, setIsLoadingEmergencies] = useState(true);
  const [isLoadingAmbulances, setIsLoadingAmbulances] = useState(true);
  const [isLoadingMap, setIsLoadingMap] = useState(true);

  // WebSocket connection state
  const [wsConnected, setWsConnected] = useState(false);
  const [wsConnectionState, setWsConnectionState] = useState('disconnected');

  // Selected items for highlighting
  const [selectedEmergency, setSelectedEmergency] = useState(null);
  const [selectedAmbulance, setSelectedAmbulance] = useState(null);
  const [trackedPair, setTrackedPair] = useState(null); // { emergencyId, ambulanceId }
  const [mapClickMode, setMapClickMode] = useState(false);
  const [showAdminTools, setShowAdminTools] = useState(false);
  const [routeData, setRouteData] = useState({});
  const locationHandlerRef = useRef(null);

  /**
   * Fetch initial emergency data
   */
  const fetchEmergencies = useCallback(async () => {
    try {
      setIsLoadingEmergencies(true);
      const [pending, assigned] = await Promise.all([
        emergencyApi.getByStatus('PENDING'),
        emergencyApi.getByStatus('ASSIGNED'),
      ]);
      // Ensure both are arrays before spreading
      const pendingArray = Array.isArray(pending) ? pending : [];
      const assignedArray = Array.isArray(assigned) ? assigned : [];
      setEmergencies([...pendingArray, ...assignedArray]);
    } catch (error) {
      console.error('Error fetching emergencies:', error);
      // Set empty array on error
      setEmergencies([]);

      if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        showToast('Request timeout while loading emergencies. Please check your connection.', 'error');
      } else if (error.code === 'ERR_NETWORK' || !error.response) {
        showToast('Network error: Unable to reach server. Please check your connection.', 'error');
      } else if (error.response?.status === 403) {
        showToast('Access denied: You do not have permission to view emergencies.', 'error');
      } else if (error.response?.status >= 500) {
        showToast('Server error while loading emergencies. Please try again later.', 'error');
      } else {
        showToast('Failed to load emergencies. Please try again.', 'error');
      }
    } finally {
      setIsLoadingEmergencies(false);
    }
  }, [showToast]);

  /**
   * Fetch initial ambulance data
   */
  const fetchAmbulances = useCallback(async (silent = false) => {
    try {
      if (!silent) {
        setIsLoadingAmbulances(true);
      }
      const fleet = await ambulanceApi.getFleet();
      // Ensure fleet is an array
      setAmbulances(Array.isArray(fleet) ? fleet : []);
    } catch (error) {
      console.error('Error fetching ambulances:', error);
      // Set empty array on error
      setAmbulances([]);

      if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        showToast('Request timeout while loading fleet. Please check your connection.', 'error');
      } else if (error.code === 'ERR_NETWORK' || !error.response) {
        showToast('Network error: Unable to reach server. Please check your connection.', 'error');
      } else if (error.response?.status === 403) {
        showToast('Access denied: You do not have permission to view fleet data.', 'error');
      } else if (error.response?.status >= 500) {
        showToast('Server error while loading fleet. Please try again later.', 'error');
      } else {
        showToast('Failed to load ambulance fleet. Please try again.', 'error');
      }
    } finally {
      if (!silent) {
        setIsLoadingAmbulances(false);
        setIsLoadingMap(false);
      }
    }
  }, [showToast]);

  /**
   * Handle emergency marker click - enable tracking
   */
  const handleEmergencyClick = (emergency) => {
    setSelectedEmergency(emergency);
    setSelectedAmbulance(null);
    
    // If emergency is assigned, set up tracking
    if (emergency.status === 'ASSIGNED' && emergency.assignedAmbulanceId) {
      setTrackedPair({
        emergencyId: emergency.id,
        ambulanceId: emergency.assignedAmbulanceId
      });
    } else {
      setTrackedPair(null);
    }
  };

  /**
   * Handle ambulance marker click
   */
  const handleAmbulanceClick = (ambulance) => {
    setSelectedAmbulance(ambulance);
    setSelectedEmergency(null);
    setTrackedPair(null);
  };

  const applyEmergencyUpdate = useCallback((update) => {
    if (!update?.emergencyId || !update?.status) {
      return;
    }
    setEmergencies(prev => {
      const idx = prev.findIndex(e => e.id === update.emergencyId || e.emergencyId === update.emergencyId);
      if (update.status === 'COMPLETED') {
        if (idx === -1) return prev;
        const copy = [...prev];
        copy.splice(idx, 1);
        return copy;
      }
      if (idx === -1) return prev;
      const next = [...prev];
      next[idx] = {
        ...next[idx],
        status: update.status,
        assignedAmbulanceId: update.ambulanceId ?? next[idx].assignedAmbulanceId,
      };
      return next;
    });

    if (update.ambulanceId) {
      setAmbulances(prev => prev.map(amb => {
        if (amb.id !== update.ambulanceId) {
          return amb;
        }
        let nextStatus = amb.status;
        if (update.status === 'ASSIGNED') {
          nextStatus = 'ASSIGNED';
        } else if (update.status === 'COMPLETED') {
          nextStatus = 'AVAILABLE';
        }
        return { ...amb, status: nextStatus };
      }));
    }
  }, []);

  /**
   * Connect to WebSocket and subscribe to real-time updates
   */
  useEffect(() => {
    if (!accessToken) return;

    let unsubLocations;
    let reconnectTimeout;
    let unsubEmergencies;

    const connectWebSocket = async () => {
      try {
        setWsConnectionState('connecting');

        await wsService.connect(accessToken);
        setWsConnected(true);
        setWsConnectionState('connected');

        // Subscribe to ambulance location updates (only real topic that exists)
        unsubLocations = wsService.subscribe('/topic/location', (update) => {
          setAmbulances(prev => {
            const exists = prev.some(amb => amb.id === update.ambulanceId);
            if (!exists) {
              return [
                ...prev,
                {
                  id: update.ambulanceId,
                  status: update.status ?? 'AVAILABLE',
                  version: 0,
                  latitude: update.latitude,
                  longitude: update.longitude,
                  speed: update.speed ?? 0,
                  heading: update.heading ?? 0,
                  available: (update.status ?? 'AVAILABLE') === 'AVAILABLE',
                },
              ];
            }

            return prev.map(amb =>
              amb.id === update.ambulanceId
                ? {
                  ...amb,
                  latitude: update.latitude,
                  longitude: update.longitude,
                  speed: update.speed,
                  heading: update.heading,
                  status: update.status ?? amb.status
                }
                : amb
            );
          });
        });
        unsubEmergencies = wsService.subscribe('/topic/emergencies', (update) => {
          applyEmergencyUpdate(update);
        });

        console.log('WebSocket subscriptions established');
      } catch (error) {
        console.error('WebSocket connection failed:', error);
        setWsConnected(false);
        setWsConnectionState('error');

        if (error.message?.includes('401') || error.message?.includes('Unauthorized')) {
          showToast('WebSocket authentication failed. Please log in again.', 'error');
        } else if (error.message?.includes('timeout')) {
          showToast('WebSocket connection timeout. Retrying...', 'warning');
          reconnectTimeout = setTimeout(() => {
            if (accessToken) {
              connectWebSocket();
            }
          }, 5000);
        } else {
          showToast('Real-time updates unavailable. Some features may be limited.', 'warning');
          reconnectTimeout = setTimeout(() => {
            if (accessToken) {
              connectWebSocket();
            }
          }, 5000);
        }
      }
    };

    connectWebSocket();

    return () => {
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
      }
      if (unsubLocations) unsubLocations();
      if (unsubEmergencies) unsubEmergencies();
      wsService.disconnect();
      setWsConnected(false);
      setWsConnectionState('disconnected');
    };
  }, [accessToken, showToast, applyEmergencyUpdate]);

  /**
   * Fetch initial data on mount
   */
  useEffect(() => {
    fetchEmergencies();
    fetchAmbulances(false);
  }, [fetchEmergencies, fetchAmbulances]);

  /**
   * Handle clear tracking event
   */
  useEffect(() => {
    const handleClearTracking = () => {
      setTrackedPair(null);
      setSelectedEmergency(null);
      setSelectedAmbulance(null);
    };

    window.addEventListener('clearTracking', handleClearTracking);
    return () => window.removeEventListener('clearTracking', handleClearTracking);
  }, []);

  /**
   * Fetch routes for assigned emergencies when emergencies or ambulances change
   * Routes are dynamically updated and trimmed to show only the path between current positions
   */
  useEffect(() => {
    const fetchRoutes = async () => {
      // Find emergencies that have assigned ambulances
      const assignedEmergencies = emergencies.filter(
        e => e.status === 'ASSIGNED' && e.assignedAmbulanceId
      );

      // Clear routes for emergencies that are no longer assigned
      setRouteData(prev => {
        const newRouteData = {};
        assignedEmergencies.forEach(emergency => {
          const routeKey = `${emergency.id}-${emergency.assignedAmbulanceId}`;
          if (prev[routeKey]) {
            newRouteData[routeKey] = prev[routeKey];
          }
        });
        return newRouteData;
      });

      for (const emergency of assignedEmergencies) {
        const ambulance = ambulances.find(
          a => a.id === emergency.assignedAmbulanceId || a.id === emergency.ambulanceId
        );

        if (ambulance && ambulance.latitude && ambulance.longitude) {
          const emergencyLat = emergency.latitude ?? emergency.lat;
          const emergencyLon = emergency.longitude ?? emergency.lon;

          if (emergencyLat && emergencyLon) {
            const routeKey = `${emergency.id}-${ambulance.id}`;
            
            try {
              const route = await fetchOSRMRoute(
                ambulance.latitude,
                ambulance.longitude,
                emergencyLat,
                emergencyLon
              );
              
              if (route && route.coordinates) {
                // Trim route to show only path between current ambulance position and emergency
                const ambulancePoint = [ambulance.latitude, ambulance.longitude];
                const emergencyPoint = [emergencyLat, emergencyLon];
                const rawCoordinates = Array.isArray(route.coordinates) ? route.coordinates : [];

                let normalized = rawCoordinates;
                const startIdx = nearestIndex(rawCoordinates, ambulancePoint);
                const endIdx = nearestIndex(rawCoordinates, emergencyPoint);
                
                if (startIdx >= 0 && endIdx >= 0) {
                  const from = Math.min(startIdx, endIdx);
                  const to = Math.max(startIdx, endIdx);
                  normalized = rawCoordinates.slice(from, to + 1);
                }

                const finalCoordinates = [ambulancePoint, ...normalized, emergencyPoint];
                
                setRouteData(prev => ({
                  ...prev,
                  [routeKey]: {
                    coordinates: finalCoordinates,
                    distance: route.distance,
                    duration: route.duration,
                  },
                }));
              }
            } catch (error) {
              console.error('Error fetching route:', error);
            }
          }
        }
      }
    };

    if (emergencies.length > 0 && ambulances.length > 0) {
      fetchRoutes();
      
      // Refresh routes every 10 seconds to keep them updated
      const interval = setInterval(fetchRoutes, 10000);
      return () => clearInterval(interval);
    }
  }, [emergencies, ambulances]);

  return (
    <div className="dispatcher-dashboard">
      {/* Header with logout button */}
      <header className="dashboard-header">
        <h1>Admin Dashboard</h1>
        <div className="header-actions">
          <span className="user-info">Welcome, {user?.username}</span>

          {/* WebSocket connection status indicator */}
          <ConnectionStatus status={wsConnectionState} />
          {wsConnectionState === 'disconnected' && !wsConnected && (
            <span className="ws-status ws-disconnected" title="Real-time updates offline">
              ● Offline
            </span>
          )}

          <button onClick={logout} className="logout-button">
            Logout
          </button>
        </div>
      </header>

      {/* Main content area with two-column layout */}
      <main className="dashboard-content">
        {/* Map panel - 60% width */}
        <div className="map-panel">
          {isLoadingMap ? (
            <SkeletonLoader type="map" />
          ) : (
            <>
              <MapComponent
                center={[18.5204, 73.8567]}
                zoom={13}
                onMapClick={mapClickMode ? (lat, lng) => {
                  locationHandlerRef.current?.(lat, lng);
                } : undefined}
              >
                {/* Render emergency markers */}
                {emergencies.map(emergency => (
                  <EmergencyMarker
                    key={emergency.id}
                    emergency={emergency}
                    onClick={handleEmergencyClick}
                  />
                ))}

                {/* Render ambulance markers */}
                {ambulances.map(ambulance => (
                  <AmbulanceMarker
                    key={ambulance.id}
                    ambulance={ambulance}
                    onClick={handleAmbulanceClick}
                  />
                ))}

                {/* Render route polylines for assigned emergencies - using actual OSRM route coordinates */}
                {Object.entries(routeData).map(([routeKey, route]) => {
                  if (!route.coordinates || route.coordinates.length < 2) return null;
                  const isTracked = trackedPair && 
                    routeKey === `${trackedPair.emergencyId}-${trackedPair.ambulanceId}`;
                  
                  return (
                    <Polyline
                      key={routeKey}
                      positions={route.coordinates}
                      pathOptions={{
                        color: isTracked ? '#f59e0b' : '#3b82f6',
                        weight: isTracked ? 6 : 5,
                        opacity: isTracked ? 0.95 : 0.8,
                        lineCap: 'round',
                        lineJoin: 'round',
                      }}
                    />
                  );
                })}
              </MapComponent>

              {/* Emergency creation control */}
              <EmergencyCreationControl
                onEmergencyCreated={(e) => setEmergencies(prev => [...prev, e])}
                onModeChange={setMapClickMode}
                onLocationHandlerReady={(handler) => {
                  locationHandlerRef.current = handler;
                }}
                showToast={showToast}
              />
            </>
          )}
        </div>

        {/* Side panel - 40% width */}
        <div className="side-panel">
          {/* Tracking Panel - shows when emergency-ambulance pair is tracked */}
          {trackedPair && (() => {
            const emergency = emergencies.find(e => e.id === trackedPair.emergencyId);
            const ambulance = ambulances.find(a => a.id === trackedPair.ambulanceId);
            const routeKey = `${trackedPair.emergencyId}-${trackedPair.ambulanceId}`;
            const route = routeData[routeKey];
            
            return emergency && ambulance ? (
              <TrackingPanel 
                emergency={emergency}
                ambulance={ambulance}
                routeData={route}
              />
            ) : null;
          })()}

          {/* Tracking hint - only show when no tracking is active */}
          {!trackedPair && emergencies.some(e => e.status === 'ASSIGNED') && (
            <div className="tracking-hint-panel">
              <p>💡 Click on an assigned emergency to track ambulance in real-time</p>
            </div>
          )}

          {/* Emergency Queue Panel */}
          {isLoadingEmergencies ? (
            <SkeletonLoader type="card" count={3} />
          ) : (
            <EmergencyQueuePanel
              emergencies={emergencies}
              ambulances={ambulances}
              onEmergencyClick={handleEmergencyClick}
            />
          )}

          {/* Fleet Status Panel */}
          {isLoadingAmbulances ? (
            <SkeletonLoader type="table" count={5} />
          ) : (
            <FleetStatusPanel ambulances={ambulances} />
          )}

          {/* Admin tools toggle */}
          <div className="metrics-link-panel">
            <h2>Admin Tools</h2>
            <p>Use advanced operational tools only when needed.</p>
            <button
              type="button"
              className="grafana-link-button"
              onClick={() => setShowAdminTools(prev => !prev)}
            >
              {showAdminTools ? 'Hide Admin Tools' : 'Show Admin Tools'}
            </button>
          </div>

          {/* Admin-specific panels (collapsible) */}
          {showAdminTools && (
            <>
              <FleetManagementPanel />
              <SystemHealthPanel />
            </>
          )}

          {/* Grafana Metrics Link */}
          <div className="metrics-link-panel">
            <h2>System Metrics</h2>
            <p>View detailed system metrics and performance data in Grafana.</p>
            <a
              href="http://localhost:3001"
              target="_blank"
              rel="noopener noreferrer"
              className="grafana-link-button"
            >
              Open Grafana Dashboard
            </a>
          </div>
        </div>
      </main>
    </div>
  );
};

export default AdminDashboard;
