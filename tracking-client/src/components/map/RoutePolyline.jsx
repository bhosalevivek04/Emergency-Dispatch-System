import { Polyline } from 'react-leaflet';
import PropTypes from 'prop-types';

/**
 * RoutePolyline - Displays a route line from ambulance to emergency location
 * Used in the driver dashboard to show the path to the emergency
 */
export default function RoutePolyline({ 
  ambulancePosition, 
  emergencyPosition,
  color = '#3b82f6',
  weight = 4,
  opacity = 0.7,
  dashArray = '10, 10'
}) {
  // Create positions array for the polyline
  const positions = [
    [ambulancePosition.latitude, ambulancePosition.longitude],
    [emergencyPosition.latitude, emergencyPosition.longitude],
  ];

  return (
    <Polyline
      positions={positions}
      pathOptions={{
        color,
        weight,
        opacity,
        dashArray,
      }}
    />
  );
}

RoutePolyline.propTypes = {
  ambulancePosition: PropTypes.shape({
    latitude: PropTypes.number.isRequired,
    longitude: PropTypes.number.isRequired,
  }).isRequired,
  emergencyPosition: PropTypes.shape({
    latitude: PropTypes.number.isRequired,
    longitude: PropTypes.number.isRequired,
  }).isRequired,
  color: PropTypes.string,
  weight: PropTypes.number,
  opacity: PropTypes.number,
  dashArray: PropTypes.string,
};
