import { useState } from "react";
import { startSimulation, stopSimulation } from "../services/simulation";
import { fetchOSRMRoute } from "../services/osrm";

export default function SimulationPanel({ onSimulationStart, onSimulationStop }) {
    const [ambulanceId, setAmbulanceId] = useState("AMB-001");
    const [startLat, setStartLat] = useState("18.5314");
    const [startLon, setStartLon] = useState("73.8446");
    const [endLat, setEndLat] = useState("18.5204");
    const [endLon, setEndLon] = useState("73.8567");
    const [simulating, setSimulating] = useState(false);
    const [error, setError] = useState(null);
    const [routeInfo, setRouteInfo] = useState(null);
    const [loading, setLoading] = useState(false);

    const handlePreviewRoute = async () => {
        try {
            setError(null);
            setLoading(true);
            const route = await fetchOSRMRoute(
                parseFloat(startLat),
                parseFloat(startLon),
                parseFloat(endLat),
                parseFloat(endLon)
            );
            setRouteInfo({
                distance: (route.distance / 1000).toFixed(2),
                duration: (route.duration / 60).toFixed(0),
            });
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleStart = async () => {
        try {
            setError(null);
            await startSimulation(
                ambulanceId,
                parseFloat(startLat),
                parseFloat(startLon),
                parseFloat(endLat),
                parseFloat(endLon),
                (location) => {
                    if (onSimulationStart) onSimulationStart(location);
                },
                (err) => {
                    setError(err.message);
                    setSimulating(false);
                }
            );
            setSimulating(true);
        } catch (err) {
            setError(err.message);
        }
    };

    const handleStop = () => {
        stopSimulation();
        setSimulating(false);
        if (onSimulationStop) onSimulationStop();
    };

    return (
        <div
            style={{
                position: "absolute",
                bottom: 10,
                left: 10,
                background: "white",
                padding: 15,
                borderRadius: 10,
                boxShadow: "0 4px 20px rgba(0,0,0,0.15)",
                minWidth: 340,
                zIndex: 1000,
                maxHeight: 600,
                overflowY: "auto",
            }}
        >
            <h3 style={{ marginTop: 0, fontSize: 14 }}>🎬 Real-Time Route Simulator</h3>

            <div style={{ marginBottom: 10 }}>
                <label style={{ display: "block", marginBottom: 5, fontSize: 11, fontWeight: "bold" }}>
                    Ambulance ID:
                </label>
                <input
                    type="text"
                    value={ambulanceId}
                    onChange={(e) => setAmbulanceId(e.target.value.toUpperCase())}
                    disabled={simulating}
                    placeholder="e.g., AMB-001"
                    style={{
                        width: "100%",
                        padding: 6,
                        border: "1px solid #ddd",
                        borderRadius: 4,
                        fontSize: 11,
                        boxSizing: "border-box",
                    }}
                />
            </div>

            <div style={{ marginBottom: 10, padding: 10, background: "#f0f9ff", borderRadius: 5 }}>
                <h4 style={{ margin: "0 0 8px 0", fontSize: 11 }}>📍 From (Hospital)</h4>
                <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
                    <input
                        type="number"
                        step="0.0001"
                        value={startLat}
                        onChange={(e) => setStartLat(e.target.value)}
                        disabled={simulating}
                        placeholder="Lat"
                        style={{
                            flex: 1,
                            padding: 5,
                            border: "1px solid #ddd",
                            borderRadius: 4,
                            fontSize: 10,
                        }}
                    />
                    <input
                        type="number"
                        step="0.0001"
                        value={startLon}
                        onChange={(e) => setStartLon(e.target.value)}
                        disabled={simulating}
                        placeholder="Lon"
                        style={{
                            flex: 1,
                            padding: 5,
                            border: "1px solid #ddd",
                            borderRadius: 4,
                            fontSize: 10,
                        }}
                    />
                </div>

                <h4 style={{ margin: "0 0 8px 0", fontSize: 11 }}>📍 To (Emergency)</h4>
                <div style={{ display: "flex", gap: 8 }}>
                    <input
                        type="number"
                        step="0.0001"
                        value={endLat}
                        onChange={(e) => setEndLat(e.target.value)}
                        disabled={simulating}
                        placeholder="Lat"
                        style={{
                            flex: 1,
                            padding: 5,
                            border: "1px solid #ddd",
                            borderRadius: 4,
                            fontSize: 10,
                        }}
                    />
                    <input
                        type="number"
                        step="0.0001"
                        value={endLon}
                        onChange={(e) => setEndLon(e.target.value)}
                        disabled={simulating}
                        placeholder="Lon"
                        style={{
                            flex: 1,
                            padding: 5,
                            border: "1px solid #ddd",
                            borderRadius: 4,
                            fontSize: 10,
                        }}
                    />
                </div>
            </div>

            <button
                onClick={handlePreviewRoute}
                disabled={simulating || loading}
                style={{
                    width: "100%",
                    padding: 8,
                    background: "#8b5cf6",
                    color: "white",
                    border: "none",
                    borderRadius: 4,
                    cursor: "pointer",
                    fontWeight: "bold",
                    fontSize: 11,
                    marginBottom: 10,
                }}
            >
                {loading ? "⏳ Loading Route..." : "🗺️ Preview Route"}
            </button>

            {routeInfo && (
                <div
                    style={{
                        marginBottom: 10,
                        padding: 8,
                        background: "#ecfdf5",
                        borderRadius: 4,
                        fontSize: 10,
                        border: "1px solid #6ee7b7",
                    }}
                >
                    <strong>✅ Route Found:</strong>
                    <br />
                    📏 Distance: {routeInfo.distance} km
                    <br />
                    ⏱️ Duration: {routeInfo.duration} min
                </div>
            )}

            <div style={{ display: "flex", gap: 10 }}>
                {!simulating ? (
                    <button
                        onClick={handleStart}
                        disabled={!routeInfo}
                        style={{
                            flex: 1,
                            padding: 10,
                            background: routeInfo ? "#22c55e" : "#ccc",
                            color: "white",
                            border: "none",
                            borderRadius: 4,
                            cursor: routeInfo ? "pointer" : "not-allowed",
                            fontWeight: "bold",
                            fontSize: 11,
                        }}
                    >
                        ▶ Start Simulation
                    </button>
                ) : (
                    <button
                        onClick={handleStop}
                        style={{
                            flex: 1,
                            padding: 10,
                            background: "#ef4444",
                            color: "white",
                            border: "none",
                            borderRadius: 4,
                            cursor: "pointer",
                            fontWeight: "bold",
                            fontSize: 11,
                        }}
                    >
                        ⏹ Stop
                    </button>
                )}
            </div>

            {simulating && (
                <div
                    style={{
                        marginTop: 10,
                        padding: 8,
                        background: "#dbeafe",
                        borderRadius: 4,
                        fontSize: 10,
                        color: "#1e40af",
                    }}
                >
                    ✅ Simulating {ambulanceId}
                    <br />
                    Following OSRM route | Updates every 2s
                </div>
            )}

            {error && (
                <div
                    style={{
                        marginTop: 10,
                        padding: 8,
                        background: "#fee2e2",
                        borderRadius: 4,
                        fontSize: 10,
                        color: "#991b1b",
                    }}
                >
                    ❌ {error}
                </div>
            )}
        </div>
    );
}
