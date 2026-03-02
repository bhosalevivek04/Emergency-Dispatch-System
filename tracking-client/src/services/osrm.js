// OSRM route service
const OSRM_URL = "https://router.project-osrm.org/route/v1/driving";

export async function fetchOSRMRoute(startLat, startLon, endLat, endLon) {
    try {
        const url = `${OSRM_URL}/${startLon},${startLat};${endLon},${endLat}?overview=full&geometries=geojson`;

        const response = await fetch(url);
        const data = await response.json();

        if (!data.routes || data.routes.length === 0) {
            throw new Error("No route found");
        }

        const route = data.routes[0];
        const coords = route.geometry.coordinates;

        // Convert [lon, lat] to [lat, lon]
        const latLngs = coords.map((c) => [c[1], c[0]]);

        return {
            coordinates: latLngs,
            distance: route.distance, // meters
            duration: route.duration, // seconds
        };
    } catch (error) {
        console.error("OSRM fetch error:", error);
        throw error;
    }
}

// Get interpolated position along route
export function getPositionAlongRoute(coordinates, progress) {
    if (progress < 0 || progress > 1) return null;

    const totalDistance = coordinates.length - 1;
    const targetIndex = progress * totalDistance;
    const index = Math.floor(targetIndex);
    const fraction = targetIndex - index;

    if (index >= coordinates.length - 1) {
        return coordinates[coordinates.length - 1];
    }

    const start = coordinates[index];
    const end = coordinates[index + 1];

    const lat = start[0] + (end[0] - start[0]) * fraction;
    const lon = start[1] + (end[1] - start[1]) * fraction;

    return [lat, lon];
}

// Calculate heading between two points
export function calculateHeading(from, to) {
    const dLon = to[1] - from[1];
    const dLat = to[0] - from[0];
    const heading = (Math.atan2(dLon, dLat) * 180) / Math.PI;
    return (heading + 360) % 360;
}
