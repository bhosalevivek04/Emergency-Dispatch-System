# Implementation Plan: Frontend Authentication and Dashboards

## Overview

This implementation plan builds a production-ready React frontend with JWT authentication, role-based dashboards, and real-time WebSocket integration for the Emergency Dispatch System. The implementation follows a bottom-up approach: core infrastructure first (auth, API layer, WebSocket), then shared components, then role-specific dashboards, and finally integration and testing.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Initialize React project with Vite and JSX
  - Install dependencies: react-router-dom, axios, @stomp/stompjs, sockjs-client, leaflet, react-leaflet, tailwindcss, prop-types
  - Set up Tailwind CSS configuration
  - Create directory structure: src/components, src/contexts, src/services, src/utils, src/pages
  - _Requirements: All requirements depend on proper project setup_

- [x] 2. Implement core data model constants
  - [x] 2.1 Create JSDoc comments and PropTypes for all data models
    - Document User, Emergency, Ambulance, LocationUpdate, AuthTokens, ServiceHealth shapes using JSDoc
    - Create PropTypes definitions for component props
    - Export constants from src/utils/constants.js
    - _Requirements: 5.4, 1.1, 6.1_

- [x] 3. Implement authentication context and state management
  - [x] 3.1 Create AuthContext with authentication state
    - Implement AuthState object with isAuthenticated, user, accessToken, refreshToken
    - Create AuthContext with login, logout, refreshAccessToken functions
    - Store tokens in memory only (no localStorage)
    - _Requirements: 1.3, 1.5, 25.1_
  
  - [x] 3.2 Implement login function in AuthContext
    - Call POST /auth/login with credentials
    - Store tokens and user info in context state on success
    - Handle authentication errors
    - _Requirements: 1.2, 1.3, 1.4_
  
  - [x] 3.3 Implement logout function in AuthContext
    - Call POST /auth/logout to invalidate tokens
    - Clear all authentication state from context
    - Disconnect WebSocket connection
    - _Requirements: 3.2, 3.3, 3.5_
  
  - [x] 3.4 Implement token refresh function in AuthContext
    - Call POST /auth/refresh with refresh token
    - Update accessToken in context state on success
    - Clear state and redirect to login on failure
    - _Requirements: 2.2, 2.3, 2.4_

- [x] 4. Implement unified API service layer
  - [x] 4.1 Create Axios client with base configuration
    - Configure baseURL to http://localhost:8080
    - Set default headers and timeout
    - _Requirements: 5.1, 22.1, 22.2_
  
  - [x] 4.2 Implement request interceptor for JWT attachment
    - Attach Authorization header with Bearer token to all requests
    - _Requirements: 5.2, 22.5_
  
  - [x] 4.3 Implement response interceptor for token refresh
    - Intercept 401 responses
    - Trigger token refresh flow
    - Retry original request with new token
    - Redirect to login if refresh fails
    - _Requirements: 2.1, 2.3, 2.5, 5.3_
  
  - [x] 4.4 Create typed API functions for authentication endpoints
    - Implement authApi.login, authApi.refresh, authApi.logout
    - _Requirements: 1.2, 2.1, 3.2, 5.4_
  
  - [x] 4.5 Create typed API functions for emergency endpoints
    - Implement emergencyApi.create, emergencyApi.getById, emergencyApi.getByStatus, emergencyApi.updateStatus
    - _Requirements: 7.3, 8.6, 14.2, 15.3, 5.4_
  
  - [x] 4.6 Create typed API functions for ambulance endpoints
    - Implement ambulanceApi.getFleet, ambulanceApi.getAvailable, ambulanceApi.getById
    - _Requirements: 9.6, 5.4_
  
  - [x] 4.7 Create typed API functions for tracking endpoints
    - Implement trackingApi.updateLocation
    - _Requirements: 16.1, 16.2, 5.4_
  
  - [x] 4.8 Create typed API functions for diagnostic endpoints
    - Implement diagnosticApi.initFleet, diagnosticApi.getFleetStatus, diagnosticApi.getServiceHealth
    - _Requirements: 11.3, 11.4, 12.2, 5.4_

- [x] 5. Implement WebSocket service
  - [x] 5.1 Create STOMP WebSocket client wrapper
    - Implement connect method with JWT authentication
    - Implement disconnect method
    - Implement subscribe method with topic and callback
    - Implement isConnected status check
    - Handle connection errors and reconnection logic
    - _Requirements: 18.1, 18.2, 18.3, 18.5_
  
  - [x] 5.2 Add WebSocket authentication handling
    - Include JWT token in connection headers
    - Trigger token refresh on authentication failure
    - _Requirements: 18.2, 18.4_

