# Requirements Document

## Introduction

This document specifies the requirements for a complete frontend rebuild of the Emergency Dispatch System tracking client. The current frontend is a minimal tracking-only application with no authentication, hardcoded service endpoints, and a single map view. The rebuild will create a production-ready frontend with JWT authentication, role-based dashboards for three distinct user types (Admin, Dispatcher, Ambulance Driver), and full integration with the API Gateway using authenticated requests and WebSocket connections.

The backend already supports complete authentication and authorization with three roles. This frontend rebuild will provide feature parity with backend capabilities and deliver appropriate user experiences for each role.

## Glossary

- **Frontend_Application**: The React-based web application that provides the user interface for the Emergency Dispatch System
- **API_Gateway**: The backend service running on port 8080 that handles authentication, authorization, and routes requests to microservices
- **Auth_Service**: The backend authentication service that issues and validates JWT tokens
- **JWT_Token**: JSON Web Token used for authentication, includes access token and refresh token
- **Admin_User**: A user with the ADMIN role who has full system access including fleet management and system health monitoring
- **Dispatcher_User**: A user with the DISPATCHER role who can create emergencies, monitor the fleet, and perform dispatch operations
- **Driver_User**: A user with the AMBULANCE_DRIVER role who can view their assignments and update their location
- **Dashboard**: A role-specific user interface showing relevant information and controls for that user type
- **Emergency**: An incident requiring ambulance dispatch, with properties including location, priority (HIGH/MEDIUM/LOW), and status
- **Ambulance**: A vehicle in the fleet with properties including ID, status (AVAILABLE/ASSIGNED/ON_ROUTE), location, and speed
- **WebSocket_Connection**: A persistent bidirectional connection for real-time updates, authenticated with JWT
- **API_Service_Layer**: A unified client-side module that handles all HTTP requests to the API Gateway with automatic JWT attachment
- **Token_Refresh**: The process of obtaining a new access token using a refresh token when the current token expires
- **Role_Based_Routing**: Navigation logic that directs users to appropriate dashboards based on their assigned role
- **Emergency_Queue**: A real-time list of active emergencies sorted by priority
- **Fleet_Status**: Current state information for all ambulances including availability and assignment status
- **Location_Update**: A message sent by an ambulance to update its current GPS coordinates
- **Mission**: An active emergency assignment for a specific ambulance driver

## Requirements

### Requirement 1: User Authentication

**User Story:** As any user, I want to log in with my credentials, so that I can access the system with my assigned role and permissions.

#### Acceptance Criteria

1. THE Frontend_Application SHALL provide a login page with username and password input fields
2. WHEN a user submits valid credentials, THE Frontend_Application SHALL send a POST request to /auth/login via the API_Gateway
3. WHEN the Auth_Service returns a successful response, THE Frontend_Application SHALL store the accessToken, refreshToken, username, and roles in React Context state
4. WHEN the Auth_Service returns an error response, THE Frontend_Application SHALL display an error message to the user
5. THE Frontend_Application SHALL NOT use localStorage for token storage
6. WHEN authentication succeeds, THE Frontend_Application SHALL redirect the user to their role-appropriate dashboard

### Requirement 2: Automatic Token Refresh

**User Story:** As an authenticated user, I want my session to remain active without manual re-login, so that I can work continuously without interruption.

#### Acceptance Criteria

1. WHEN the API_Gateway returns a 401 Unauthorized response, THE Frontend_Application SHALL automatically send a POST request to /auth/refresh with the refresh token
2. WHEN the token refresh succeeds, THE Frontend_Application SHALL update the stored accessToken in React Context state
3. WHEN the token refresh succeeds, THE Frontend_Application SHALL retry the original failed request with the new token
4. IF the token refresh fails, THEN THE Frontend_Application SHALL clear all authentication state and redirect to the login page
5. THE Frontend_Application SHALL handle token refresh transparently without user interaction

### Requirement 3: User Logout

**User Story:** As an authenticated user, I want to log out of the system, so that I can end my session securely.

#### Acceptance Criteria

