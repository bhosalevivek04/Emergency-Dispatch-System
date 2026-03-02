import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet-routing-machine";
import "leaflet/dist/leaflet.css";
import "leaflet-routing-machine/dist/leaflet-routing-machine.css";

const ambulanceIcon = new L.Icon({
  iconUrl: "https://cdn-icons-png.flaticon.com/512/2967/2967350.png",
  iconSize: [40, 40],
});

const hospitalIcon = new L.Icon({
  iconUrl: "https://cdn-icons-png.flaticon.com/512/3063/3063155.png",
  iconSize: [40, 40],
});

function RoutingControl({ start, end }) {
  const map = useMap();
  const routingControlRef = useRef(null);

  // OSRM service URL can be overridden via env var for production
  // Default is the public demo server which will emit a warning:
  // "You are using OSRM's demo server. Please note that it is NOT SUITABLE FOR PRODUCTION USE"
  // Replace it with your own OSRM instance or a paid provider before deploying.
  const OSRM_SERVICE_URL =
    process.env.REACT_APP_OSRM_SERVICE_URL ||
    "https://router.project-osrm.org/route/v1";

  useEffect(() => {
    if (!map) {
      console.warn("Map not ready for routing control");
      return;
    }

    // Wait for map to be ready
    const initRouting = () => {
      try {
        // Double-check map exists before proceeding
        if (!map || !map._layers) {
          console.warn("Map is not fully initialized");
          return;
        }

        // Remove existing routing control safely
        if (routingControlRef.current) {
          try {
            if (map && typeof map.removeControl === 'function') {
              map.removeControl(routingControlRef.current);
            }
          } catch (error) {
            console.warn("Error removing routing control:", error);
          }
          routingControlRef.current = null;
        }

        // Create new routing control with explicit router options
        if (map && typeof L.Routing.control === 'function') {
          routingControlRef.current = L.Routing.control({
            router: L.Routing.osrmv1({
              serviceUrl: OSRM_SERVICE_URL,
              profile: "car",
            }),
            waypoints: [
              L.latLng(start[0], start[1]),
              L.latLng(end[0], end[1])
            ],
            routeWhileDragging: false,
            addWaypoints: false,
            draggableWaypoints: false,
            lineOptions: {
              styles: [{ color: '#ff0000', opacity: 0.8, weight: 5 }]
            },
            createMarker: () => null, // Don't create default markers
            show: false, // Hide the instruction panel
          }).addTo(map);
        }
      } catch (error) {
        console.error("Error initializing routing:", error);
      }
    };

    // Initialize after a short delay to ensure map is ready
    const timer = setTimeout(initRouting, 100);

    return () => {
      clearTimeout(timer);
      if (routingControlRef.current && map && typeof map.removeControl === 'function') {
        try {
          map.removeControl(routingControlRef.current);
        } catch (error) {
          console.warn("Error in cleanup - already removed or invalid:", error);
        }
        routingControlRef.current = null;
      }
    };
  }, [map, start, end, OSRM_SERVICE_URL]);

  return null;
}

export default function RouteMap({
  startPoint = [18.5204, 73.8567],
  endPoint = [18.5314, 73.8446]
}) {
  // Validate coordinates
  const validateCoords = (coords) => {
    return Array.isArray(coords) &&
      coords.length === 2 &&
      !isNaN(coords[0]) && !isNaN(coords[1]) &&
      isFinite(coords[0]) && isFinite(coords[1]) &&
      coords[0] >= -90 && coords[0] <= 90 &&
      coords[1] >= -180 && coords[1] <= 180;
  };

  const validStart = validateCoords(startPoint) ? startPoint : [18.5204, 73.8567];
  const validEnd = validateCoords(endPoint) ? endPoint : [18.5314, 73.8446];
  const [center] = useState(validStart);

  return (
    <div>
      <div style={{
        padding: "10px",
        background: "#e7f3ff",
        marginBottom: "10px",
        borderRadius: "5px"
      }}>
        🗺️ Route Planning | Start: {validStart[0].toFixed(4)}, {validStart[1].toFixed(4)}
        → End: {validEnd[0].toFixed(4)}, {validEnd[1].toFixed(4)}
      </div>

      <MapContainer
        center={center}
        zoom={14}
        style={{ height: "600px", width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <RoutingControl start={validStart} end={validEnd} />

        <Marker position={validStart} icon={ambulanceIcon}>
          <Popup>🚑 Ambulance Start</Popup>
        </Marker>

        <Marker position={validEnd} icon={hospitalIcon}>
          <Popup>🏥 Hospital Destination</Popup>
        </Marker>
      </MapContainer>
    </div>
  );
}
