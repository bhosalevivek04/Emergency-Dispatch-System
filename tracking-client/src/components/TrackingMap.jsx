import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import { useEffect, useRef, useState } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

// OSRM endpoint for routing (matches RouteMap component)
// set REACT_APP_OSRM_SERVICE_URL to empty string to disable routing altogether
const OSRM_SERVICE_URL =
  process.env.REACT_APP_OSRM_SERVICE_URL === "" ? null :
    process.env.REACT_APP_OSRM_SERVICE_URL ||
    "https://router.project-osrm.org/route/v1";

// flag that tracks whether remote router appears reachable; start true
let routingAvailable = !!OSRM_SERVICE_URL;

// Create a proper SVG ambulance icon
const createAmbulanceIcon = () => {
  const svgString = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <!-- Ambulance body -->
      <rect x="15" y="35" width="70" height="40" rx="8" fill="#e74c3c" stroke="#c0392b" stroke-width="2"/>
      <!-- Ambulance cabin -->
      <rect x="15" y="20" width="25" height="20" rx="4" fill="#c0392b"/>
      <!-- Windshield -->
      <rect x="17" y="22" width="12" height="10" fill="#87ceeb" opacity="0.7"/>
      <!-- Red cross -->
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
    className: "ambulance-marker"
  });
};

const ambulanceIcon = createAmbulanceIcon();

function AnimatedMarker({ position, ambulanceId, shouldPan = false }) {
  const map = useMap();
  const markerRef = useRef();

  useEffect(() => {
    if (markerRef.current) {
      markerRef.current.setLatLng(position);
      if (shouldPan && map) {
        map.panTo(position, { animate: true, duration: 0.5 });
      }
    }
  }, [position, map, shouldPan]);

  return (
    <Marker
      position={position}
      icon={ambulanceIcon}
      ref={markerRef}
    >
      <Popup>
        🚑 Ambulance {ambulanceId}
        <br />
        Lat: {position[0].toFixed(4)}
        <br />
        Lng: {position[1].toFixed(4)}
      </Popup>
    </Marker>
  );
}