1. THE Frontend_Application SHALL provide a logout control accessible from all dashboards
2. WHEN a user initiates logout, THE Frontend_Application SHALL send a POST request to /auth/logout via the API_Gateway
3. WHEN logout completes, THE Frontend_Application SHALL clear all authentication state from React Context
4. WHEN logout completes, THE Frontend_Application SHALL redirect the user to the login page
5. THE Frontend_Application SHALL perform logout operations regardless of whether the API request succeeds or fails

### Requirement 4: Role-Based Dashboard Routing

**User Story:** As an authenticated user, I want to see a dashboard appropriate for my role, so that I only access features I am authorized to use.

#### Acceptance Criteria

1. WHEN a user with the ADMIN role logs in, THE Frontend_Application SHALL route them to /dashboard/admin
2. WHEN a user with the DISPATCHER role logs in, THE Frontend_Application SHALL route them to /dashboard/dispatcher
3. WHEN a user with the AMBULANCE_DRIVER role logs in, THE Frontend_Application SHALL route them to /dashboard/driver
4. THE Frontend_Application SHALL prevent users from accessing dashboard routes for roles they do not possess
5. WHEN an unauthorized user attempts to access a protected route, THE Frontend_Application SHALL redirect them to their authorized dashboard

### Requirement 5: Unified API Service Layer

**User Story:** As a developer, I want all API requests to go through a unified service layer, so that authentication and error handling are consistent across the application.

#### Acceptance Criteria

1. THE API_Service_Layer SHALL send all HTTP requests to the API_Gateway at localhost:8080
2. THE API_Service_Layer SHALL attach the Authorization header with Bearer token to every request
3. THE API_Service_Layer SHALL intercept 401 responses and trigger the token refresh flow
4. THE API_Service_Layer SHALL provide typed functions for all backend endpoints
5. THE Frontend_Application SHALL NOT make direct HTTP requests outside the API_Service_Layer

### Requirement 6: Dispatcher Live Map Display

**User Story:** As a Dispatcher_User, I want to see a live map with all emergencies and ambulances, so that I can monitor the operational situation in real-time.

#### Acceptance Criteria

1. THE Dashboard SHALL display a map using the Leaflet library
2. THE Dashboard SHALL display emergency markers as red pins with priority badges showing HIGH, MEDIUM, or LOW
3. THE Dashboard SHALL display ambulance markers colored by status: green for AVAILABLE, yellow for ASSIGNED, red for ON_ROUTE
4. WHEN a user clicks an ambulance marker, THE Dashboard SHALL display a popup showing ambulance ID, status, current speed, and active emergency ID
5. WHEN a user clicks an emergency marker, THE Dashboard SHALL display a popup showing emergency ID, priority, status, and assigned ambulance
6. THE Dashboard SHALL update marker positions and colors when new data is received

### Requirement 7: Emergency Creation Interface

**User Story:** As a Dispatcher_User, I want to create new emergencies by clicking on the map, so that I can quickly report incidents at specific locations.

#### Acceptance Criteria

1. THE Dashboard SHALL provide a create emergency panel with a priority selector for HIGH, MEDIUM, or LOW
2. WHEN a user clicks on the map, THE Dashboard SHALL capture the latitude and longitude coordinates
3. WHEN a user submits the emergency creation form, THE Dashboard SHALL send a POST request to /api/emergencies with the location and priority
4. WHEN the emergency is created successfully, THE Dashboard SHALL display a new emergency marker on the map immediately
5. WHEN the emergency is created successfully, THE Dashboard SHALL display a toast notification showing the emergency ID
6. IF emergency creation fails, THEN THE Dashboard SHALL display an error message to the user

### Requirement 8: Emergency Queue Display

**User Story:** As a Dispatcher_User, I want to see a real-time list of all active emergencies, so that I can monitor which incidents need attention.

#### Acceptance Criteria

