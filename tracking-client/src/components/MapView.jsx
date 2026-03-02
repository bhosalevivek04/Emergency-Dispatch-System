import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet-rotatedmarker";
import { fetchOSRMRoute } from "../services/osrm";

const ambulanceIcon = L.icon({
    iconUrl: "https://cdn-icons-png.flaticon.com/512/3448/3448339.png",
    iconSize: [48, 48],
    iconAnchor: [24, 24],
});

export default function MapView({ ambulances }) {
    const mapRef = useRef(null);
    const markersRef = useRef({});
    const routeLinesRef = useRef({});
    const mapInitialized = useRef(false);

    // Initialize map once
    useEffect(() => {
        if (mapInitialized.current) return;

        const mapInstance = L.map("map").setView([18.5259, 73.8507], 13);

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: "© OpenStreetMap contributors",
        }).addTo(mapInstance);

        mapRef.current = mapInstance;
        mapInitialized.current = true;

        return () => {
            if (mapRef.current) {
                mapRef.current.remove();
                mapRef.current = null;
                mapInitialized.current = false;
            }
        };
    }, []);

    // Update ambulance positions and draw routes
    useEffect(() => {
        if (!mapRef.current) return;

        Object.values(ambulances).forEach(async (location) => {
            const { ambulanceId, latitude, longitude, heading } = location;
            const latLng = [latitude, longitude];

            // Draw route if first location and there's route data
            if (!routeLinesRef.current[ambulanceId] && location.routeCoordinates) {
                const routePolyline = L.polyline(location.routeCoordinates, {
                    color: "#3b82f6",
                    weight: 5,
                    opacity: 0.7,
                    dashArray: "5, 5",
                }).addTo(mapRef.current);

                routeLinesRef.current[ambulanceId] = {
                    full: routePolyline,
                    traveled: L.polyline([], {
                        color: "#10b981",
                        weight: 5,
                    }).addTo(mapRef.current),
                };

                // Fit map to route
                mapRef.current.fitBounds(routePolyline.getBounds());
            }

            // Update traveled path if route exists
            if (routeLinesRef.current[ambulanceId] && location.routeCoordinates && location.progress !== undefined) {
                const traveledLength = Math.floor(location.routeCoordinates.length * location.progress);
                const traveledPath = location.routeCoordinates.slice(0, traveledLength);
                if (traveledPath.length > 0) {
                    routeLinesRef.current[ambulanceId].traveled.setLatLngs(traveledPath);
                }
            }

            // Update marker
            if (markersRef.current[ambulanceId]) {
                markersRef.current[ambulanceId].setLatLng(latLng);
                markersRef.current[ambulanceId].setRotationAngle(heading || 0);
            } else {
                const marker = L.marker(latLng, {
                    icon: ambulanceIcon,
                    rotationAngle: heading || 0,
                }).addTo(mapRef.current);

                marker.bindPopup(`<b>${ambulanceId}</b><br>Speed: ${location.speed?.toFixed(1)} km/h`);
                markersRef.current[ambulanceId] = marker;
            }
        });
    }, [ambulances]);

    return <div id="map" style={{ height: "100vh" }} />;
}
