import { useMemo } from 'react';
import './TrackingPanel.css';

// Helper function to calculate direct distance between two points
const calculateDirectDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371000; // Earth's radius in meters
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return 2 * R * Math.asin(Math.sqrt(a));
};

/**
 * TrackingPanel component
 * Displays real-time tracking information between an emergency and assigned ambulance
 * Similar to citizen tracking view but for admin dashboard
 */
const TrackingPanel = ({ emergency, ambulance, routeData }) => {
  // Calculate direct distance between ambulance and emergency
  const directDistanceMeters = useMemo(() => {
    if (!ambulance?.latitude || !ambulance?.longitude || 
        !emergency?.latitude || !emergency?.longitude) {
      return Number.POSITIVE_INFINITY;
    }
    return calculateDirectDistance(
      ambulance.latitude,
      ambulance.longitude,
      emergency.latitude,
      emergency.longitude
    );
  }, [ambulance?.latitude, ambulance?.longitude, emergency?.latitude, emergency?.longitude]);

  const routeDistanceMeters = Number(routeData?.distance ?? 0);
  const rawEtaMinutes = Math.round(Number(routeData?.duration ?? 0) / 60);
  const etaMinutes = directDistanceMeters <= 50 ? 0 : Math.max(1, rawEtaMinutes);
  
  const ambulanceSpeed = Number(ambulance?.speed ?? 0);
  const isMoving = ambulanceSpeed > 0.5;
  const isAtLocation = directDistanceMeters <= 50; // 50 meters threshold
  const isArrivingSoon = isMoving && !isAtLocation && etaMinutes <= 2 && directDistanceMeters <= 500;

  const statusLabel = useMemo(() => {
    if (emergency.status === 'COMPLETED') return 'COMPLETED';
    if (isAtLocation) return 'AT LOCATION';
    if (isMoving) return 'ON ROUTE';
    if (emergency.status === 'ASSIGNED') return 'ASSIGNED';
    return emergency.status;
  }, [emergency.status, isAtLocation, isMoving]);

  if (!emergency || !ambulance) {
    return null;
  }

  return (
    <div className={`tracking-panel ${isArrivingSoon ? 'arriving-soon' : ''}`}>
      <div className="tracking-panel-header">
        <h2>🚨 Active Tracking</h2>
        <button 
          className="tracking-close-btn"
          onClick={() => window.dispatchEvent(new CustomEvent('clearTracking'))}
          title="Stop tracking"
        >
          ✕
        </button>
      </div>

      <div className="tracking-info-grid">
        <div className="tracking-info-section">
          <h3>Emergency</h3>
          <div className="tracking-detail">
            <span className="tracking-label">ID:</span>
            <span className="tracking-value">{emergency.id}</span>
          </div>
          <div className="tracking-detail">
            <span className="tracking-label">Priority:</span>
            <span className={`tracking-badge priority-${emergency.priority?.toLowerCase()}`}>
              {emergency.priority}
            </span>
          </div>
          <div className="tracking-detail">
            <span className="tracking-label">Location:</span>
            <span className="tracking-value tracking-coords">
              {emergency.latitude?.toFixed(4)}, {emergency.longitude?.toFixed(4)}
            </span>
          </div>
        </div>

        <div className="tracking-info-section">
          <h3>Ambulance</h3>
          <div className="tracking-detail">
            <span className="tracking-label">ID:</span>
            <span className="tracking-value">{ambulance.id}</span>
          </div>
          <div className="tracking-detail">
            <span className="tracking-label">Speed:</span>
            <span className="tracking-value">{ambulanceSpeed.toFixed(1)} km/h</span>
          </div>
          <div className="tracking-detail">
            <span className="tracking-label">Location:</span>
            <span className="tracking-value tracking-coords">
              {ambulance.latitude?.toFixed(4)}, {ambulance.longitude?.toFixed(4)}
            </span>
          </div>
        </div>
      </div>

      <div className="tracking-status-section">
        <div className="tracking-status-badge">
          <span className={`status-indicator status-${statusLabel.toLowerCase().replace(' ', '-')}`}>
            {statusLabel}
          </span>
        </div>

        {isArrivingSoon && !isAtLocation && (
          <div className="tracking-alert arriving-alert">
            ⚡ Arriving soon ({etaMinutes} min, {(directDistanceMeters / 1000).toFixed(2)} km away)
          </div>
        )}

        {isAtLocation && emergency.status !== 'COMPLETED' && (
          <div className="tracking-alert arrived-alert">
            ✓ Ambulance has reached location ({directDistanceMeters.toFixed(0)}m away)
          </div>
        )}
      </div>

      {routeData && (
        <div className="tracking-route-section">
          <h3>Route Information</h3>
          <div className="tracking-route-stats">
            <div className="tracking-stat">
              <span className="stat-label">Distance</span>
              <span className="stat-value">{(routeDistanceMeters / 1000).toFixed(2)} km</span>
            </div>
            <div className="tracking-stat">
              <span className="stat-label">ETA</span>
              <span className="stat-value">{etaMinutes} min</span>
            </div>
            <div className="tracking-stat">
              <span className="stat-label">Status</span>
              <span className="stat-value">{isMoving ? '🚑 Moving' : '⏸ Stationary'}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TrackingPanel;
