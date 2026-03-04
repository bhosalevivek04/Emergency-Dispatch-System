# Requirements Document

## Introduction

This document specifies the requirements for implementing service-level authorization in the Emergency Dispatch System. The system currently has JWT authentication at the API Gateway level, which validates tokens and forwards user context via HTTP headers (X-User-Username, X-User-Roles). This feature adds role-based access control (RBAC) enforcement at each microservice to ensure that authenticated users can only access endpoints appropriate for their assigned roles.

The authorization system will support three roles: ADMIN (full system access), DISPATCHER (emergency and dispatch operations), and AMBULANCE_DRIVER (assigned emergency operations and location updates).

## Glossary

- **Authorization_Filter**: A Spring Security component that intercepts HTTP requests and extracts user roles from headers
- **Emergency_Service**: The microservice that manages emergency request creation and status tracking (port 8081)
- **Ambulance_Service**: The microservice that manages the ambulance fleet and location updates (port 8082)
- **Tracking_Service**: The microservice that provides real-time location tracking via REST and WebSocket (port 8085)
- **Dispatch_Service**: The internal microservice that handles emergency dispatch logic (port 8083)
- **Notification_Service**: The internal microservice that sends notifications (port 8084)
- **API_Gateway**: The entry point that validates JWT tokens and forwards X-User-Roles header (port 8080)
- **X-User-Roles**: HTTP header containing comma-separated role names forwarded by API_Gateway
- **X-User-Username**: HTTP header containing the authenticated username forwarded by API_Gateway
- **ADMIN**: Role with full system access including metrics and resource management
- **DISPATCHER**: Role with access to create emergencies and manage dispatch operations
- **AMBULANCE_DRIVER**: Role with access to view assigned emergencies and update location
- **Protected_Endpoint**: An HTTP endpoint that requires specific roles for access
- **Security_Context**: Spring Security object containing authenticated user information and roles

## Requirements

### Requirement 1: Extract User Roles from Gateway Headers

**User Story:** As a microservice, I want to extract user roles from the X-User-Roles header, so that I can enforce authorization rules based on authenticated user roles.

#### Acceptance Criteria

1. WHEN an HTTP request contains the X-User-Roles header, THE Authorization_Filter SHALL parse the comma-separated role values into a collection of role objects
2. WHEN an HTTP request contains the X-User-Username header, THE Authorization_Filter SHALL extract the username value
3. WHEN the Authorization_Filter successfully extracts roles and username, THE Authorization_Filter SHALL create a Security_Context with the extracted information
4. WHEN an HTTP request is missing the X-User-Roles header, THE Authorization_Filter SHALL create a Security_Context with an empty role collection
5. THE Authorization_Filter SHALL execute before any Protected_Endpoint handler method

### Requirement 2: Enforce Emergency Service Authorization Rules

**User Story:** As a system administrator, I want Emergency_Service endpoints protected by role-based access control, so that only authorized users can create and view emergency requests.

#### Acceptance Criteria

1. WHEN a user with DISPATCHER role requests POST /emergency, THE Emergency_Service SHALL process the request
2. WHEN a user with ADMIN role requests POST /emergency, THE Emergency_Service SHALL process the request
3. WHEN a user with AMBULANCE_DRIVER role requests POST /emergency, THE Emergency_Service SHALL return HTTP 403 Forbidden
4. WHEN a user with DISPATCHER role requests GET /emergency/{id}, THE Emergency_Service SHALL return the emergency details
5. WHEN a user with AMBULANCE_DRIVER role requests GET /emergency/{id}, THE Emergency_Service SHALL return the emergency details
6. WHEN a user with ADMIN role requests GET /emergency/{id}, THE Emergency_Service SHALL return the emergency details
7. WHEN a user with DISPATCHER role requests GET /emergency/status/{status}, THE Emergency_Service SHALL return matching emergencies
8. WHEN a user with ADMIN role requests GET /emergency/status/{status}, THE Emergency_Service SHALL return matching emergencies
9. WHEN a user with AMBULANCE_DRIVER role requests GET /emergency/status/{status}, THE Emergency_Service SHALL return HTTP 403 Forbidden
10. WHEN a user with DISPATCHER role requests GET /emergency/pending, THE Emergency_Service SHALL return pending emergencies
11. WHEN a user with ADMIN role requests GET /emergency/pending, THE Emergency_Service SHALL return pending emergencies
12. WHEN a user with AMBULANCE_DRIVER role requests GET /emergency/pending, THE Emergency_Service SHALL return HTTP 403 Forbidden

