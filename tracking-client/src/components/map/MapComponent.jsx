import { MapContainer, TileLayer, useMapEvents } from 'react-leaflet';
import PropTypes from 'prop-types';
import 'leaflet/dist/leaflet.css';

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

/**
 * MapComponent - Base map component using Leaflet
 * Initializes a Leaflet map with OpenStreetMap tiles and handles click events
 */
export default function MapComponent({ 
  center = [18.5204, 73.8567], 
  zoom = 13, 
  onMapClick,
  children,
  style = { height: '100%', width: '100%' }
}) {
  return (
    <MapContainer
      center={center}
      zoom={zoom}
      style={style}
      scrollWheelZoom={true}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {onMapClick && <MapEventsHandler onMapClick={onMapClick} />}
      {children}
    </MapContainer>
  );
}

MapComponent.propTypes = {
  center: PropTypes.arrayOf(PropTypes.number),
  zoom: PropTypes.number,
  onMapClick: PropTypes.func,
  children: PropTypes.node,
  style: PropTypes.object,
};