1. THE Dashboard SHALL display an emergency queue panel showing all emergencies with status PENDING or ASSIGNED
2. THE Dashboard SHALL sort emergencies by priority with HIGH priority first, then MEDIUM, then LOW
3. THE Dashboard SHALL display each emergency with ID, priority badge, status, time since creation, and assigned ambulance
4. THE Dashboard SHALL color-code emergency rows: red for HIGH priority, orange for MEDIUM priority, blue for LOW priority
5. THE Dashboard SHALL update the emergency queue when new emergencies are created or status changes occur
6. THE Dashboard SHALL fetch emergency data from GET /api/emergencies/status/PENDING and GET /api/emergencies/status/ASSIGNED

### Requirement 9: Fleet Status Display

**User Story:** As a Dispatcher_User, I want to see the current status of all ambulances, so that I know which vehicles are available for dispatch.

#### Acceptance Criteria

1. THE Dashboard SHALL display a fleet status panel showing summary counts of ambulances by status
2. THE Dashboard SHALL display the count of AVAILABLE ambulances
3. THE Dashboard SHALL display the count of ASSIGNED ambulances
4. THE Dashboard SHALL display the count of ON_ROUTE ambulances
5. THE Dashboard SHALL display a table with one row per ambulance showing ID, status, and current speed
6. THE Dashboard SHALL fetch fleet data from GET /api/ambulances/available and GET /api/ambulances/fleet
7. THE Dashboard SHALL update fleet status when ambulance state changes occur

### Requirement 10: Admin Dashboard Inheritance

**User Story:** As an Admin_User, I want access to all dispatcher features, so that I can perform operational tasks in addition to administrative functions.

#### Acceptance Criteria

1. THE Admin Dashboard SHALL include all features from the Dispatcher Dashboard
2. THE Admin Dashboard SHALL display the live map with emergency and ambulance markers
3. THE Admin Dashboard SHALL provide the emergency creation interface
4. THE Admin Dashboard SHALL display the emergency queue panel
5. THE Admin Dashboard SHALL display the fleet status panel

### Requirement 11: Fleet Management Interface

**User Story:** As an Admin_User, I want to manage the ambulance fleet, so that I can initialize and monitor vehicle configurations.

#### Acceptance Criteria

1. THE Admin Dashboard SHALL display a fleet management panel showing the complete fleet table with version numbers
2. THE Admin Dashboard SHALL provide a button to initialize the fleet
3. WHEN an admin clicks the initialize fleet button, THE Admin Dashboard SHALL send a POST request to /diagnostic/init-fleet
4. THE Admin Dashboard SHALL fetch detailed fleet status from GET /diagnostic/fleet-status
5. THE Admin Dashboard SHALL display fleet data from GET /api/ambulances/fleet
6. THE Admin Dashboard SHALL restrict fleet initialization to users with the ADMIN role

### Requirement 12: System Health Monitoring

**User Story:** As an Admin_User, I want to monitor the health of all backend services, so that I can identify and respond to system issues.

#### Acceptance Criteria

1. THE Admin Dashboard SHALL display a system health panel showing status indicators for all services
2. THE Admin Dashboard SHALL poll GET /actuator/health for each service through the API_Gateway
3. THE Admin Dashboard SHALL display green status indicators for healthy services
4. THE Admin Dashboard SHALL display red status indicators for unhealthy services
5. THE Admin Dashboard SHALL monitor the emergency service, dispatch service, ambulance service, tracking service, notification service, and auth service
6. THE Admin Dashboard SHALL update health status at regular intervals

### Requirement 13: Metrics Dashboard Access

**User Story:** As an Admin_User, I want to access detailed system metrics, so that I can analyze performance and troubleshoot issues.

#### Acceptance Criteria

1. THE Admin Dashboard SHALL provide a link to the Grafana metrics dashboard
2. THE Admin Dashboard SHALL configure the Grafana link to point to localhost:3001
3. WHEN an admin clicks the metrics link, THE Frontend_Application SHALL open Grafana in a new browser tab

### Requirement 14: Driver Active Mission Display

**User Story:** As a Driver_User, I want to see my currently assigned emergency, so that I know where to respond and what the situation is.

#### Acceptance Criteria

