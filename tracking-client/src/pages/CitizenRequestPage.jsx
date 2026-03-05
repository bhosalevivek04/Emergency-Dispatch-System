import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CircleMarker, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import MapComponent from '../components/map/MapComponent';
import StatusTimeline from '../components/StatusTimeline';
import ConnectionStatus from '../components/ConnectionStatus';
import { withCorrelationHeader } from '../utils/correlation';
import { fetchOSRMRoute } from '../services/osrm';
import './CitizenRequestPage.css';

const apiBase = 'http://localhost:8080';
const CITIZEN_STATE_KEY = 'citizen_request_state_v1';

const toRad = (deg) => (deg * Math.PI) / 180;
const distanceMeters = (a, b) => {
  const R = 6371000;
  const dLat = toRad(b[0] - a[0]);
  const dLng = toRad(b[1] - a[1]);
  const lat1 = toRad(a[0]);
  const lat2 = toRad(b[0]);
  const h =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.sin(dLng / 2) * Math.sin(dLng / 2) * Math.cos(lat1) * Math.cos(lat2);
  return 2 * R * Math.asin(Math.sqrt(h));
};

const nearestIndex = (points, target) => {
  if (!points?.length) return -1;
  let bestIdx = 0;
  let bestDist = Number.POSITIVE_INFINITY;
  points.forEach((pt, idx) => {
    const d = distanceMeters(pt, target);
    if (d < bestDist) {
      bestDist = d;
      bestIdx = idx;
    }
  });
  return bestIdx;
};

