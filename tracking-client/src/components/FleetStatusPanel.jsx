import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { AmbulancePropType } from '../utils/constants';
import './FleetStatusPanel.css';

/**
 * FleetStatusPanel component
 * Displays the current status of all ambulances in the fleet
 * Shows summary counts by status and a detailed table
 * 
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6
 * 
 * @param {Object} props
 * @param {Array<Ambulance>} props.ambulances - Array of all ambulances in the fleet
 */
const FleetStatusPanel = ({ ambulances }) => {
  // Calculate summary counts by status
  const statusCounts = useMemo(() => {
    const counts = {
      AVAILABLE: 0,
      ASSIGNED: 0,
      ON_ROUTE: 0,
    };

    ambulances.forEach((ambulance) => {
      if (counts.hasOwnProperty(ambulance.status)) {
        counts[ambulance.status]++;
      }
    });

    return counts;
  }, [ambulances]);

  /**
   * Get CSS class for status badge
   * @param {string} status - Status (AVAILABLE, ASSIGNED, ON_ROUTE)
   * @returns {string} CSS class name
   */
  const getStatusClass = (status) => {
    return `status-badge status-${status.toLowerCase().replace('_', '-')}`;
  };

  /**
   * Get display label for status
   * @param {string} status - Status (AVAILABLE, ASSIGNED, ON_ROUTE)
   * @returns {string} Display label
   */
  const getStatusLabel = (status) => {
    return status.replace('_', ' ');
  };

  return (
    <div className="fleet-status-panel">
      <div className="panel-header">
        <h2>Fleet Status</h2>
        <span className="fleet-count">{ambulances.length} Total</span>
      </div>

      {/* Summary counts by status */}
      <div className="status-summary">
        <div className="status-count-card status-available">
          <div className="count-label">Available</div>
          <div className="count-value">{statusCounts.AVAILABLE}</div>
        </div>

        <div className="status-count-card status-assigned">
          <div className="count-label">Assigned</div>
          <div className="count-value">{statusCounts.ASSIGNED}</div>
        </div>

        <div className="status-count-card status-on-route">
          <div className="count-label">On Route</div>
          <div className="count-value">{statusCounts.ON_ROUTE}</div>
        </div>
      </div>

      {/* Ambulance table */}
      <div className="fleet-table-container">
        {ambulances.length === 0 ? (
          <div className="empty-state">
            <p>No ambulances in fleet</p>
          </div>
        ) : (
          <table className="fleet-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Status</th>
                <th>Speed</th>
              </tr>
            </thead>
            <tbody>
              {ambulances.map((ambulance) => (
                <tr key={ambulance.id}>
                  <td className="ambulance-id">{ambulance.id}</td>
                  <td>
                    <span className={getStatusClass(ambulance.status)}>
                      {getStatusLabel(ambulance.status)}
                    </span>
                  </td>
                  <td className="ambulance-speed">
                    {ambulance.speed.toFixed(1)} km/h
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

FleetStatusPanel.propTypes = {
  ambulances: PropTypes.arrayOf(AmbulancePropType).isRequired,
};

export default FleetStatusPanel;
