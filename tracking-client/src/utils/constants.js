import PropTypes from 'prop-types';

/**
 * @typedef {Object} User
 * @property {string} username - The username of the authenticated user
 * @property {string[]} roles - Array of role strings (e.g., ['ADMIN'], ['DISPATCHER'], ['AMBULANCE_DRIVER'])
 */

/**
 * PropTypes definition for User object
 */
export const UserPropType = PropTypes.shape({
  username: PropTypes.string.isRequired,
  roles: PropTypes.arrayOf(PropTypes.string).isRequired,
});

/**
 * @typedef {Object} Emergency
 * @property {string} id - Unique identifier for the emergency
 * @property {number} latitude - Latitude coordinate of the emergency location
 * @property {number} longitude - Longitude coordinate of the emergency location
 * @property {'HIGH'|'MEDIUM'|'LOW'} priority - Priority level of the emergency
 * @property {'PENDING'|'ASSIGNED'|'COMPLETED'} status - Current status of the emergency
 * @property {string} [assignedAmbulanceId] - ID of the ambulance assigned to this emergency (optional)
 * @property {string} createdAt - ISO 8601 timestamp of when the emergency was created
 */

/**
 * PropTypes definition for Emergency object
 */
export const EmergencyPropType = PropTypes.shape({
  id: PropTypes.string.isRequired,
  latitude: PropTypes.number.isRequired,
  longitude: PropTypes.number.isRequired,
  priority: PropTypes.oneOf(['HIGH', 'MEDIUM', 'LOW']).isRequired,
  status: PropTypes.oneOf(['PENDING', 'ASSIGNED', 'COMPLETED']).isRequired,
  assignedAmbulanceId: PropTypes.string,
  createdAt: PropTypes.string.isRequired,
});

/**
 * @typedef {Object} Ambulance
 * @property {string} id - Unique identifier for the ambulance
 * @property {'AVAILABLE'|'ASSIGNED'|'ON_ROUTE'} status - Current operational status of the ambulance
 * @property {number} latitude - Current latitude coordinate of the ambulance
 * @property {number} longitude - Current longitude coordinate of the ambulance
 * @property {number} speed - Current speed of the ambulance in km/h
 * @property {string} [assignedEmergencyId] - ID of the emergency assigned to this ambulance (optional)
 * @property {number} [version] - Version number for optimistic locking (optional)
 */

/**
 * PropTypes definition for Ambulance object
 */
export const AmbulancePropType = PropTypes.shape({
  id: PropTypes.string.isRequired,
  status: PropTypes.oneOf(['AVAILABLE', 'ASSIGNED', 'ON_ROUTE']).isRequired,
  latitude: PropTypes.number.isRequired,
  longitude: PropTypes.number.isRequired,
  speed: PropTypes.number.isRequired,
  assignedEmergencyId: PropTypes.string,
  version: PropTypes.number,
});

/**
 * @typedef {Object} LocationUpdate
 * @property {string} ambulanceId - ID of the ambulance sending the location update
 * @property {number} latitude - Current latitude coordinate
 * @property {number} longitude - Current longitude coordinate
 * @property {string} timestamp - ISO 8601 timestamp of when the location was recorded
 */

/**
 * PropTypes definition for LocationUpdate object
 */
export const LocationUpdatePropType = PropTypes.shape({
  ambulanceId: PropTypes.string.isRequired,
  latitude: PropTypes.number.isRequired,
  longitude: PropTypes.number.isRequired,
  timestamp: PropTypes.string.isRequired,
});

/**
 * @typedef {Object} AuthTokens
 * @property {string} accessToken - JWT access token for API authentication
 * @property {string} refreshToken - JWT refresh token for obtaining new access tokens
 */

/**
 * PropTypes definition for AuthTokens object
 */
export const AuthTokensPropType = PropTypes.shape({
  accessToken: PropTypes.string.isRequired,
  refreshToken: PropTypes.string.isRequired,
});

/**
 * @typedef {Object} ServiceHealth
 * @property {'UP'|'DOWN'} status - Health status of the service
 * @property {Object} [details] - Additional health check details (optional)
 */

/**
 * PropTypes definition for ServiceHealth object
 */
export const ServiceHealthPropType = PropTypes.shape({
  status: PropTypes.oneOf(['UP', 'DOWN']).isRequired,
  details: PropTypes.object,
});

// Export all PropTypes as a single object for convenience
export const DataModelPropTypes = {
  User: UserPropType,
  Emergency: EmergencyPropType,
  Ambulance: AmbulancePropType,
  LocationUpdate: LocationUpdatePropType,
  AuthTokens: AuthTokensPropType,
  ServiceHealth: ServiceHealthPropType,
};
