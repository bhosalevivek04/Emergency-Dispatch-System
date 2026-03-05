import axios from 'axios';
import { withCorrelationHeader } from '../utils/correlation';

/**
 * Axios client instance configured for the Emergency Dispatch System API Gateway.
 * 
 * Features:
 * - Base URL configured to API Gateway at localhost:8080
 * - Request interceptor automatically attaches JWT Bearer token
 * - Response interceptor handles 401 errors with automatic token refresh
 * - Retries failed requests after successful token refresh
 */

// Store reference to auth context - will be set by initializeApiClient
let authContextRef = null;

/**
 * Initialize the API client with auth context reference.
 * This must be called before making any API requests.
 * 
 * @param {Object} authContext - The auth context containing tokens and refresh function
 */
export const initializeApiClient = (authContext) => {
  authContextRef = authContext;
};

/**
 * Axios client instance with base configuration
 */
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request interceptor - Attaches JWT Bearer token to all requests
 */
apiClient.interceptors.request.use(
  (config) => {
    config.headers = withCorrelationHeader(config.headers || {});
    if (authContextRef && authContextRef.accessToken) {
      config.headers.Authorization = `Bearer ${authContextRef.accessToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response interceptor - Handles 401 errors with automatic token refresh
 * 
 * When a 401 Unauthorized response is received:
 * 1. Attempts to refresh the access token using the refresh token
 * 2. Updates the Authorization header with the new token
 * 3. Retries the original request
 * 4. If refresh fails, triggers logout and redirects to login page
 */
apiClient.interceptors.response.use(
  (response) => {
    const correlationId = response.headers['x-correlation-id'];
    if (correlationId) {
      response.correlationId = correlationId;
    }
    // Pass through successful responses
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // Check if this is a 401 error and we haven't already retried
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Attempt to refresh the access token
        if (authContextRef && authContextRef.refreshAccessToken) {
          const newToken = await authContextRef.refreshAccessToken();
          
          // Update the Authorization header with the new token
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          
          // Retry the original request with the new token
          return apiClient(originalRequest);
        }
      } catch (refreshError) {
        // Token refresh failed - auth context will handle logout and redirect
        return Promise.reject(refreshError);
      }
    }

    // For all other errors, reject the promise
    return Promise.reject(error);
  }
);

export default apiClient;
