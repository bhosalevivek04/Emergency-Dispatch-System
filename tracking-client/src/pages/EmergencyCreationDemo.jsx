import { useState } from 'react';
import { MapContainer, TileLayer, useMapEvents } from 'react-leaflet';
import EmergencyCreationControl from '../components/EmergencyCreationControl';
import 'leaflet/dist/leaflet.css';

/**
 * EmergencyCreationDemo component
 * Demonstrates the EmergencyCreationControl component with a simple map
 */
const EmergencyCreationDemo = () => {
  const [emergencies, setEmergencies] = useState([]);
  const [toasts, setToasts] = useState([]);

  // Simple toast implementation for demo
  const showToast = (message, type = 'info') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
    
    // Auto-dismiss after 5 seconds
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, 5000);
  };

  const handleEmergencyCreated = (emergency) => {
    setEmergencies((prev) => [...prev, emergency]);
    console.log('Emergency created:', emergency);
  };

  const handleModeChange = (isActive) => {
    console.log('Creation mode:', isActive ? 'active' : 'inactive');
  };

  // Map click handler component
  const MapClickHandler = () => {
    useMapEvents({
      click: (e) => {
        // Access the global emergency creation control
        if (window.emergencyCreationControl?.isCreationMode) {
          window.emergencyCreationControl.handleLocationSelect(
            e.latlng.lat,
            e.latlng.lng
          );
        }
      },
    });
    return null;
  };

  return (
    <div style={{ height: '100vh', width: '100vw', position: 'relative' }}>
      {/* Simple toast display */}
      <div style={{
        position: 'fixed',
        top: '1rem',
        right: '1rem',
        zIndex: 2000,
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
      }}>
        {toasts.map((toast) => (
          <div
            key={toast.id}
            style={{
              padding: '1rem',
              borderRadius: '8px',
              backgroundColor: toast.type === 'success' ? '#10b981' : '#ef4444',
              color: 'white',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.2)',
              minWidth: '300px',
            }}
          >
            {toast.message}
          </div>
        ))}
      </div>

      {/* Map */}
      <MapContainer
        center={[40.7128, -74.0060]} // New York City
        zoom={13}
        style={{ height: '100%', width: '100%' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapClickHandler />
      </MapContainer>

      {/* Emergency Creation Control */}
      <EmergencyCreationControl
        onEmergencyCreated={handleEmergencyCreated}
        onModeChange={handleModeChange}
        showToast={showToast}
      />

      {/* Debug info */}
      <div style={{
        position: 'fixed',
        bottom: '1rem',
        left: '1rem',
        background: 'white',
        padding: '1rem',
        borderRadius: '8px',
        boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
        maxWidth: '300px',
        zIndex: 1000,
      }}>
        <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem' }}>
          Emergencies Created: {emergencies.length}
        </h3>
        {emergencies.length > 0 && (
          <ul style={{ margin: 0, padding: '0 0 0 1.5rem', fontSize: '0.875rem' }}>
            {emergencies.map((emergency) => (
              <li key={emergency.id}>
                {emergency.id} - {emergency.priority}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default EmergencyCreationDemo;
