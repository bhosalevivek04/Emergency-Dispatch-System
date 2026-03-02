import { useEffect, useState } from "react";
import MapView from "./components/MapView";
import InfoPanel from "./components/InfoPanel";
import SimulationPanel from "./components/SimulationPanel";
import { connectWebSocket, fetchInitialLocations } from "./services/websocket";

function App() {
  const [ambulances, setAmbulances] = useState({});
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    connectWebSocket((location) => {
      setAmbulances(prev => ({
        ...prev,
        [location.ambulanceId]: location
      }));
    }, setConnected);

    fetchInitialLocations().then(data => {
      if (Array.isArray(data)) {
        const ambulancesObj = {};
        data.forEach(amb => {
          ambulancesObj[amb.ambulanceId] = amb;
        });
        setAmbulances(ambulancesObj);
      } else {
        setAmbulances(data);
      }
    });
  }, []);

  const handleSimulationStart = (location) => {
    // Merge simulation data with existing ambulance state
    setAmbulances(prev => ({
      ...prev,
      [location.ambulanceId]: {
        ...prev[location.ambulanceId],
        ...location
      }
    }));
  };

  return (
    <>
      <MapView ambulances={ambulances} />
      <InfoPanel connected={connected} ambulances={ambulances} />
      <SimulationPanel onSimulationStart={handleSimulationStart} />
    </>
  );
}

export default App;