- [x] 6. Implement shared UI components
  - [x] 6.1 Create ToastNotification component
    - Support success, error, info, warning types
    - Implement auto-dismiss for success/info
    - Require manual dismissal for error/warning
    - Support stacking multiple toasts
    - _Requirements: 23.3, 23.4, 23.5_
  
  - [x] 6.2 Create LoadingSpinner component
    - Support small, medium, large sizes
    - Support full-screen overlay mode
    - _Requirements: 24.1, 24.4_
  
  - [x] 6.3 Create SkeletonLoader component
    - Support text, card, table, map types
    - Implement animated placeholder
    - _Requirements: 24.3_

- [x] 7. Implement authentication pages and routing
  - [x] 7.1 Create LoginPage component
    - Render username and password input fields
    - Implement form submission with loading state
    - Display error messages from failed login
    - Redirect to role-appropriate dashboard on success
    - _Requirements: 1.1, 1.2, 1.4, 1.6, 24.2_
  
  - [x] 7.2 Create ProtectedRoute component
    - Check authentication status before rendering
    - Redirect to /login if not authenticated
    - Check user roles against required roles
    - Redirect to authorized dashboard if role mismatch
    - _Requirements: 4.3, 4.4, 4.5_
  
  - [x] 7.3 Set up React Router with role-based routes
    - Configure routes for /login, /dashboard/admin, /dashboard/dispatcher, /dashboard/driver
    - Wrap dashboard routes with ProtectedRoute
    - Implement role-based navigation logic
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 8. Checkpoint - Ensure authentication flow works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement map components
  - [x] 9.1 Create base MapComponent with Leaflet
    - Initialize Leaflet map with center and zoom
    - Handle map click events
    - _Requirements: 6.1, 7.2_
  
  - [x] 9.2 Create EmergencyMarker component
    - Render red pin markers for emergencies
    - Display priority badge (HIGH/MEDIUM/LOW)
    - Show popup with emergency details on click
    - _Requirements: 6.2, 6.5_
  
  - [x] 9.3 Create AmbulanceMarker component
    - Render markers colored by status (green/yellow/red)
    - Show popup with ambulance details on click
    - Animate marker position updates smoothly
    - _Requirements: 6.3, 6.4, 6.6, 20.4_
  
  - [x] 9.4 Create RoutePolyline component for driver dashboard
    - Draw line from ambulance to emergency location
    - _Requirements: 14.4_

- [x] 10. Implement Dispatcher Dashboard
  - [x] 10.1 Create DispatcherDashboard layout component
    - Implement two-column layout: map panel (60%) and side panel (40%)
    - Add header with logout button
    - _Requirements: 6.1, 3.1_
  
  - [x] 10.2 Implement emergency queue panel
    - Fetch emergencies with PENDING and ASSIGNED status
    - Sort by priority (HIGH, MEDIUM, LOW)
    - Display emergency cards with ID, priority badge, status, time since creation, assigned ambulance
    - Apply color-coding by priority
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6_
  
  - [x] 10.3 Implement fleet status panel
    - Calculate summary counts by status (AVAILABLE, ASSIGNED, ON_ROUTE)
    - Display count badges
    - Render table with ambulance ID, status, speed
    - Apply color-coding to status badges
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  
  - [x] 10.4 Implement emergency creation control
    - Add floating button to activate creation mode
    - Capture map click to set emergency location
    - Display form panel with priority selector
    - Submit POST /api/emergencies with location and priority
    - Show toast notification on success
    - Display new emergency marker immediately
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [x] 10.5 Integrate map with emergency and ambulance markers
    - Render all emergencies as markers on map
    - Render all ambulances as markers on map
    - Handle marker click events
    - _Requirements: 6.2, 6.3, 6.4, 6.5_
  
  - [x] 10.6 Connect WebSocket for real-time updates
    - Establish WebSocket connection on dashboard mount
    - Subscribe to /topic/emergencies
    - Subscribe to /topic/ambulances/location
    - Subscribe to /topic/ambulances/status
    - Update local state when messages received
    - Disconnect on unmount
    - _Requirements: 18.1, 18.5, 19.1, 19.2, 19.3, 20.1, 20.2, 20.3, 21.1, 21.2, 21.3, 21.4_
  
  - [x] 10.7 Implement loading states for dashboard data
    - Show skeleton loaders while fetching initial data
    - Display loading spinner for map markers
    - _Requirements: 24.1, 24.3, 24.4, 24.5_
  
  - [x] 10.8 Implement error handling for dashboard operations
    - Display error toasts for failed API requests
    - Show connection status indicator for WebSocket
    - Handle network errors gracefully
    - _Requirements: 23.1, 23.2, 23.6_

