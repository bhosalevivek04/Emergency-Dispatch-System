import { useState, useEffect } from 'react';
import { diagnosticApi, ambulanceApi } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import LoadingSpinner from './LoadingSpinner';
import './FleetManagementPanel.css';

/**
 * FleetManagementPanel component
 * Displays detailed fleet status and provides fleet initialization control
 * Admin-only component
 */
const FleetManagementPanel = () => {
  const [fleetStatus, setFleetStatus] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isInitializing, setIsInitializing] = useState(false);
  const { showToast } = useToast();

  /**
   * Fetch fleet status from diagnostic API
   */
  const fetchFleetStatus = async () => {
    try {
      setIsLoading(true);
      const status = await diagnosticApi.getFleetStatus();
      setFleetStatus(status);
    } catch (error) {
      console.error('Failed to fetch fleet status:', error);
      showToast('Failed to fetch fleet status', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * Initialize the fleet
   */
  const handleInitFleet = async () => {
    try {
      setIsInitializing(true);
      await diagnosticApi.initFleet();
      showToast('Fleet initialized successfully', 'success');
      // Refresh fleet status after initialization
      await fetchFleetStatus();
    } catch (error) {
      console.error('Failed to initialize fleet:', error);
      showToast('Failed to initialize fleet', 'error');
    } finally {
      setIsInitializing(false);
    }
  };

  // Fetch fleet status on mount
  useEffect(() => {
    fetchFleetStatus();
  }, []);

  if (isLoading) {
    return (
      <div className="fleet-management-panel">
        <h2>Fleet Management</h2>
        <LoadingSpinner size="medium" />
      </div>
    );
  }

  return (
    <div className="fleet-management-panel">
      <div className="panel-header">
        <h2>Fleet Management</h2>
        <button
          onClick={handleInitFleet}
          disabled={isInitializing}
          className="init-fleet-button"
        >
          {isInitializing ? 'Initializing...' : 'Initialize Fleet'}
        </button>
      </div>

      {fleetStatus && (
        <div className="fleet-summary">
          <div className="summary-item">
            <span className="summary-label">Total Ambulances:</span>
            <span className="summary-value">{fleetStatus.totalCount || 0}</span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Available:</span>
            <span className="summary-value">{fleetStatus.availableCount || 0}</span>
          </div>
        </div>
      )}

      <div className="fleet-table-container">
        <table className="fleet-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Status</th>
              <th>Speed (km/h)</th>
              <th>Location</th>
              <th>Version</th>
              <th>Assigned Emergency</th>
            </tr>
          </thead>
          <tbody>
            {fleetStatus?.ambulances?.length > 0 ? (
              fleetStatus.ambulances.map((ambulance) => (
                <tr key={ambulance.id}>
                  <td>{ambulance.id}</td>
                  <td>
                    <span className={`status-badge status-${ambulance.status?.toLowerCase()}`}>
                      {ambulance.status}
                    </span>
                  </td>
                  <td>{ambulance.speed?.toFixed(1) || '0.0'}</td>
                  <td>
                    {ambulance.latitude?.toFixed(4)}, {ambulance.longitude?.toFixed(4)}
                  </td>
                  <td>{ambulance.version || 'N/A'}</td>
                  <td>{ambulance.assignedEmergencyId || '-'}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="6" className="no-data">
                  No ambulances in fleet. Click "Initialize Fleet" to create ambulances.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default FleetManagementPanel;
