import { MapContainer, TileLayer, Popup } from "react-leaflet";
import { AnimatedMarker } from "./TrackingMap";
import { useEffect, useState } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

// reuse the same SVG-based ambulance icon generator
const createAmbulanceIcon = () => {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <rect x="15" y="35" width="70" height="40" rx="8" fill="#e74c3c" stroke="#c0392b" stroke-width="2"/>
      <rect x="15" y="20" width="25" height="20" rx="4" fill="#c0392b"/>
      <rect x="17" y="22" width="12" height="10" fill="#87ceeb" opacity="0.7"/>
      <g fill="white">
        <rect x="48" y="43" width="4" height="20" />
        <rect x="38" y="53" width="24" height="4" />
      </g>
      <circle cx="30" cy="78" r="6" fill="#333"/>
      <circle cx="30" cy="78" r="3" fill="#666"/>
      <circle cx="70" cy="78" r="6" fill="#333"/>
      <circle cx="70" cy="78" r="3" fill="#666"/>
      <circle cx="85" cy="40" r="3" fill="#ffff00"/>
      <circle cx="85" cy="50" r="3" fill="#ffff00"/>
    </svg>
  `;
  return L.divIcon({
    html: svg,
    iconSize: [50, 50],
    iconAnchor: [25, 25],
    popupAnchor: [0, -25],
    className: "ambulance-marker"
  });
};

const ambulanceIcon = createAmbulanceIcon();

export default function MultiTrackingMap() {
  const [ambulances, setAmbulances] = useState({});
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS("http://localhost:8085/ws"),
      debug: (str) => {
        console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      console.log("✅ Connected to WebSocket");
      setConnected(true);

      // Subscribe to all ambulance locations
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
            data.longitude < -180 || data.longitude > 180 ||
            isNaN(data.latitude) || isNaN(data.longitude)) {
            console.error("Coordinates out of range or NaN:", data);
            return;
          }

          setAmbulances(prev => ({
            ...prev,
            [data.ambulanceId]: {
              position: [data.latitude, data.longitude],
              timestamp: data.timestamp,
              speed: data.speed || 0,
              heading: data.heading || 0
            }
          }));
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

  const center = [18.5204, 73.8567];

  return (
    <div>
      <div style={{
        padding: "10px",
        background: connected ? "#d4edda" : "#f8d7da",
        color: connected ? "#155724" : "#721c24",
        marginBottom: "10px",
        borderRadius: "5px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }}>
        <span>{connected ? "🟢 Connected" : "🔴 Disconnected"}</span>
        <span>🚑 Active Ambulances: {Object.keys(ambulances).length}</span>
      </div>

      <div style={{
        background: "#f8f9fa",
        padding: "10px",
        marginBottom: "10px",
        borderRadius: "5px",
        maxHeight: "100px",
        overflowY: "auto"
      }}>
        {Object.entries(ambulances).map(([id, data]) => (
          <div key={id} style={{
            padding: "5px",
            margin: "5px 0",
            background: "white",
            borderRadius: "3px",
            fontSize: "12px"
          }}>
            <strong>{id}</strong> |
            Lat: {data.position[0].toFixed(4)} |
            Lng: {data.position[1].toFixed(4)} |
            Speed: {data.speed} km/h
          </div>
        ))}
      </div>

      <MapContainer
        center={center}
        zoom={13}
        style={{ height: "600px", width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {Object.entries(ambulances).map(([id, data]) => (
          <AnimatedMarker
            key={id}
            position={data.position}
            ambulanceId={id}
          />
        ))}
      </MapContainer>
    </div>
  );
}
