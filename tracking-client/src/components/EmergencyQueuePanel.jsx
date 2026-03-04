import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { EmergencyPropType } from '../utils/constants';
import './EmergencyQueuePanel.css';

/**
 * EmergencyQueuePanel component
 * Displays a real-time list of active emergencies (PENDING and ASSIGNED status)
 * Sorted by priority (HIGH -> MEDIUM -> LOW)
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.6
 * 
 * @param {Object} props
 * @param {Array<Emergency>} props.emergencies - Array of all emergencies
 * @param {Function} props.onEmergencyClick - Callback when an emergency is clicked
 */
const EmergencyQueuePanel = ({ emergencies, onEmergencyClick }) => {
  // Filter and sort emergencies
  const activeEmergencies = useMemo(() => {
    // Filter to PENDING and ASSIGNED status only
    const filtered = emergencies.filter(
      emergency => emergency.status === 'PENDING' || emergency.status === 'ASSIGNED'
    );

    // Sort by priority: HIGH -> MEDIUM -> LOW
    const priorityOrder = { HIGH: 1, MEDIUM: 2, LOW: 3 };
    return filtered.sort((a, b) => priorityOrder[a.priority] - priorityOrder[b.priority]);
  }, [emergencies]);

  /**
   * Calculate time since emergency was created
   * @param {string} createdAt - ISO 8601 timestamp
   * @returns {string} Human-readable time difference
   */
  const getTimeSinceCreation = (createdAt) => {
    const now = new Date();
    const created = new Date(createdAt);
    const diffMs = now - created;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffDays > 0) {
      return `${diffDays}d ${diffHours % 24}h ago`;
    } else if (diffHours > 0) {
      return `${diffHours}h ${diffMins % 60}m ago`;
    } else if (diffMins > 0) {
      return `${diffMins}m ago`;
    } else {
      return 'Just now';
    }
  };

  /**
   * Get CSS class for priority badge
   * @param {string} priority - Priority level (HIGH, MEDIUM, LOW)
   * @returns {string} CSS class name
   */
  const getPriorityClass = (priority) => {
    return `priority-badge priority-${priority.toLowerCase()}`;
  };

  /**
   * Get CSS class for emergency card row
   * @param {string} priority - Priority level (HIGH, MEDIUM, LOW)
   * @returns {string} CSS class name
   */
  const getCardClass = (priority) => {
    return `emergency-card emergency-${priority.toLowerCase()}`;
  };

  return (
    <div className="emergency-queue-panel">
      <div className="panel-header">
        <h2>Emergency Queue</h2>
        <span className="emergency-count">{activeEmergencies.length} Active</span>
      </div>

      <div className="emergency-list">
        {activeEmergencies.length === 0 ? (
          <div className="empty-state">
            <p>No active emergencies</p>
          </div>
        ) : (
          activeEmergencies.map((emergency) => (
            <div
              key={emergency.id}
              className={getCardClass(emergency.priority)}
              onClick={() => onEmergencyClick && onEmergencyClick(emergency)}
            >
              {/* Priority badge */}
              <div className={getPriorityClass(emergency.priority)}>
                {emergency.priority}
              </div>

              {/* Emergency details */}
              <div className="emergency-details">
                <div className="emergency-id">
                  <span className="label">ID:</span>
                  <span className="value">{emergency.id}</span>
                </div>

                <div className="emergency-status">
                  <span className="label">Status:</span>
                  <span className={`status-badge status-${emergency.status.toLowerCase()}`}>
                    {emergency.status}
                  </span>
                </div>

                <div className="emergency-time">
                  <span className="label">Created:</span>
                  <span className="value">{getTimeSinceCreation(emergency.createdAt)}</span>
                </div>

                {emergency.assignedAmbulanceId && (
                  <div className="emergency-ambulance">
                    <span className="label">Ambulance:</span>
                    <span className="value">{emergency.assignedAmbulanceId}</span>
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

EmergencyQueuePanel.propTypes = {
  emergencies: PropTypes.arrayOf(EmergencyPropType).isRequired,
  onEmergencyClick: PropTypes.func,
};

export default EmergencyQueuePanel;
