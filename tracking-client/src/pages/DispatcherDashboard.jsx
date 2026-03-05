import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { emergencyApi, ambulanceApi } from '../services/api';
import { wsService } from '../services/websocketService';
import MapComponent from '../components/map/MapComponent';
import EmergencyMarker from '../components/map/EmergencyMarker';
import AmbulanceMarker from '../components/map/AmbulanceMarker';
import EmergencyQueuePanel from '../components/EmergencyQueuePanel';
import FleetStatusPanel from '../components/FleetStatusPanel';
import EmergencyCreationControl from '../components/EmergencyCreationControl';
import SkeletonLoader from '../components/SkeletonLoader';
import ConnectionStatus from '../components/ConnectionStatus';
import { useToast } from '../contexts/ToastContext';
import './DispatcherDashboard.css';

/**
 * DispatcherDashboard component
 * Provides a two-column layout with map panel (60%) and side panel (40%)
 * Includes header with logout button, real-time updates via WebSocket
 * 
 * Requirements: 6.1, 3.1, 6.2, 6.3, 6.4, 6.5, 18.1, 18.5, 19.1, 19.2, 19.3, 20.1, 20.2, 20.3, 21.1, 21.2, 21.3, 21.4, 24.1, 24.3, 24.4, 24.5
 */
const DispatcherDashboard = () => {
  const { user, logout, accessToken } = useAuth();
  const { showToast } = useToast();

  // State for emergencies and ambulances
  const [emergencies, setEmergencies] = useState([]);
  const [ambulances, setAmbulances] = useState([]);

  // Loading states
  const [isLoadingEmergencies, setIsLoadingEmergencies] = useState(true);
  const [isLoadingAmbulances, setIsLoadingAmbulances] = useState(true);
  const [isLoadingMap, setIsLoadingMap] = useState(true);

  // WebSocket connection state
  const [wsConnectionState, setWsConnectionState] = useState('disconnected'); // 'connected', 'connecting', 'disconnected', 'error'

  // Selected items for highlighting
  const [selectedEmergency, setSelectedEmergency] = useState(null);
  const [selectedAmbulance, setSelectedAmbulance] = useState(null);
  const [mapClickMode, setMapClickMode] = useState(false);
  const locationHandlerRef = useRef(null);

  /**
   * Fetch initial emergency data
   */
  const fetchEmergencies = useCallback(async (silent = false) => {
    try {
      if (!silent) {
        setIsLoadingEmergencies(true);
      }
      // Fetch both PENDING and ASSIGNED emergencies
      const [pending, assigned] = await Promise.all([
        emergencyApi.getByStatus('PENDING'),
        emergencyApi.getByStatus('ASSIGNED'),
      ]);
      // Ensure both are arrays before spreading
      const pendingArray = Array.isArray(pending) ? pending : [];
      const assignedArray = Array.isArray(assigned) ? assigned : [];
      setEmergencies([...pendingArray, ...assignedArray]);
    } catch (error) {
      // Set empty array on error
      setEmergencies([]);

      // Provide specific error messages based on error type
      if (!silent) {
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
      }
    } finally {
      if (!silent) {
        setIsLoadingEmergencies(false);
      }
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
      // Set empty array on error
      setAmbulances([]);

      // Provide specific error messages based on error type
      if (!silent) {
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
      }
    } finally {
      if (!silent) {
        setIsLoadingAmbulances(false);
        setIsLoadingMap(false); // Map can render once we have ambulance data
      }
    }
  }, [showToast]);

  /**
   * Handle emergency marker click
   */
  const handleEmergencyClick = (emergency) => {
    setSelectedEmergency(emergency);
    setSelectedAmbulance(null);
  };

  /**
   * Handle ambulance marker click
   */
  const handleAmbulanceClick = (ambulance) => {
    setSelectedAmbulance(ambulance);
    setSelectedEmergency(null);
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
    let unsubStateListener;

    const connectWebSocket = async () => {
      try {
        setWsConnectionState('connecting');

        // Connect to WebSocket with JWT token
        await wsService.connect(accessToken);

        // Subscribe to ambulance location updates (only real topic that exists)
        unsubLocations = wsService.subscribe('/topic/location', (update) => {
          setAmbulances(prev =>
            prev.map(amb =>
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
            )
          );
        });
        unsubEmergencies = wsService.subscribe('/topic/emergencies', (update) => {
          applyEmergencyUpdate(update);
        });
      } catch (error) {
        setWsConnectionState('error');

        // Provide specific error messages
        if (error.message?.includes('401') || error.message?.includes('Unauthorized')) {
          showToast('WebSocket authentication failed. Please log in again.', 'error');
        } else if (error.message?.includes('timeout')) {
          showToast('WebSocket connection timeout. Retrying...', 'warning');
          // Attempt to reconnect after a delay
          reconnectTimeout = setTimeout(() => {
            if (accessToken) {
              connectWebSocket();
            }
          }, 5000);
        } else {
          showToast('Real-time updates unavailable. Some features may be limited.', 'warning');
          // Attempt to reconnect after a delay
          reconnectTimeout = setTimeout(() => {
            if (accessToken) {
              connectWebSocket();
            }
          }, 5000);
        }
      }
    };

    unsubStateListener = wsService.onConnectionStateChange((state) => {
      setWsConnectionState(state);
    });

    connectWebSocket();

    // Cleanup on unmount
    return () => {
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
      }
      if (unsubLocations) unsubLocations();
      if (unsubEmergencies) unsubEmergencies();
      if (unsubStateListener) unsubStateListener();
      wsService.disconnect();
      setWsConnectionState('disconnected');
    };
  }, [accessToken, showToast, applyEmergencyUpdate]);

  /**
   * Fetch initial data on mount
   */
  useEffect(() => {
    fetchEmergencies(false);
    fetchAmbulances(false);
  }, [fetchEmergencies, fetchAmbulances]);

  useEffect(() => {
    if (wsConnectionState === 'connected') {
      return;
    }

    const fallbackPoller = setInterval(() => {
      fetchEmergencies(true);
      fetchAmbulances(true);
    }, 5000);

    return () => clearInterval(fallbackPoller);
  }, [wsConnectionState, fetchEmergencies, fetchAmbulances]);

  return (
    <div className="dispatcher-dashboard">
      {/* Header with logout button */}
      <header className="dashboard-header">
        <h1>Dispatcher Dashboard</h1>
        <div className="header-actions">
          <span className="user-info">Welcome, {user?.username}</span>

          {/* WebSocket connection status indicator */}
          <ConnectionStatus status={wsConnectionState} />
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
        </div>
      </main>
    </div>
  );
};

export default DispatcherDashboard;