export { AnimatedMarker };
export default function TrackingMap({ ambulanceId = "AMB001" }) {
  const [position, setPosition] = useState([18.5204, 73.8567]);
  const [connected, setConnected] = useState(false);
  const [lastUpdate, setLastUpdate] = useState(null);
  const [paused, setPaused] = useState(false);
  const clientRef = useRef(null);
  const positionRef = useRef([18.5204, 73.8567]);

  // Update ref when position changes
  useEffect(() => {
    positionRef.current = position;
  }, [position]);

  // Smooth movement interpolation using requestAnimationFrame
  // moves the marker over a specified duration (ms)
  const animationRef = useRef();

  // animate along a list of lat-lng points using requestAnimationFrame
  // returns a cancel function
  const animateRoute = (points, duration = 1000) => {
    // cancel any existing animation first
    if (animationRef.current) {
      cancelAnimationFrame(animationRef.current);
    }
    if (!Array.isArray(points) || points.length === 0) return () => { };

    let startTime = null;
    const total = points.length;

    const step = (timestamp) => {
      if (!startTime) startTime = timestamp;
      const elapsed = timestamp - startTime;
      const t = Math.min(elapsed / duration, 1);
      const idx = Math.floor(t * (total - 1));
      setPosition(points[idx]);
      if (t < 1) {
        animationRef.current = requestAnimationFrame(step);
      }
    };

    animationRef.current = requestAnimationFrame(step);

    // return a cancel callback in case caller wants to stop early
    return () => {
      if (animationRef.current) cancelAnimationFrame(animationRef.current);
    };
  };

  // compute a route between current position and end point using OSRM
  const smoothMove = async (end) => {
    const start = positionRef.current;

    // basic validation
    if (!Array.isArray(end) || end.length !== 2 ||
      isNaN(end[0]) || isNaN(end[1]) ||
      !isFinite(end[0]) || !isFinite(end[1])) {
      console.error("Invalid coordinates for move:", end);
      return;
    }

    // if routing is disabled or proven unreachable, skip lookup
    if (!routingAvailable || !OSRM_SERVICE_URL) {
      animateRoute([start, end], 1000);
      return;
    }

    try {
      const url = `${OSRM_SERVICE_URL}/driving/${start[1]},${start[0]};${end[1]},${end[0]}?overview=full&geometries=geojson`;
      const resp = await fetch(url, { timeout: 2000 });
      if (!resp.ok) throw new Error(`status ${resp.status}`);
      const data = await resp.json();
      if (data && data.routes && data.routes[0] &&
        data.routes[0].geometry && data.routes[0].geometry.coordinates) {
        const coords = data.routes[0].geometry.coordinates;
        const latlngs = coords.map(c => [c[1], c[0]]);
        animateRoute(latlngs, 1000);
        return;
      }
      // if data malformed fall through to fallback
    } catch (err) {
      console.warn("OSRM routing failed, disabling future lookups", err);
      routingAvailable = false; // avoid further attempts this session
    }

    // fallback: straight interpolation
    animateRoute([start, end], 1000);
  };

  useEffect(() => {
    // cleanup any unfinished animation on unmount
    return () => {
      if (animationRef.current) cancelAnimationFrame(animationRef.current);
    };
  }, []);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS("http://localhost:8085/ws"),
      debug: (str) => {
        console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onStompError: (frame) => {
        console.error("STOMP error", frame);
        setConnected(false);
      }
    });
    clientRef.current = client;

    client.onConnect = () => {
      console.log("✅ Connected to WebSocket");
      setConnected(true);

      client.subscribe("/topic/location", (message) => {
        try {
          const data = JSON.parse(message.body);
          console.log("📍 Location update:", data);

          // Validate data structure
          if (!data || typeof data.latitude !== 'number' || typeof data.longitude !== 'number') {
            console.error("Invalid location data:", data);
            return;
          }

          // Validate coordinate ranges
          if (data.latitude < -90 || data.latitude > 90 ||
            data.longitude < -180 || data.longitude > 180) {
            console.error("Coordinates out of range:", data);
            return;
          }

          const newPosition = [data.latitude, data.longitude];
          smoothMove(newPosition);
          setLastUpdate(new Date().toLocaleTimeString());
        } catch (error) {
          console.error("Error processing location update:", error);
        }
      });
    };

    client.onStompError = (frame) => {
      console.error("❌ STOMP error:", frame);
      setConnected(false);
    };

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  return (
    <div>
      <div style={{
        padding: "10px",
        background: connected ? "#d4edda" : "#f8d7da",
        color: connected ? "#155724" : "#721c24",
        marginBottom: "10px",
        borderRadius: "5px"
      }}>
        {connected ? (paused ? "⏸️ Paused" : "🟢 Connected") : "🔴 Disconnected"}
        {lastUpdate && ` | Last update: ${lastUpdate}`}
        <button
          style={{ marginLeft: "10px" }}
          onClick={() => {
            if (paused) {
              // check service health before attempting reconnect
              fetch("http://localhost:8085/actuator/health")
                .then(r => r.json())
                .then(json => {
                  if (json.status === "UP") {
                    clientRef.current && clientRef.current.activate();
                    setPaused(false);
                  } else {
                    console.warn("Tracking service not available yet", json);
                    alert("Cannot resume: tracking service is not reachable.");
                  }
                }).catch(err => {
                  console.warn("Failed health check", err);
                  alert("Cannot resume: tracking service is not reachable.");
                });
            } else {
              clientRef.current && clientRef.current.deactivate();
              setPaused(true);
            }
          }}
        >
          {paused ? "Resume" : "Pause"}
        </button>
      </div>

      <MapContainer
        center={position}
        zoom={15}
        style={{ height: "600px", width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <AnimatedMarker position={position} ambulanceId={ambulanceId} shouldPan={true} />
      </MapContainer>
    </div>
  );
}
