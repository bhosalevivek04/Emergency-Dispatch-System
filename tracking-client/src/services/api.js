import apiClient from './apiClient.js';

/**
 * Typed API functions for the Emergency Dispatch System.
 * All functions use the configured apiClient which handles JWT authentication and token refresh.
 * 
 * @module api
 */

// ============================================================================
// Authentication API
// ============================================================================

/**
 * Authentication API functions
 * @namespace authApi
 */
export const authApi = {
  /**
   * Login with username and password
   * @param {Object} credentials - Login credentials
   * @param {string} credentials.username - Username
   * @param {string} credentials.password - Password
   * @returns {Promise<Object>} Login response with tokens and user info
   * @returns {string} return.accessToken - JWT access token
   * @returns {string} return.refreshToken - JWT refresh token
   * @returns {string} return.username - Username
   * @returns {string[]} return.roles - User roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER)
   */
  login: (credentials) =>
    apiClient.post('/auth/login', credentials).then(res => res.data),

  /**
   * Refresh access token using refresh token
   * @param {string} refreshToken - Refresh token
   * @returns {Promise<Object>} Response with new access token
   * @returns {string} return.accessToken - New JWT access token
   */
  refresh: (refreshToken) =>
    apiClient.post('/auth/refresh', { refreshToken }).then(res => res.data),

  /**
   * Logout and invalidate tokens
   * @returns {Promise<void>}
   */
  logout: () =>
    apiClient.post('/auth/logout').then(res => res.data),
};

// ============================================================================
// Emergency API
// ============================================================================

/**
 * Emergency API functions
 * @namespace emergencyApi
 */
export const emergencyApi = {
  /**
   * Create a new emergency
   * @param {Object} data - Emergency data
   * @param {number} data.latitude - Emergency latitude
   * @param {number} data.longitude - Emergency longitude
   * @param {('HIGH'|'MEDIUM'|'LOW')} data.priority - Emergency priority
   * @returns {Promise<Object>} Created emergency
   * @returns {string} return.id - Emergency ID
   * @returns {number} return.latitude - Emergency latitude
   * @returns {number} return.longitude - Emergency longitude
   * @returns {('HIGH'|'MEDIUM'|'LOW')} return.priority - Emergency priority
   * @returns {('PENDING'|'ASSIGNED'|'COMPLETED')} return.status - Emergency status
   * @returns {string} [return.assignedAmbulanceId] - Assigned ambulance ID
   * @returns {string} return.createdAt - ISO 8601 timestamp
   */
  create: (data) =>
    apiClient.post('/api/emergencies', data).then(res => res.data),

  /**
   * Get emergency by ID
   * @param {string} id - Emergency ID
   * @returns {Promise<Object>} Emergency details
   */
  getById: (id) =>
    apiClient.get(`/api/emergencies/${id}`).then(res => res.data),

  /**
   * Get emergencies by status
   * @param {('PENDING'|'ASSIGNED'|'COMPLETED')} status - Emergency status
   * @returns {Promise<Array<Object>>} Array of emergencies
   */
  getByStatus: (status) =>
    apiClient.get(`/api/emergencies/status/${status}`).then(res => res.data),

  /**
   * Update emergency status
   * @param {string} id - Emergency ID
   * @param {('PENDING'|'ASSIGNED'|'COMPLETED')} status - New status
   * @returns {Promise<Object>} Updated emergency
   */
  updateStatus: (id, status) =>
    apiClient.put(`/api/emergencies/${id}/status`, { status }).then(res => res.data),
};

// ============================================================================
// Ambulance API
// ============================================================================

/**
 * Ambulance API functions
 * @namespace ambulanceApi
 */
export const ambulanceApi = {
  /**
   * Get all ambulances in the fleet
   * @returns {Promise<Array<Object>>} Array of ambulances
   * @returns {string} return[].id - Ambulance ID
   * @returns {('AVAILABLE'|'ASSIGNED'|'ON_ROUTE')} return[].status - Ambulance status
   * @returns {number} return[].latitude - Ambulance latitude
   * @returns {number} return[].longitude - Ambulance longitude
   * @returns {number} return[].speed - Ambulance speed in km/h
   * @returns {string} [return[].assignedEmergencyId] - Assigned emergency ID
   * @returns {number} [return[].version] - Version number for optimistic locking
   */
  getFleet: () =>
    apiClient.get('/api/ambulances/fleet').then(res => res.data),

  /**
   * Get available ambulances
   * @returns {Promise<Array<Object>>} Array of available ambulances
   */
  getAvailable: () =>
    apiClient.get('/api/ambulances/available').then(res => res.data),

  /**
   * Get ambulance by ID
   * @param {string} id - Ambulance ID
   * @returns {Promise<Object>} Ambulance details
   */
  getById: (id) =>
    apiClient.get(`/api/ambulances/${id}`).then(res => res.data),
};

// ============================================================================
// Tracking API
// ============================================================================

/**
 * Tracking API functions
 * @namespace trackingApi
 */
export const trackingApi = {
  /**
   * Update ambulance location
   * @param {Object} data - Location update data
   * @param {string} data.ambulanceId - Ambulance ID
   * @param {number} data.latitude - Current latitude
   * @param {number} data.longitude - Current longitude
   * @param {string} data.timestamp - ISO 8601 timestamp
   * @returns {Promise<void>}
   */
  updateLocation: (data) =>
    apiClient.post('/api/tracking/location', data).then(res => res.data),
};

// ============================================================================
// Diagnostic API (Admin only)
// ============================================================================

/**
 * Diagnostic API functions (Admin only)
 * @namespace diagnosticApi
 */
export const diagnosticApi = {
  /**
   * Initialize the ambulance fleet
   * @returns {Promise<void>}
   */
  initFleet: () =>
    apiClient.post('/diagnostic/init-fleet').then(res => res.data),

  /**
   * Get detailed fleet status
   * @returns {Promise<Object>} Fleet status
   * @returns {Array<Object>} return.ambulances - Array of ambulances with details
   * @returns {number} return.totalCount - Total number of ambulances
   * @returns {number} return.availableCount - Number of available ambulances
   */
  getFleetStatus: () =>
    apiClient.get('/diagnostic/fleet-status').then(res => res.data),

  /**
   * Get service health status
   * @param {string} [service] - Optional service name to check specific service
   * @returns {Promise<Object>} Service health status
   * @returns {('UP'|'DOWN')} return.status - Service status
   * @returns {Object} [return.details] - Additional health details
   */
  getServiceHealth: (service) => {
    const url = service ? `/actuator/health/${service}` : '/actuator/health';
    return apiClient.get(url).then(res => res.data);
  },
};
