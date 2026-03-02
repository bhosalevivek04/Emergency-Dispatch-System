export default function InfoPanel({ connected, ambulances }) {
    const ambulanceArray = Object.values(ambulances).filter(a => a && a.ambulanceId);

    return (
        <div
            style={{
                position: "absolute",
                top: 10,
                right: 10,
                background: "white",
                padding: 15,
                borderRadius: 10,
                boxShadow: "0 4px 20px rgba(0,0,0,0.15)",
                maxWidth: 340,
                zIndex: 1000,
                maxHeight: "70vh",
                overflowY: "auto",
            }}
        >
            <h3 style={{ marginTop: 0, marginBottom: 10 }}>
                <span
                    style={{
                        display: "inline-block",
                        width: 10,
                        height: 10,
                        borderRadius: "50%",
                        background: connected ? "#22c55e" : "#ef4444",
                        marginRight: 8,
                    }}
                ></span>
                Live Tracking ({ambulanceArray.length})
            </h3>

            {ambulanceArray.length === 0 ? (
                <div
                    style={{
                        background: "#f3f4f6",
                        padding: 10,
                        borderRadius: 6,
                        fontSize: 12,
                        color: "#666",
                    }}
                >
                    No ambulances tracking...
                </div>
            ) : (
                ambulanceArray.map((data) => (
                    <div
                        key={data.ambulanceId}
                        style={{
                            background: "#f0f9ff",
                            padding: 10,
                            marginBottom: 10,
                            borderRadius: 6,
                            fontSize: 11,
                            border: "1px solid #0ea5e9",
                        }}
                    >
                        <div style={{ fontWeight: "bold", marginBottom: 5, fontSize: 12 }}>
                            🚑 {data.ambulanceId}
                        </div>
                        <div style={{ marginBottom: 3 }}>
                            📍 Lat: {data.latitude?.toFixed(4)} | Lon: {data.longitude?.toFixed(4)}
                        </div>
                        <div style={{ marginBottom: 3 }}>
                            🚗 Speed: <strong>{data.speed?.toFixed(1)} km/h</strong>
                        </div>
                        <div style={{ marginBottom: 3 }}>
                            🧭 Heading: <strong>{data.heading || 0}°</strong>
                        </div>
                        {data.progress !== undefined && (
                            <>
                                <div style={{ marginTop: 8, marginBottom: 3 }}>
                                    📊 Progress:
                                    <div
                                        style={{
                                            width: "100%",
                                            height: 6,
                                            background: "#dbeafe",
                                            borderRadius: 3,
                                            overflow: "hidden",
                                            marginTop: 3,
                                        }}
                                    >
                                        <div
                                            style={{
                                                width: `${(data.progress || 0) * 100}%`,
                                                height: "100%",
                                                background: "#0ea5e9",
                                                transition: "width 0.3s ease",
                                            }}
                                        />
                                    </div>
                                </div>
                                <div style={{ fontSize: 10, color: "#666" }}>
                                    {Math.round((data.progress || 0) * 100)}% complete
                                </div>
                            </>
                        )}
                    </div>
                ))
            )}
        </div>
    );
}