1. THE Driver Dashboard SHALL display the assigned emergency details including ID, location, and priority
2. THE Driver Dashboard SHALL fetch emergency data from GET /api/emergencies/{emergencyId}
3. THE Driver Dashboard SHALL display a map showing the emergency location
4. THE Driver Dashboard SHALL display a route line from the ambulance current position to the emergency location
5. THE Driver Dashboard SHALL calculate and display an ETA countdown based on current position and route distance
6. IF no emergency is assigned, THEN THE Driver Dashboard SHALL display a message indicating no active mission

### Requirement 15: Driver Mission Status Progression

**User Story:** As a Driver_User, I want to update my mission status as I respond to an emergency, so that dispatchers can track my progress.

#### Acceptance Criteria

1. THE Driver Dashboard SHALL display the current mission status: ASSIGNED, ON_ROUTE, ARRIVED, or COMPLETED
2. THE Driver Dashboard SHALL provide controls to progress through status transitions
3. WHEN a driver updates status, THE Driver Dashboard SHALL send the appropriate API request to update the emergency status
4. THE Driver Dashboard SHALL display status progression in a clear visual sequence
5. THE Driver Dashboard SHALL prevent invalid status transitions

### Requirement 16: Driver Location Updates

**User Story:** As a Driver_User, I want my location to be automatically updated, so that dispatchers can track my position in real-time.

#### Acceptance Criteria

1. THE Driver Dashboard SHALL send location updates to POST /api/tracking/location via the API_Gateway
2. THE Driver Dashboard SHALL include the Authorization header with JWT_Token in location update requests
3. THE Driver Dashboard SHALL send location updates at regular intervals while on an active mission
4. THE Driver Dashboard SHALL include ambulance ID, latitude, longitude, and timestamp in location updates
5. THE Driver Dashboard SHALL support both real GPS data and simulated movement for testing

### Requirement 17: Driver Mobile-Optimized Interface

**User Story:** As a Driver_User, I want a simple mobile-friendly interface, so that I can use the system easily while in the field.

#### Acceptance Criteria

1. THE Driver Dashboard SHALL use a mobile-first responsive design
2. THE Driver Dashboard SHALL display only the driver's own ambulance position on the map
3. THE Driver Dashboard SHALL use large touch-friendly controls for status updates
4. THE Driver Dashboard SHALL display mission information in a fullscreen layout
5. THE Driver Dashboard SHALL minimize visual clutter by hiding information not relevant to the driver

### Requirement 18: Authenticated WebSocket Connection

**User Story:** As any authenticated user, I want to receive real-time updates through a secure connection, so that my dashboard reflects current system state without manual refresh.

#### Acceptance Criteria

1. THE Frontend_Application SHALL establish a WebSocket_Connection to ws://localhost:8080/ws/ws-sockjs
2. THE Frontend_Application SHALL include the JWT_Token in the WebSocket connection headers or query parameters
3. THE Frontend_Application SHALL use the STOMP protocol over WebSocket for message subscription
4. IF the WebSocket_Connection fails authentication, THEN THE Frontend_Application SHALL trigger the token refresh flow
5. THE Frontend_Application SHALL automatically reconnect the WebSocket_Connection if it is disconnected

### Requirement 19: Real-Time Emergency Status Updates

**User Story:** As a Dispatcher_User or Admin_User, I want to see emergency status changes in real-time, so that I can monitor incident progression without refreshing the page.

#### Acceptance Criteria

1. THE Dashboard SHALL subscribe to the /topic/emergencies STOMP topic via the WebSocket_Connection
2. WHEN an emergency status changes, THE Dashboard SHALL update the emergency queue display immediately
3. WHEN an emergency status changes, THE Dashboard SHALL update the emergency marker on the map
4. THE Dashboard SHALL handle emergency status transitions from PENDING to ASSIGNED to COMPLETED
5. THE Dashboard SHALL display visual feedback when emergency updates are received

### Requirement 20: Real-Time Ambulance Location Updates

**User Story:** As a Dispatcher_User or Admin_User, I want to see ambulance positions update in real-time, so that I can track vehicle movements without refreshing the page.

