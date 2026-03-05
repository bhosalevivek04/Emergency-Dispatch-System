import { useCallback, useEffect, useMemo, useState } from 'react';
import { CircleMarker, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import MapComponent from '../components/map/MapComponent';
import './CitizenRequestPage.css';

const apiBase = 'http://localhost:8080';

const CitizenRequestPage = () => {
  const [priority, setPriority] = useState('HIGH');
  const [callerPhone, setCallerPhone] = useState('');
  const [description, setDescription] = useState('');
  const [selectedLocation, setSelectedLocation] = useState(null);
  const [requestId, setRequestId] = useState('');
  const [trackingId, setTrackingId] = useState('');
  const [statusData, setStatusData] = useState(null);
  const [ambulanceLocation, setAmbulanceLocation] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [isLocating, setIsLocating] = useState(false);
  const [isPostSubmitView, setIsPostSubmitView] = useState(false);
  const [error, setError] = useState('');

  const ambulanceIcon = useMemo(() => {
    const svgString = `
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
        <rect x="15" y="35" width="70" height="40" rx="8" fill="#2563eb" stroke="#1f2937" stroke-width="2"/>
        <rect x="15" y="20" width="25" height="20" rx="4" fill="#2563eb" opacity="0.85"/>
        <rect x="17" y="22" width="12" height="10" fill="#87ceeb" opacity="0.7"/>
        <g fill="white">
          <rect x="48" y="43" width="4" height="20" />
          <rect x="38" y="53" width="24" height="4" />
        </g>
        <circle cx="30" cy="78" r="6" fill="#333"/>
        <circle cx="30" cy="78" r="3" fill="#666"/>
        <circle cx="70" cy="78" r="6" fill="#333"/>
        <circle cx="70" cy="78" r="3" fill="#666"/>
        <circle cx="85" cy="40" r="3" fill="#fde047"/>
        <circle cx="85" cy="50" r="3" fill="#fde047"/>
      </svg>
    `;
    return L.divIcon({
      html: svgString,
      iconSize: [44, 44],
      iconAnchor: [22, 22],
      className: 'citizen-ambulance-marker',
    });
  }, []);

  const resetForNewRequest = useCallback(() => {
    setPriority('HIGH');
    setCallerPhone('');
    setDescription('');
    setSelectedLocation(null);
    setRequestId('');
    setStatusData(null);
    setAmbulanceLocation(null);
    setTrackingId('');
    setIsPostSubmitView(false);
    setError('');
  }, []);

  const handleMapClick = useCallback((lat, lng) => {
    setSelectedLocation({ lat, lng });
    setError('');
  }, []);

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported by your browser.');
      return;
    }
    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setSelectedLocation({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        });
        setError('');
        setIsLocating(false);
      },
      () => {
        setError('Unable to fetch your location. Please allow location permission.');
        setIsLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const submitEmergency = async (e) => {
    e.preventDefault();
    if (!selectedLocation) {
      setError('Please select location on map.');
      return;
    }
    setIsSubmitting(true);
    setError('');
    try {
      const response = await fetch(`${apiBase}/api/emergencies/public`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          latitude: selectedLocation.lat,
          longitude: selectedLocation.lng,
          priority,
          callerPhone: callerPhone || null,
          description: description || null,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body?.error || body?.message || 'Failed to submit emergency');
      }
      const data = await response.json();
      const emergencyId = data.emergencyId || '';
      setRequestId(emergencyId);
      setTrackingId(emergencyId);
      setStatusData(data);
      setIsPostSubmitView(true);
      if (data.latitude != null && data.longitude != null) {
        setSelectedLocation({ lat: data.latitude, lng: data.longitude });
      }
    } catch (err) {
      setError(err.message || 'Failed to submit emergency');
    } finally {
      setIsSubmitting(false);
    }
  };

  const fetchStatus = useCallback(async (id) => {
    if (!id) return;
    setIsChecking(true);
    try {
      const response = await fetch(`${apiBase}/api/emergencies/public/${id}`);
      if (!response.ok) {
        throw new Error('Request ID not found');
      }
      const data = await response.json();
      setStatusData(data);
      if (data.latitude != null && data.longitude != null) {
        setSelectedLocation({ lat: data.latitude, lng: data.longitude });
      }
      setError('');
    } catch (err) {
      setError(err.message || 'Unable to fetch status');
    } finally {
      setIsChecking(false);
    }
  }, []);

  useEffect(() => {
    if (!statusData?.emergencyId) return;
    if (statusData.status === 'COMPLETED') return;
    const interval = setInterval(() => {
      fetchStatus(statusData.emergencyId);
    }, 5000);
    return () => clearInterval(interval);
  }, [statusData, fetchStatus]);

  const fetchAmbulanceLocation = useCallback(async (ambulanceId) => {
    if (!ambulanceId) return;
    try {
      const response = await fetch(`${apiBase}/api/tracking/public/ambulances/${ambulanceId}`);
      if (!response.ok) return;
      const data = await response.json();
      if (data?.latitude != null && data?.longitude != null) {
        setAmbulanceLocation({ lat: data.latitude, lng: data.longitude, id: ambulanceId });
      }
    } catch (_err) {
      // Silent fail for transient tracking misses
    }
  }, []);

  useEffect(() => {
    const assignedAmbulanceId = statusData?.assignedAmbulanceId;
    if (!assignedAmbulanceId || statusData?.status === 'COMPLETED') {
      setAmbulanceLocation(null);
      return;
    }
    fetchAmbulanceLocation(assignedAmbulanceId);
    const interval = setInterval(() => {
      fetchAmbulanceLocation(assignedAmbulanceId);
    }, 5000);
    return () => clearInterval(interval);
  }, [statusData, fetchAmbulanceLocation]);

  const statusLabel = statusData?.status || 'UNKNOWN';

  return (
    <div className="citizen-page">
      <header className="citizen-header">
        <h1>Request Emergency Help</h1>
        <p>Choose your location on map and submit request.</p>
      </header>

      <main className="citizen-content">
        <section className="citizen-map-section">
          <MapComponent center={[18.5204, 73.8567]} zoom={13} onMapClick={handleMapClick}>
            {selectedLocation && (
              <CircleMarker
                center={[selectedLocation.lat, selectedLocation.lng]}
                radius={10}
                pathOptions={{ color: '#dc2626', fillColor: '#ef4444', fillOpacity: 0.5 }}
              />
            )}
            {ambulanceLocation && (
              <Marker
                position={[ambulanceLocation.lat, ambulanceLocation.lng]}
                icon={ambulanceIcon}
              >
                <Popup>
                  <div>
                    <strong>Assigned Ambulance:</strong> {ambulanceLocation.id}
                    <div>{ambulanceLocation.lat.toFixed(4)}, {ambulanceLocation.lng.toFixed(4)}</div>
                  </div>
                </Popup>
              </Marker>
            )}
          </MapComponent>
        </section>

        <section className="citizen-form-section">
          {isPostSubmitView ? (
            <div className="citizen-success-box">
              <h3>Request Submitted</h3>
              <p>Your request is active. We are tracking updates in real time.</p>
              <button type="button" className="new-request-btn" onClick={resetForNewRequest}>
                Create New Request
              </button>
            </div>
          ) : (
            <form onSubmit={submitEmergency} className="citizen-form">
              <button
                type="button"
                className="locate-btn"
                onClick={useCurrentLocation}
                disabled={isLocating}
              >
                {isLocating ? 'Locating...' : 'Use My Location'}
              </button>

              <label>Priority</label>
              <select value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>

              <label>Phone (optional)</label>
              <input value={callerPhone} onChange={(e) => setCallerPhone(e.target.value)} placeholder="+91..." />

              <label>Description (optional)</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                placeholder="Briefly describe emergency"
              />

              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Submitting...' : 'Request Help'}
              </button>
            </form>
          )}

          {requestId && (
            <div className="citizen-status-box">
              <strong>Request ID:</strong> {requestId}
              <div><strong>Status:</strong> {statusLabel}</div>
              {statusData?.assignedAmbulanceId && (
                <div><strong>Ambulance:</strong> {statusData.assignedAmbulanceId}</div>
              )}
              {ambulanceLocation && (
                <div>
                  <strong>Ambulance Location:</strong> {ambulanceLocation.lat.toFixed(4)}, {ambulanceLocation.lng.toFixed(4)}
                </div>
              )}
            </div>
          )}

          <div className="citizen-track-box">
            <label>Track Existing Request ID</label>
            <div className="track-row">
              <input
                value={trackingId}
                onChange={(e) => setTrackingId(e.target.value)}
                placeholder="EMG-..."
              />
              <button type="button" onClick={() => fetchStatus(trackingId)} disabled={isChecking}>
                {isChecking ? 'Checking...' : 'Track'}
              </button>
            </div>
          </div>

          {error && <div className="citizen-error">{error}</div>}
        </section>
      </main>
    </div>
  );
};

export default CitizenRequestPage;
