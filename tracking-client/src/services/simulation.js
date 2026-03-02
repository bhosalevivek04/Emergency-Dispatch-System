import { fetchOSRMRoute, getPositionAlongRoute, calculateHeading } from "./osrm";

const TRACKING_SERVICE_URL = "http://localhost:8085";

let simulationInterval = null;
let routeCoordinates = [];
let routeProgress = 0;
let ambulanceSpeed = 50; // km/h

export async function startSimulation(ambulanceId, startLat, startLon, endLat, endLon, onLocationUpdate, onError) {
    try {
        if (simulationInterval) {
            stopSimulation();
        }

        // Fetch real OSRM route
        const route = await fetchOSRMRoute(startLat, startLon, endLat, endLon);
        routeCoordinates = route.coordinates;
        routeProgress = 0;

        const speedMultiplier = (ambulanceSpeed * 0.000556) / calculateRouteDistance(routeCoordinates);
        let isFirstUpdate = true;

        simulationInterval = setInterval(() => {
            if (routeProgress >= 1) {
                stopSimulation();
                return;
            }

            routeProgress = Math.min(1, routeProgress + speedMultiplier);
            const position = getPositionAlongRoute(routeCoordinates, routeProgress);

            if (!position) return;

            const nextProgress = Math.min(1, routeProgress + speedMultiplier * 2);
            const nextPosition = getPositionAlongRoute(routeCoordinates, nextProgress) || position;
            const heading = calculateHeading(position, nextPosition);

            sendLocationUpdate(ambulanceId, position, heading, ambulanceSpeed, onError);

            if (onLocationUpdate) {
                const update = {
                    ambulanceId,
                    latitude: position[0],
                    longitude: position[1],
                    heading,
                    speed: ambulanceSpeed,
                    progress: routeProgress,
                    timestamp: Date.now(),
                };

                if (isFirstUpdate) {
                    update.routeCoordinates = routeCoordinates;
                    isFirstUpdate = false;
                }

                onLocationUpdate(update);
            }
        }, 2000);

    } catch (error) {
        console.error("Simulation start error:", error);
        if (onError) onError(error);
    }
}

async function sendLocationUpdate(ambulanceId, position, heading, speed, onError) {
    try {
        const response = await fetch(`${TRACKING_SERVICE_URL}/tracking/location`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ambulanceId,
                latitude: position[0],
                longitude: position[1],
                speed,
                heading: Math.round(heading),
                timestamp: Date.now(),
            }),
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
    } catch (error) {
        console.error("Location update error:", error);
        if (onError) onError(error);
    }
}

function calculateRouteDistance(coordinates) {
    let distance = 0;
    for (let i = 0; i < coordinates.length - 1; i++) {
        const lat1 = coordinates[i][0];
        const lon1 = coordinates[i][1];
        const lat2 = coordinates[i + 1][0];
        const lon2 = coordinates[i + 1][1];

        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        distance += 6371 * c;
    }
    return distance;
}

export function stopSimulation() {
    if (simulationInterval) {
        clearInterval(simulationInterval);
        simulationInterval = null;
    }
    routeCoordinates = [];
    routeProgress = 0;
}

export function isSimulating() {
    return simulationInterval !== null;
}

export function setSimulationSpeed(speed) {
    ambulanceSpeed = speed;
}