### Requirement 3: Enforce Ambulance Service Authorization Rules

**User Story:** As a system administrator, I want Ambulance_Service endpoints protected by role-based access control, so that only authorized users can view ambulances and update locations.

#### Acceptance Criteria

1. WHEN a user with DISPATCHER role requests GET /ambulance/available, THE Ambulance_Service SHALL return available ambulances
2. WHEN a user with ADMIN role requests GET /ambulance/available, THE Ambulance_Service SHALL return available ambulances
3. WHEN a user with AMBULANCE_DRIVER role requests GET /ambulance/available, THE Ambulance_Service SHALL return HTTP 403 Forbidden
4. WHEN a user with DISPATCHER role requests GET /ambulance/{id}, THE Ambulance_Service SHALL return the ambulance details
5. WHEN a user with AMBULANCE_DRIVER role requests GET /ambulance/{id}, THE Ambulance_Service SHALL return the ambulance details
6. WHEN a user with ADMIN role requests GET /ambulance/{id}, THE Ambulance_Service SHALL return the ambulance details
7. WHEN a user with AMBULANCE_DRIVER role requests POST /ambulance/{id}/location, THE Ambulance_Service SHALL update the ambulance location
8. WHEN a user with ADMIN role requests POST /ambulance/{id}/location, THE Ambulance_Service SHALL update the ambulance location
9. WHEN a user with DISPATCHER role requests POST /ambulance/{id}/location, THE Ambulance_Service SHALL return HTTP 403 Forbidden
10. WHEN a user with ADMIN role requests GET /diagnostic/*, THE Ambulance_Service SHALL return diagnostic information
11. WHEN a user with DISPATCHER role requests GET /diagnostic/*, THE Ambulance_Service SHALL return HTTP 403 Forbidden
12. WHEN a user with AMBULANCE_DRIVER role requests GET /diagnostic/*, THE Ambulance_Service SHALL return HTTP 403 Forbidden

### Requirement 4: Enforce Tracking Service Authorization Rules

**User Story:** As a system administrator, I want Tracking_Service endpoints protected by role-based access control, so that only authorized users can access location tracking data.

#### Acceptance Criteria

1. WHEN a user with DISPATCHER role requests GET /api/tracking/ambulance/{id}, THE Tracking_Service SHALL return the ambulance tracking data
2. WHEN a user with AMBULANCE_DRIVER role requests GET /api/tracking/ambulance/{id}, THE Tracking_Service SHALL return the ambulance tracking data
3. WHEN a user with ADMIN role requests GET /api/tracking/ambulance/{id}, THE Tracking_Service SHALL return the ambulance tracking data
4. WHEN a user with DISPATCHER role connects to WebSocket /ws/tracking, THE Tracking_Service SHALL establish the WebSocket connection
5. WHEN a user with AMBULANCE_DRIVER role connects to WebSocket /ws/tracking, THE Tracking_Service SHALL establish the WebSocket connection
6. WHEN a user with ADMIN role connects to WebSocket /ws/tracking, THE Tracking_Service SHALL establish the WebSocket connection
7. WHEN a user without DISPATCHER, AMBULANCE_DRIVER, or ADMIN role connects to WebSocket /ws/tracking, THE Tracking_Service SHALL reject the connection

### Requirement 5: Configure Method-Level Security

**User Story:** As a developer, I want to use Spring Security annotations on endpoint methods, so that I can declaratively specify authorization rules.

#### Acceptance Criteria

1. THE Emergency_Service SHALL enable method-level security with @EnableMethodSecurity annotation
2. THE Ambulance_Service SHALL enable method-level security with @EnableMethodSecurity annotation
3. THE Tracking_Service SHALL enable method-level security with @EnableMethodSecurity annotation
4. WHEN a Protected_Endpoint method is annotated with @PreAuthorize, THE Security_Context SHALL evaluate the authorization expression before method execution
5. WHEN the @PreAuthorize expression evaluates to false, THE microservice SHALL return HTTP 403 Forbidden without executing the method
6. WHEN the @PreAuthorize expression evaluates to true, THE microservice SHALL execute the method and return the result

### Requirement 6: Handle Missing or Invalid Authorization Headers

**User Story:** As a security engineer, I want the system to handle missing or invalid authorization headers gracefully, so that unauthorized requests are properly rejected.

#### Acceptance Criteria

1. WHEN an HTTP request is missing the X-User-Roles header, THE Authorization_Filter SHALL create a Security_Context with no roles
2. WHEN a Protected_Endpoint requires roles and the Security_Context has no roles, THE microservice SHALL return HTTP 403 Forbidden
3. WHEN the X-User-Roles header contains invalid role names, THE Authorization_Filter SHALL ignore the invalid roles and include only valid roles in the Security_Context
4. WHEN an HTTP request is missing the X-User-Username header, THE Authorization_Filter SHALL create a Security_Context with an anonymous username
5. THE Authorization_Filter SHALL log a warning message when the X-User-Roles header is missing from a request to a Protected_Endpoint

### Requirement 7: Provide Authorization Error Responses

**User Story:** As a client application developer, I want clear error responses when authorization fails, so that I can understand why access was denied.

#### Acceptance Criteria

1. WHEN authorization fails for a Protected_Endpoint, THE microservice SHALL return HTTP 403 Forbidden status code
2. WHEN authorization fails for a Protected_Endpoint, THE microservice SHALL include an error message in the response body
3. THE error message SHALL indicate that the user lacks the required role for the requested operation
4. THE error message SHALL NOT disclose sensitive information about system internals or other users
5. WHEN authorization fails, THE microservice SHALL log the failed authorization attempt with username and requested endpoint

### Requirement 8: Test Authorization Rules

**User Story:** As a quality assurance engineer, I want comprehensive tests for authorization rules, so that I can verify role-based access control works correctly.

#### Acceptance Criteria

1. FOR EACH Protected_Endpoint in Emergency_Service, THE test suite SHALL verify that requests with authorized roles return HTTP 200 or HTTP 201
2. FOR EACH Protected_Endpoint in Emergency_Service, THE test suite SHALL verify that requests with unauthorized roles return HTTP 403 Forbidden
3. FOR EACH Protected_Endpoint in Ambulance_Service, THE test suite SHALL verify that requests with authorized roles return HTTP 200 or HTTP 201
4. FOR EACH Protected_Endpoint in Ambulance_Service, THE test suite SHALL verify that requests with unauthorized roles return HTTP 403 Forbidden
5. FOR EACH Protected_Endpoint in Tracking_Service, THE test suite SHALL verify that requests with authorized roles return HTTP 200 or HTTP 201
6. FOR EACH Protected_Endpoint in Tracking_Service, THE test suite SHALL verify that requests with unauthorized roles return HTTP 403 Forbidden
7. THE test suite SHALL verify that requests with missing X-User-Roles header to Protected_Endpoints return HTTP 403 Forbidden
8. THE test suite SHALL verify that the ADMIN role can access all Protected_Endpoints across all services

### Requirement 9: Document Authorization Rules

**User Story:** As a developer, I want clear documentation of authorization rules for each endpoint, so that I understand which roles are required for each operation.

#### Acceptance Criteria

1. THE documentation SHALL list all Protected_Endpoints in Emergency_Service with their required roles
2. THE documentation SHALL list all Protected_Endpoints in Ambulance_Service with their required roles
3. THE documentation SHALL list all Protected_Endpoints in Tracking_Service with their required roles
4. THE documentation SHALL describe the purpose and capabilities of each role (ADMIN, DISPATCHER, AMBULANCE_DRIVER)
5. THE documentation SHALL include examples of authorization test requests for each Protected_Endpoint
6. THE documentation SHALL explain how to test authorization using curl or similar HTTP clients with X-User-Roles header

### Requirement 10: Exclude Internal Services from Authorization

**User Story:** As a system architect, I want internal services to remain accessible without authorization headers, so that service-to-service communication is not disrupted.

#### Acceptance Criteria

1. THE Dispatch_Service SHALL NOT require X-User-Roles header for internal endpoints
2. THE Notification_Service SHALL NOT require X-User-Roles header for internal endpoints
3. WHEN Dispatch_Service or Notification_Service are called by other microservices, THE services SHALL process requests without authorization checks
4. THE documentation SHALL clearly identify Dispatch_Service and Notification_Service as internal-only services
5. THE API_Gateway SHALL NOT expose Dispatch_Service or Notification_Service endpoints to external clients