#### Acceptance Criteria

1. THE Dashboard SHALL subscribe to ambulance location update topics via the WebSocket_Connection
2. WHEN an ambulance location update is received, THE Dashboard SHALL update the ambulance marker position on the map
3. WHEN an ambulance location update is received, THE Dashboard SHALL update the speed display in the fleet status panel
4. THE Dashboard SHALL animate marker movement smoothly between position updates
5. THE Dashboard SHALL handle location updates for all ambulances in the fleet

### Requirement 21: Real-Time Fleet Status Updates

**User Story:** As a Dispatcher_User or Admin_User, I want to see fleet status changes in real-time, so that I know immediately when ambulances become available or are assigned.

#### Acceptance Criteria

1. THE Dashboard SHALL subscribe to fleet status update topics via the WebSocket_Connection
2. WHEN an ambulance status changes, THE Dashboard SHALL update the ambulance marker color on the map
3. WHEN an ambulance status changes, THE Dashboard SHALL update the fleet status summary counts
4. WHEN an ambulance status changes, THE Dashboard SHALL update the ambulance row in the fleet status table
5. THE Dashboard SHALL handle status transitions between AVAILABLE, ASSIGNED, and ON_ROUTE

### Requirement 22: API Gateway Proxy Configuration

**User Story:** As a system administrator, I want the nginx proxy to route requests through the API Gateway, so that all requests are authenticated and authorized properly.

#### Acceptance Criteria

1. THE nginx configuration SHALL proxy /api requests to the API_Gateway at api-gateway:8080
2. THE nginx configuration SHALL proxy /auth requests to the API_Gateway at api-gateway:8080
3. THE nginx configuration SHALL proxy /ws requests to the API_Gateway WebSocket endpoint
4. THE nginx configuration SHALL NOT proxy requests directly to tracking-service:8085
5. THE nginx configuration SHALL preserve the Authorization header in proxied requests

### Requirement 23: Error Handling and User Feedback

**User Story:** As any user, I want clear feedback when errors occur, so that I understand what went wrong and what actions I can take.

#### Acceptance Criteria

1. WHEN an API request fails, THE Frontend_Application SHALL display an error message to the user
2. THE Frontend_Application SHALL display different error messages for network errors, authentication errors, and validation errors
3. THE Frontend_Application SHALL display error messages using a toast notification component
4. THE Frontend_Application SHALL automatically dismiss success notifications after 5 seconds
5. THE Frontend_Application SHALL require manual dismissal for error notifications
6. WHEN a WebSocket_Connection fails, THE Frontend_Application SHALL display a connection status indicator

### Requirement 24: Loading States and User Feedback

**User Story:** As any user, I want to see loading indicators during asynchronous operations, so that I know the system is processing my request.

#### Acceptance Criteria

1. WHEN the Frontend_Application is fetching data from the API_Gateway, THE Frontend_Application SHALL display a loading indicator
2. WHEN a user submits a form, THE Frontend_Application SHALL disable the submit button until the request completes
3. THE Frontend_Application SHALL display skeleton loaders for dashboard panels while initial data is loading
4. THE Frontend_Application SHALL display a loading spinner for map markers while fetching location data
5. WHEN data loading completes, THE Frontend_Application SHALL remove all loading indicators

### Requirement 25: Session Persistence Across Page Refresh

**User Story:** As an authenticated user, I want to remain logged in when I refresh the page, so that I don't have to re-enter my credentials frequently.

#### Acceptance Criteria

1. THE Frontend_Application SHALL maintain authentication state in React Context that persists across component re-renders
2. WHEN a user refreshes the page, THE Frontend_Application SHALL attempt to use the stored refresh token to obtain a new access token
3. IF the refresh token is valid, THEN THE Frontend_Application SHALL restore the user session and redirect to their dashboard
4. IF the refresh token is invalid or expired, THEN THE Frontend_Application SHALL redirect to the login page
5. THE Frontend_Application SHALL handle session restoration without requiring localStorage

