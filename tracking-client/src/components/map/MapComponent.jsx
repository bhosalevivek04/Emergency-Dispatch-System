import { useEffect, useRef } from 'react';
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import PropTypes from 'prop-types';
import 'leaflet/dist/leaflet.css';
import './MapComponent.css';

/**
 * MapEventsHandler - Component to handle map click events
 */
function MapEventsHandler({ onMapClick }) {
  useMapEvents({
    click: (e) => {
      if (onMapClick) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

MapEventsHandler.propTypes = {
  onMapClick: PropTypes.func,
};

function MapViewportController({ center, zoom, enabled }) {
  const map = useMap();
  const lastCenterRef = useRef(null);
  const lastZoomRef = useRef(null);

  useEffect(() => {
    if (!enabled || !center || center.length !== 2) {
      return;
    }

    const [lat, lng] = center;
    const lastCenter = lastCenterRef.current;
    const lastZoom = lastZoomRef.current;

    const centerUnchanged =
      lastCenter &&
      Math.abs(lastCenter[0] - lat) < 0.000001 &&
      Math.abs(lastCenter[1] - lng) < 0.000001;
    const zoomUnchanged = lastZoom === zoom;

    if (centerUnchanged && zoomUnchanged) {
      return;
    }

    map.flyTo([lat, lng], zoom, { animate: true, duration: 0.6 });
    lastCenterRef.current = [lat, lng];
    lastZoomRef.current = zoom;
  }, [map, center, zoom, enabled]);

  return null;
}

MapViewportController.propTypes = {
  center: PropTypes.arrayOf(PropTypes.number),
  zoom: PropTypes.number,
  enabled: PropTypes.bool,
};

/**
 * MapComponent - Base map component using Leaflet
 * Initializes a Leaflet map with OpenStreetMap tiles and handles click events
 */
export default function MapComponent({ 
  center = [18.5204, 73.8567], 
  zoom = 13, 
  onMapClick,
  panToCenterOnChange = false,
  children,
  style = { height: '100%', width: '100%' }
}) {
  return (
    <MapContainer
      center={center}
      zoom={zoom}
      style={style}
      className={onMapClick ? 'map-select-mode' : ''}
      scrollWheelZoom={true}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <MapViewportController center={center} zoom={zoom} enabled={panToCenterOnChange} />
      {onMapClick && <MapEventsHandler onMapClick={onMapClick} />}
      {children}
    </MapContainer>
  );
}

MapComponent.propTypes = {
  center: PropTypes.arrayOf(PropTypes.number),
  zoom: PropTypes.number,
  onMapClick: PropTypes.func,
  panToCenterOnChange: PropTypes.bool,
  children: PropTypes.node,
  style: PropTypes.object,
};