- [x] 11. Checkpoint - Ensure dispatcher dashboard works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement Admin Dashboard
  - [x] 12.1 Create AdminDashboard component inheriting dispatcher features
    - Include all DispatcherDashboard components (map, emergency queue, fleet status)
    - Add admin-specific panels below dispatcher features
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [x] 12.2 Implement fleet management panel
    - Fetch detailed fleet status from GET /diagnostic/fleet-status
    - Display fleet table with version numbers
    - Add "Initialize Fleet" button
    - Call POST /diagnostic/init-fleet on button click
    - Show loading state during initialization
    - Refresh fleet status after initialization
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_
  
  - [x] 12.3 Implement system health monitoring panel
    - Poll GET /actuator/health for all services every 30 seconds
    - Display service name and status indicator (green/red/gray)
    - Monitor emergency, dispatch, ambulance, tracking, notification, auth services
    - Show last checked timestamp
    - Handle polling errors gracefully
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_
  
  - [x] 12.4 Add Grafana metrics link
    - Display link to Grafana dashboard at localhost:3001
    - Open link in new browser tab
    - _Requirements: 13.1, 13.2, 13.3_

- [x] 13. Implement Driver Dashboard
  - [x] 13.1 Create DriverDashboard mobile-optimized layout
    - Implement fullscreen mobile-first layout
    - Add header with logout button
    - Use large touch-friendly controls
    - _Requirements: 17.1, 17.3, 17.4, 17.5_
  
  - [x] 13.2 Implement mission info card
    - Fetch assigned emergency from GET /api/emergencies/{emergencyId}
    - Display emergency ID, location, priority
    - Calculate distance from current location to emergency
    - Estimate and display ETA countdown
    - Show current mission status
    - Display "No active mission" message if no assignment
    - _Requirements: 14.1, 14.2, 14.5, 14.6_
  
  - [x] 13.3 Implement mission map for driver
    - Display map with emergency marker
    - Display driver's ambulance marker
    - Draw route polyline from ambulance to emergency
    - Update ambulance position in real-time
    - _Requirements: 14.3, 14.4, 17.2_
  
  - [x] 13.4 Implement status progression controls
    - Display current mission status (ASSIGNED, ON_ROUTE, ARRIVED, COMPLETED)
    - Show large touch-friendly buttons for status transitions
    - Only show valid next status button
    - Call appropriate API to update emergency status
    - Disable buttons during API call
    - Show confirmation for COMPLETED status
    - Display success feedback after status change
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_
  
  - [x] 13.5 Implement location tracking service
    - Use browser Geolocation API to get current position
    - Send location updates to POST /api/tracking/location at regular intervals
    - Include Authorization header with JWT token
    - Only send updates when mission is active
    - Include ambulanceId, latitude, longitude, timestamp in updates
    - Handle geolocation errors gracefully
    - Provide simulated movement option for testing
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_
  
  - [x] 13.6 Connect WebSocket for driver mission updates
    - Subscribe to /topic/driver/{ambulanceId}/mission
    - Update mission state when messages received
    - _Requirements: 18.1_

- [x] 14. Checkpoint - Ensure driver dashboard works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement session persistence and error handling
  - [x] 15.1 Add session restoration on page refresh
    - Attempt to use refresh token to obtain new access token on app load
    - Restore user session if refresh token is valid
    - Redirect to login if refresh token is invalid
    - _Requirements: 25.2, 25.3, 25.4, 25.5_
  
  - [x] 15.2 Implement global error boundary
    - Catch React component errors
    - Display user-friendly error message
    - Log errors for debugging
    - _Requirements: 23.1_
  
  - [x] 15.3 Add WebSocket connection status indicator
    - Display connection status in dashboard header
    - Show reconnecting state during connection attempts
    - _Requirements: 23.6_

- [x] 16. Final integration and polish
  - [x] 16.1 Verify all API requests go through unified service layer
    - Audit codebase for direct axios calls
    - Ensure all requests use typed API functions
    - _Requirements: 5.5_
  
  - [x] 16.2 Verify nginx proxy configuration compatibility
    - Ensure frontend makes requests to correct endpoints
    - Verify /api, /auth, /ws paths are used correctly
    - _Requirements: 22.1, 22.2, 22.3, 22.4_
  
  - [x] 16.3 Test role-based routing and authorization
    - Verify ADMIN users can access admin dashboard
    - Verify DISPATCHER users can access dispatcher dashboard
    - Verify AMBULANCE_DRIVER users can access driver dashboard
    - Verify users cannot access unauthorized routes
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 16.4 Test complete authentication flow
    - Test login with valid credentials
    - Test login with invalid credentials
    - Test automatic token refresh on 401
    - Test logout functionality
    - Test session persistence on page refresh
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 25.1, 25.2_
  
  - [x] 16.5 Test real-time WebSocket updates
    - Test emergency status updates appear in real-time
    - Test ambulance location updates appear in real-time
    - Test fleet status updates appear in real-time
    - Test WebSocket reconnection on disconnect
    - _Requirements: 18.5, 19.1, 19.2, 19.3, 20.1, 20.2, 20.3, 21.1, 21.2, 21.3_

- [x] 17. Final checkpoint - Ensure all features work end-to-end
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks build incrementally on previous work
- Each task references specific requirements for traceability
- Checkpoints ensure validation at key milestones
- Focus on core functionality first, then polish and error handling
- WebSocket integration happens after basic dashboard functionality is working
- Driver dashboard is implemented last as it has the most specialized requirements
