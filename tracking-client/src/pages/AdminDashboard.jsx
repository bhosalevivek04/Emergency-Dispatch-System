import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { emergencyApi, ambulanceApi } from '../services/api';
import { wsService } from '../services/websocketService';
import MapComponent from '../components/map/MapComponent';
import EmergencyMarker from '../components/map/EmergencyMarker';
import AmbulanceMarker from '../components/map/AmbulanceMarker';
import EmergencyQueuePanel from '../components/EmergencyQueuePanel';
import FleetStatusPanel from '../components/FleetStatusPanel';
import EmergencyCreationControl from '../components/EmergencyCreationControl';
import FleetManagementPanel from '../components/FleetManagementPanel';
import SystemHealthPanel from '../components/SystemHealthPanel';
import SkeletonLoader from '../components/SkeletonLoader';
import ConnectionStatus from '../components/ConnectionStatus';
import { useToast } from '../contexts/ToastContext';
import '../pages/DispatcherDashboard.css';

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
  const fetchAmbulances = useCallback(async () => {
    try {
      setIsLoadingAmbulances(true);
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
      setIsLoadingAmbulances(false);
      setIsLoadingMap(false);
    }
  }, [showToast]);

  /**
   * Handle emergency creation
   */
  const handleCreateEmergency = async (latitude, longitude, priority) => {
    try {
      const newEmergency = await emergencyApi.create({
        latitude,
        longitude,
        priority,
      });
      
      setEmergencies(prev => [...prev, newEmergency]);
      showToast(`Emergency ${newEmergency.id} created successfully`, 'success');
      return newEmergency;
    } catch (error) {
      console.error('Error creating emergency:', error);
      
      if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        showToast('Request timeout while creating emergency. Please try again.', 'error');
      } else if (error.code === 'ERR_NETWORK' || !error.response) {
        showToast('Network error: Unable to create emergency. Please check your connection.', 'error');
      } else if (error.response?.status === 400) {
        showToast('Invalid emergency data. Please check the location and priority.', 'error');
      } else if (error.response?.status === 403) {
        showToast('Access denied: You do not have permission to create emergencies.', 'error');
      } else if (error.response?.status >= 500) {
        showToast('Server error while creating emergency. Please try again later.', 'error');
      } else {
        showToast('Failed to create emergency. Please try again.', 'error');
      }
      
      throw error;
    }
  };

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

  /**
   * Connect to WebSocket and subscribe to real-time updates
   */
  useEffect(() => {
    if (!accessToken) return;

    let unsubEmergencies;
    let unsubLocations;
    let unsubStatus;
    let reconnectTimeout;

    const connectWebSocket = async () => {
      try {
        setWsConnectionState('connecting');
        
        await wsService.connect(accessToken);
        setWsConnected(true);
        setWsConnectionState('connected');

        unsubEmergencies = wsService.subscribe('/topic/emergencies', (emergency) => {
          console.log('Received emergency update:', emergency);
          setEmergencies(prev => {
            const index = prev.findIndex(e => e.id === emergency.id);
            if (index >= 0) {
              const updated = [...prev];
              updated[index] = emergency;
              return updated;
            } else {
              return [...prev, emergency];
            }
          });
        });

        unsubLocations = wsService.subscribe('/topic/ambulances/location', (update) => {
          console.log('Received location update:', update);
          setAmbulances(prev =>
            prev.map(amb =>
              amb.id === update.ambulanceId
                ? { ...amb, latitude: update.latitude, longitude: update.longitude }
                : amb
            )
          );
        });

        unsubStatus = wsService.subscribe('/topic/ambulances/status', (ambulance) => {
          console.log('Received status update:', ambulance);
          setAmbulances(prev =>
            prev.map(amb =>
              amb.id === ambulance.id ? ambulance : amb
            )
          );
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
      if (unsubEmergencies) unsubEmergencies();
      if (unsubLocations) unsubLocations();
      if (unsubStatus) unsubStatus();
      wsService.disconnect();
      setWsConnected(false);
      setWsConnectionState('disconnected');
    };
  }, [accessToken, showToast]);

  /**
   * Fetch initial data on mount
   */
  useEffect(() => {
    fetchEmergencies();
    fetchAmbulances();
  }, [fetchEmergencies, fetchAmbulances]);

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
                onCreateEmergency={handleCreateEmergency}
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
              onEmergencyClick={handleEmergencyClick}
            />
          )}

          {/* Fleet Status Panel */}
          {isLoadingAmbulances ? (
            <SkeletonLoader type="table" count={5} />
          ) : (
            <FleetStatusPanel ambulances={ambulances} />
          )}

          {/* Admin-specific panels */}
          <FleetManagementPanel />
          <SystemHealthPanel />
          
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
