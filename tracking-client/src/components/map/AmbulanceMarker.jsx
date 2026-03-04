import { Marker, Popup } from 'react-leaflet';
import { useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import L from 'leaflet';

/**
 * Create a custom ambulance marker icon with status-based coloring
 */
const createAmbulanceIcon = (status) => {
  const statusColors = {
    AVAILABLE: '#10b981',
    ASSIGNED: '#f59e0b',
    ON_ROUTE: '#ef4444',
  };

  const color = statusColors[status] || statusColors.AVAILABLE;

  const svgString = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <!-- Ambulance body -->
      <rect x="15" y="35" width="70" height="40" rx="8" fill="${color}" stroke="#1f2937" stroke-width="2"/>
      <!-- Ambulance cabin -->
      <rect x="15" y="20" width="25" height="20" rx="4" fill="${color}" opacity="0.8"/>
      <!-- Windshield -->
      <rect x="17" y="22" width="12" height="10" fill="#87ceeb" opacity="0.7"/>
      <!-- White cross -->
      <g fill="white">
        <rect x="48" y="43" width="4" height="20" />
        <rect x="38" y="53" width="24" height="4" />
      </g>
      <!-- Front wheel -->
      <circle cx="30" cy="78" r="6" fill="#333"/>
      <circle cx="30" cy="78" r="3" fill="#666"/>
      <!-- Back wheel -->
      <circle cx="70" cy="78" r="6" fill="#333"/>
      <circle cx="70" cy="78" r="3" fill="#666"/>
      <!-- Light -->
      <circle cx="85" cy="40" r="3" fill="#ffff00"/>
      <circle cx="85" cy="50" r="3" fill="#ffff00"/>
    </svg>
  `;

  return L.divIcon({
    html: svgString,
    iconSize: [50, 50],
    iconAnchor: [25, 25],
    popupAnchor: [0, -25],
    className: 'ambulance-marker',
  });
};

/**
 * AmbulanceMarker - Displays an ambulance location on the map
 * Shows a status-colored marker with smooth position animation and popup with ambulance details
 */
export default function AmbulanceMarker({ ambulance, onClick }) {
  const { id, latitude, longitude, status, speed, assignedEmergencyId } = ambulance;
  const position = [latitude, longitude];
  const icon = createAmbulanceIcon(status);
  const markerRef = useRef(null);
  const prevPositionRef = useRef(position);

  // Smooth animation when position changes
  useEffect(() => {
    if (markerRef.current) {
      const marker = markerRef.current;
      const prevPos = prevPositionRef.current;
      const newPos = position;

      // Only animate if position actually changed
      if (prevPos[0] !== newPos[0] || prevPos[1] !== newPos[1]) {
        // Use Leaflet's built-in smooth panning
        const currentLatLng = marker.getLatLng();
        const newLatLng = L.latLng(newPos[0], newPos[1]);
        
        // Animate the marker position
        let start = null;
        const duration = 1000; // 1 second animation

        const animate = (timestamp) => {
          if (!start) start = timestamp;
          const progress = Math.min((timestamp - start) / duration, 1);

          // Linear interpolation
          const lat = currentLatLng.lat + (newLatLng.lat - currentLatLng.lat) * progress;
          const lng = currentLatLng.lng + (newLatLng.lng - currentLatLng.lng) * progress;

          marker.setLatLng([lat, lng]);

          if (progress < 1) {
            requestAnimationFrame(animate);
          }
        };

        requestAnimationFrame(animate);
        prevPositionRef.current = newPos;
      }
    }
  }, [position]);

  const handleClick = () => {
    if (onClick) {
      onClick(ambulance);
    }
  };

  return (
    <Marker 
      position={position} 
      icon={icon}
      ref={markerRef}
      eventHandlers={{
        click: handleClick,
      }}
    >
      <Popup>
        <div style={{ minWidth: '150px' }}>
          <h3 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
            🚑 Ambulance {id}
          </h3>
          <div style={{ fontSize: '12px', lineHeight: '1.6' }}>
            <div>
              <strong>Status:</strong>{' '}
              <span style={{
                padding: '2px 6px',
                borderRadius: '3px',
                backgroundColor: status === 'AVAILABLE' ? '#d1fae5' : status === 'ASSIGNED' ? '#fef3c7' : '#fee2e2',
                color: status === 'AVAILABLE' ? '#10b981' : status === 'ASSIGNED' ? '#f59e0b' : '#ef4444',
                fontWeight: 'bold',
              }}>
                {status}
              </span>
            </div>
            <div><strong>Speed:</strong> {speed ? `${speed.toFixed(1)} km/h` : '0 km/h'}</div>
            {assignedEmergencyId && (
              <div><strong>Emergency:</strong> {assignedEmergencyId}</div>
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

AmbulanceMarker.propTypes = {
  ambulance: PropTypes.shape({
    id: PropTypes.string.isRequired,
    latitude: PropTypes.number.isRequired,
    longitude: PropTypes.number.isRequired,
    status: PropTypes.oneOf(['AVAILABLE', 'ASSIGNED', 'ON_ROUTE']).isRequired,
    speed: PropTypes.number,
    assignedEmergencyId: PropTypes.string,
  }).isRequired,
  onClick: PropTypes.func,
};
