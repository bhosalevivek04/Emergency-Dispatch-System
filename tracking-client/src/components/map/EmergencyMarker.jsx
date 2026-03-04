import { Marker, Popup } from 'react-leaflet';
import PropTypes from 'prop-types';
import L from 'leaflet';

/**
 * Create a custom emergency marker icon with priority badge
 */
const createEmergencyIcon = (priority) => {
  const priorityColors = {
    HIGH: '#dc2626',
    MEDIUM: '#f97316',
    LOW: '#3b82f6',
  };

  const color = priorityColors[priority] || priorityColors.MEDIUM;

  const svgString = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 140">
      <!-- Pin body -->
      <path d="M50 10 C30 10, 15 25, 15 45 C15 70, 50 110, 50 110 C50 110, 85 70, 85 45 C85 25, 70 10, 50 10 Z" 
            fill="${color}" stroke="#991b1b" stroke-width="2"/>
      <!-- Pin inner circle -->
      <circle cx="50" cy="45" r="20" fill="white" opacity="0.9"/>
      <!-- Emergency symbol (!) -->
      <text x="50" y="50" font-size="28" font-weight="bold" text-anchor="middle" fill="${color}">!</text>
      <!-- Priority badge -->
      <rect x="35" y="65" width="30" height="12" rx="2" fill="white" opacity="0.95"/>
      <text x="50" y="74" font-size="8" font-weight="bold" text-anchor="middle" fill="${color}">${priority}</text>
    </svg>
  `;

  return L.divIcon({
    html: svgString,
    iconSize: [40, 56],
    iconAnchor: [20, 56],
    popupAnchor: [0, -56],
    className: 'emergency-marker',
  });
};

/**
 * EmergencyMarker - Displays an emergency location on the map
 * Shows a red pin with priority badge and popup with emergency details
 */
export default function EmergencyMarker({ emergency, onClick }) {
  const { id, latitude, longitude, priority, status, assignedAmbulanceId } = emergency;
  const position = [latitude, longitude];
  const icon = createEmergencyIcon(priority);

  const handleClick = () => {
    if (onClick) {
      onClick(emergency);
    }
  };

  return (
    <Marker 
      position={position} 
      icon={icon}
      eventHandlers={{
        click: handleClick,
      }}
    >
      <Popup>
        <div style={{ minWidth: '150px' }}>
          <h3 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
            🚨 Emergency {id}
          </h3>
          <div style={{ fontSize: '12px', lineHeight: '1.6' }}>
            <div>
              <strong>Priority:</strong>{' '}
              <span style={{
                padding: '2px 6px',
                borderRadius: '3px',
                backgroundColor: priority === 'HIGH' ? '#fee2e2' : priority === 'MEDIUM' ? '#fed7aa' : '#dbeafe',
                color: priority === 'HIGH' ? '#dc2626' : priority === 'MEDIUM' ? '#f97316' : '#3b82f6',
                fontWeight: 'bold',
              }}>
                {priority}
              </span>
            </div>
            <div><strong>Status:</strong> {status}</div>
            {assignedAmbulanceId && (
              <div><strong>Assigned:</strong> {assignedAmbulanceId}</div>
            )}
            <div style={{ marginTop: '4px', fontSize: '11px', color: '#666' }}>
              📍 {latitude.toFixed(4)}, {longitude.toFixed(4)}
            </div>
          </div>
        </div>
      </Popup>
    </Marker>
  );
}

EmergencyMarker.propTypes = {
  emergency: PropTypes.shape({
    id: PropTypes.string.isRequired,
    latitude: PropTypes.number.isRequired,
    longitude: PropTypes.number.isRequired,
    priority: PropTypes.oneOf(['HIGH', 'MEDIUM', 'LOW']).isRequired,
    status: PropTypes.string.isRequired,
    assignedAmbulanceId: PropTypes.string,
  }).isRequired,
  onClick: PropTypes.func,
};