function RouteFitController({ coordinates, enabled, onDone }) {
  const map = useMap();

  useEffect(() => {
    if (!enabled || !coordinates || coordinates.length < 2) {
      return;
    }
    map.fitBounds(coordinates, {
      padding: [42, 42],
      maxZoom: 15,
      animate: true,
      duration: 0.6,
    });
    onDone?.();
  }, [map, coordinates, enabled, onDone]);

  return null;
}

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
  const [trackingConnectionState, setTrackingConnectionState] = useState('disconnected');
  const [lastStatusUpdatedAt, setLastStatusUpdatedAt] = useState(null);
  const [selectedAddress, setSelectedAddress] = useState('');
  const [isResolvingAddress, setIsResolvingAddress] = useState(false);
  const [routeData, setRouteData] = useState(null);
  const [isRouteLoading, setIsRouteLoading] = useState(false);
  const [shouldAutoFitRoute, setShouldAutoFitRoute] = useState(true);
  const [error, setError] = useState('');
  const hasRestoredRef = useRef(false);

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(CITIZEN_STATE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw);
      if (saved.priority) setPriority(saved.priority);
      if (typeof saved.callerPhone === 'string') setCallerPhone(saved.callerPhone);
      if (typeof saved.description === 'string') setDescription(saved.description);
      if (saved.selectedLocation?.lat != null && saved.selectedLocation?.lng != null) {
        setSelectedLocation(saved.selectedLocation);
      }
      if (saved.requestId) setRequestId(saved.requestId);
      if (saved.trackingId) setTrackingId(saved.trackingId);
      if (saved.statusData) setStatusData(saved.statusData);
      if (saved.ambulanceLocation) setAmbulanceLocation(saved.ambulanceLocation);
      if (saved.selectedAddress) setSelectedAddress(saved.selectedAddress);
      if (saved.lastStatusUpdatedAt) setLastStatusUpdatedAt(saved.lastStatusUpdatedAt);
      if (saved.requestId || saved.statusData?.emergencyId) {
        setIsPostSubmitView(true);
      }
    } catch (_err) {
      // Ignore corrupted local session state
    }
  }, []);

  useEffect(() => {
    const snapshot = {
      priority,
      callerPhone,
      description,
      selectedLocation,
      requestId,
      trackingId,
      statusData,
      ambulanceLocation,
      selectedAddress,
      lastStatusUpdatedAt,
    };
    try {
      sessionStorage.setItem(CITIZEN_STATE_KEY, JSON.stringify(snapshot));
    } catch (_err) {
      // Best effort only
    }
  }, [
    priority,
    callerPhone,
    description,
    selectedLocation,
    requestId,
    trackingId,
    statusData,
    ambulanceLocation,
    selectedAddress,
    lastStatusUpdatedAt,
  ]);

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
    setSelectedAddress('');
    setRouteData(null);
    setShouldAutoFitRoute(true);
    setError('');
    sessionStorage.removeItem(CITIZEN_STATE_KEY);
  }, []);

  const updateSelectedLocation = useCallback((lat, lng) => {
    setSelectedLocation((prev) => {
      if (!prev) {
        return { lat, lng };
      }
      const unchanged = Math.abs(prev.lat - lat) < 0.000001 && Math.abs(prev.lng - lng) < 0.000001;
      return unchanged ? prev : { lat, lng };
    });
  }, []);

  const handleMapClick = useCallback((lat, lng) => {
    updateSelectedLocation(lat, lng);
    setError('');
  }, [updateSelectedLocation]);

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported by your browser.');
      return;
    }
    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        updateSelectedLocation(position.coords.latitude, position.coords.longitude);
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
    if (isSubmitting) {
      return;
    }
    if (!selectedLocation) {
      setError('Please select location on map.');
      return;
    }
    setIsSubmitting(true);
    setError('');
    try {
      const response = await fetch(`${apiBase}/api/emergencies/public`, {
        method: 'POST',
        headers: withCorrelationHeader({ 'Content-Type': 'application/json' }),
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
      setShouldAutoFitRoute(true);
      setTrackingConnectionState('connected');
      setLastStatusUpdatedAt(new Date().toISOString());
      if (data.latitude != null && data.longitude != null) {
        updateSelectedLocation(data.latitude, data.longitude);
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
    setTrackingConnectionState((prev) => (prev === 'connected' ? prev : 'connecting'));
    try {
      const response = await fetch(`${apiBase}/api/emergencies/public/${id}`, {
        headers: withCorrelationHeader(),
      });
      if (!response.ok) {
        throw new Error('Request ID not found');
      }
      const data = await response.json();
      setStatusData(data);
      setTrackingConnectionState('connected');
      setLastStatusUpdatedAt(new Date().toISOString());
      if (data.latitude != null && data.longitude != null) {
        updateSelectedLocation(data.latitude, data.longitude);
      }
      setError('');
    } catch (err) {
      setTrackingConnectionState('error');
      setError(err.message || 'Unable to fetch status');
    } finally {
      setIsChecking(false);
    }
  }, [updateSelectedLocation]);

  useEffect(() => {
    if (!selectedLocation) {
      setSelectedAddress('');
      return;
    }

    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        setIsResolvingAddress(true);
        const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${selectedLocation.lat}&lon=${selectedLocation.lng}`;
        const response = await fetch(url, {
          signal: controller.signal,
          headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
          throw new Error('Address lookup failed');
        }
        const data = await response.json();
        const address = data?.display_name || '';
        setSelectedAddress(address);
      } catch (_err) {
        setSelectedAddress('');
      } finally {
        setIsResolvingAddress(false);
      }
    }, 350);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [selectedLocation]);

  useEffect(() => {
    if (!statusData?.emergencyId) return;
    if (statusData.status === 'COMPLETED') return;
    const interval = setInterval(() => {
      fetchStatus(statusData.emergencyId);
    }, 5000);
    return () => clearInterval(interval);
  }, [statusData, fetchStatus]);

  useEffect(() => {
    if (hasRestoredRef.current) return;
    const idToResume = requestId || statusData?.emergencyId;
    if (!idToResume) return;
    hasRestoredRef.current = true;
    fetchStatus(idToResume);
  }, [requestId, statusData?.emergencyId, fetchStatus]);

  useEffect(() => {
    if (!lastStatusUpdatedAt) return;
    const staleCheckTimer = setInterval(() => {
      const ageMs = Date.now() - new Date(lastStatusUpdatedAt).getTime();
      if (ageMs > 15000) {
        setTrackingConnectionState('error');
      }
    }, 5000);
    return () => clearInterval(staleCheckTimer);
  }, [lastStatusUpdatedAt]);

  const fetchAmbulanceLocation = useCallback(async (ambulanceId) => {
    if (!ambulanceId) return;
    try {
      const response = await fetch(`${apiBase}/api/tracking/public/ambulances/${ambulanceId}`, {
        headers: withCorrelationHeader(),
      });
      if (!response.ok) return;
      const data = await response.json();
      if (data?.latitude != null && data?.longitude != null) {
        setAmbulanceLocation({
          lat: data.latitude,
          lng: data.longitude,
          id: ambulanceId,
          speed: Number(data.speed ?? 0),
        });
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

  useEffect(() => {
    const hasAmbulance = ambulanceLocation?.lat != null && ambulanceLocation?.lng != null;
    const hasEmergency = selectedLocation?.lat != null && selectedLocation?.lng != null;
    if (!hasAmbulance || !hasEmergency || statusData?.status === 'COMPLETED') {
      setRouteData(null);
      return;
    }

    let cancelled = false;
    const loadRoute = async () => {
      try {
        setIsRouteLoading(true);
        const route = await fetchOSRMRoute(
          ambulanceLocation.lat,
          ambulanceLocation.lng,
          selectedLocation.lat,
          selectedLocation.lng
        );
        if (!cancelled) {
          const ambulancePoint = [ambulanceLocation.lat, ambulanceLocation.lng];
          const emergencyPoint = [selectedLocation.lat, selectedLocation.lng];
          const rawCoordinates = Array.isArray(route?.coordinates) ? route.coordinates : [];

          let normalized = rawCoordinates;
          const startIdx = nearestIndex(rawCoordinates, ambulancePoint);
          const endIdx = nearestIndex(rawCoordinates, emergencyPoint);
          if (startIdx >= 0 && endIdx >= 0) {
            const from = Math.min(startIdx, endIdx);
            const to = Math.max(startIdx, endIdx);
            normalized = rawCoordinates.slice(from, to + 1);
          }

          const finalCoordinates = [ambulancePoint, ...normalized, emergencyPoint];
          setRouteData({
            ...route,
            coordinates: finalCoordinates,
          });
        }
      } catch (_err) {
        if (!cancelled) {
          setRouteData(null);
        }
      } finally {
        if (!cancelled) {
          setIsRouteLoading(false);
        }
      }
    };

    loadRoute();
    const interval = setInterval(loadRoute, 10000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [ambulanceLocation, selectedLocation, statusData?.status]);

  const isMovingToEmergency =
    statusData?.status === 'ASSIGNED' && Number(ambulanceLocation?.speed ?? 0) > 0.5;
  const statusLabel = isMovingToEmergency ? 'ON_ROUTE' : (statusData?.status || 'UNKNOWN');
  const mapCenter = useMemo(
    () => (selectedLocation ? [selectedLocation.lat, selectedLocation.lng] : [18.5204, 73.8567]),
    [selectedLocation]
  );
  const mapZoom = selectedLocation ? 16 : 13;

  return (
    <div className="citizen-page">
      <header className="citizen-header">
        <h1>Request Emergency Help</h1>
        <p>Choose your location on map and submit request.</p>
        {statusData?.emergencyId && (
          <div className="citizen-connection-wrap">
            <ConnectionStatus status={trackingConnectionState} />
          </div>
        )}
      </header>

      <main className="citizen-content">
        <section className="citizen-map-section">
          <div className="map-hint">Tap map to mark emergency location</div>
          <MapComponent
            center={mapCenter}
            zoom={mapZoom}
            onMapClick={handleMapClick}
            panToCenterOnChange
          >
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
            {routeData?.coordinates?.length > 1 && (
              <>
                <Polyline
                  positions={routeData.coordinates}
                  pathOptions={{
                    color: '#1e3a8a',
                    weight: 8,
                    opacity: 0.32,
                    lineCap: 'round',
                    lineJoin: 'round',
                  }}
                />
                <Polyline
                  positions={routeData.coordinates}
                  pathOptions={{
                    color: '#2563eb',
                    weight: 5,
                    opacity: 0.95,
                    lineCap: 'round',
                    lineJoin: 'round',
                  }}
                />
                <RouteFitController
                  coordinates={routeData.coordinates}
                  enabled={shouldAutoFitRoute}
                  onDone={() => setShouldAutoFitRoute(false)}
                />
              </>
            )}
          </MapComponent>
        </section>

        <section className="citizen-form-section">
          {selectedLocation && (
            <div className="selected-location-chip">
              Selected: {selectedLocation.lat.toFixed(5)}, {selectedLocation.lng.toFixed(5)}
            </div>
          )}
          {selectedLocation && (
            <div className="selected-address-chip">
              {isResolvingAddress
                ? 'Resolving address...'
                : selectedAddress || 'Address unavailable, please verify marker on map'}
            </div>
          )}
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

              <label htmlFor="citizen-priority">Priority</label>
              <select id="citizen-priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>

              <label htmlFor="citizen-phone">Phone (optional)</label>
              <input
                id="citizen-phone"
                value={callerPhone}
                onChange={(e) => setCallerPhone(e.target.value)}
                placeholder="+91..."
                autoComplete="tel"
              />

              <label htmlFor="citizen-description">Description (optional)</label>
              <textarea
                id="citizen-description"
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
              <StatusTimeline status={statusLabel} />
              {statusData?.assignedAmbulanceId && (
                <div><strong>Ambulance:</strong> {statusData.assignedAmbulanceId}</div>
              )}
              {ambulanceLocation && (
                <div>
                  <strong>Ambulance Location:</strong> {ambulanceLocation.lat.toFixed(4)}, {ambulanceLocation.lng.toFixed(4)}
                </div>
              )}
              {ambulanceLocation && (
                <div>
                  <strong>Ambulance Speed:</strong> {Number(ambulanceLocation.speed ?? 0).toFixed(1)} km/h
                </div>
              )}
              {(isRouteLoading || routeData) && (
                <div className="route-info-box">
                  <div><strong>Route:</strong> {isRouteLoading && !routeData ? 'Calculating...' : 'Live path shown on map'}</div>
                  {routeData && (
                    <>
                      <div><strong>Distance:</strong> {(Number(routeData.distance ?? 0) / 1000).toFixed(2)} km</div>
                      <div><strong>Estimated Arrival:</strong> {Math.max(1, Math.round(Number(routeData.duration ?? 0) / 60))} min</div>
                    </>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="citizen-track-box">
            <label htmlFor="citizen-track-id">Track Existing Request ID</label>
            <div className="track-row">
              <input
                id="citizen-track-id"
                value={trackingId}
                onChange={(e) => setTrackingId(e.target.value)}
                placeholder="EMG-..."
              />
              <button
                type="button"
                className="track-btn"
                onClick={() => fetchStatus(trackingId)}
                disabled={isChecking}
              >
                {isChecking ? 'Checking...' : 'Track'}
              </button>
            </div>
          </div>

          {error && <div className="citizen-error" role="alert">{error}</div>}
        </section>
      </main>
    </div>
  );
};

export default CitizenRequestPage;
