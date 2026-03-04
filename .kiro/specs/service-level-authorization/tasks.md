# Implementation Plan: Service-Level Authorization

## Overview

This implementation adds role-based access control (RBAC) to the Emergency Dispatch System microservices. The API Gateway already validates JWT tokens and forwards user context via HTTP headers (X-User-Username, X-User-Roles). This feature adds authorization enforcement within each microservice using Spring Security's method-level security with @PreAuthorize annotations.

The implementation covers three external-facing services (Emergency, Ambulance, Tracking) while leaving internal services (Dispatch, Notification) unchanged. The authorization system supports three roles: ADMIN (full access), DISPATCHER (emergency and dispatch operations), and AMBULANCE_DRIVER (assigned emergency operations and location updates).

## Tasks

- [ ] 1. Add Spring Security dependencies to all services
  - Add spring-boot-starter-security to emergency-service pom.xml
  - Add spring-boot-starter-security to ambulance-service pom.xml
  - Add spring-boot-starter-security to tracking-service pom.xml
  - Add spring-security-test to all three services (test scope)
  - Add jqwik dependency (version 1.8.2) to all three services for property-based testing (test scope)
  - _Requirements: 5.1, 5.2, 5.3, 8.1-8.8_

- [ ] 2. Implement HeaderAuthenticationFilter for Emergency Service
  - [ ] 2.1 Create HeaderAuthenticationFilter class in emergency-service
    - Create filter extending OncePerRequestFilter with @Component and @Order(1)
    - Extract X-User-Username header (default to "anonymous" if missing)
    - Extract X-User-Roles header and parse comma-separated values
    - Validate roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER) and filter invalid ones
    - Create UsernamePasswordAuthenticationToken with username and GrantedAuthority collection
    - Set SecurityContext with authentication object
    - Clear SecurityContext in finally block to prevent leakage
    - Log warnings when headers are missing
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.3, 6.4, 6.5_
  
  - [ ]* 2.2 Write unit tests for HeaderAuthenticationFilter
    - Test with valid username and roles headers
    - Test with missing username header (should default to "anonymous")
    - Test with missing roles header (should create empty authorities)
    - Test with invalid role names (should filter them out)
    - Test with mix of valid and invalid roles
    - Test SecurityContext is populated correctly
    - Test SecurityContext is cleared after request
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 6.1, 6.3, 6.4_
  
  - [ ]* 2.3 Write property test for HeaderAuthenticationFilter
    - **Property 1: Header to SecurityContext Round Trip**
    - **Validates: Requirements 1.1, 1.2, 1.3**
    - Generate random usernames and valid role combinations
    - Verify SecurityContext contains exact username and roles with ROLE_ prefix
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [ ]* 2.4 Write property test for invalid role filtering
    - **Property 2: Invalid Roles Filtered**
    - **Validates: Requirements 6.3**
    - Generate mix of valid and invalid roles
    - Verify only valid roles appear in SecurityContext
    - _Requirements: 6.3_

- [ ] 3. Configure Spring Security for Emergency Service
  - [ ] 3.1 Create SecurityConfiguration class
    - Add @Configuration, @EnableWebSecurity, @EnableMethodSecurity(prePostEnabled = true)
    - Create SecurityFilterChain bean
    - Disable CSRF (stateless JWT authentication)
    - Permit all requests to /actuator/health and /actuator/info
    - Set authorizeHttpRequests to permitAll (authorization via @PreAuthorize)
    - Register HeaderAuthenticationFilter before UsernamePasswordAuthenticationFilter
    - Configure CustomAccessDeniedHandler for exception handling
    - _Requirements: 5.1, 5.4_
  
  - [ ] 3.2 Create CustomAccessDeniedHandler class
    - Implement AccessDeniedHandler interface
    - Extract username from SecurityContext
    - Log warning with username, HTTP method, and request URI
    - Return HTTP 403 with JSON error response containing error, message, and status fields
    - Ensure message includes username but no sensitive system information
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [ ]* 3.3 Write unit tests for CustomAccessDeniedHandler
    - Test 403 status code is returned
    - Test JSON response contains error, message, and status fields
    - Test message includes username
    - Test message indicates missing required role
    - Test warning is logged with username and endpoint
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

- [ ] 4. Add @PreAuthorize annotations to Emergency Service controllers
  - [ ] 4.1 Add authorization to EmergencyController
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") to createEmergency (POST /emergency)
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')") to getEmergency (GET /emergency/{id})
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") to getEmergenciesByStatus (GET /emergency/status/{status})
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") to getPendingEmergencies (GET /emergency/pending)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12_
  
  - [ ]* 4.2 Write integration tests for Emergency Service authorization
    - Test DISPATCHER can POST /emergency (should return 201)
    - Test ADMIN can POST /emergency (should return 201)
    - Test AMBULANCE_DRIVER cannot POST /emergency (should return 403)
    - Test DISPATCHER can GET /emergency/{id} (should return 200)
    - Test AMBULANCE_DRIVER can GET /emergency/{id} (should return 200)
    - Test ADMIN can GET /emergency/{id} (should return 200)
    - Test DISPATCHER can GET /emergency/status/{status} (should return 200)
    - Test AMBULANCE_DRIVER cannot GET /emergency/status/{status} (should return 403)
    - Test missing X-User-Roles header returns 403
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 8.1, 8.2, 8.7_
  
  - [ ]* 4.3 Write property test for Emergency Service authorization matrix
    - **Property 3: Emergency Service Authorization Matrix**
    - **Validates: Requirements 2.1-2.12**
    - Generate all Emergency Service endpoints and all roles
    - Verify authorization outcome matches defined matrix
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12_

- [ ] 5. Checkpoint - Verify Emergency Service authorization
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement HeaderAuthenticationFilter for Ambulance Service
  - [ ] 6.1 Create HeaderAuthenticationFilter class in ambulance-service
    - Create filter extending OncePerRequestFilter with @Component and @Order(1)
    - Extract X-User-Username header (default to "anonymous" if missing)
    - Extract X-User-Roles header and parse comma-separated values
    - Validate roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER) and filter invalid ones
    - Create UsernamePasswordAuthenticationToken with username and GrantedAuthority collection
    - Set SecurityContext with authentication object
    - Clear SecurityContext in finally block
    - Log warnings when headers are missing
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.3, 6.4, 6.5_
  
  - [ ]* 6.2 Write unit tests for HeaderAuthenticationFilter
    - Test with valid username and roles headers
    - Test with missing username header
    - Test with missing roles header
    - Test with invalid role names
    - Test SecurityContext population and cleanup
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 6.1, 6.3, 6.4_

- [ ] 7. Configure Spring Security for Ambulance Service
  - [ ] 7.1 Create SecurityConfiguration class
    - Add @Configuration, @EnableWebSecurity, @EnableMethodSecurity(prePostEnabled = true)
    - Create SecurityFilterChain bean with CSRF disabled
    - Permit health endpoints, set authorizeHttpRequests to permitAll
    - Register HeaderAuthenticationFilter
    - Configure CustomAccessDeniedHandler
    - _Requirements: 5.2, 5.4_
  
  - [ ] 7.2 Create CustomAccessDeniedHandler class
    - Implement AccessDeniedHandler interface
    - Log authorization failures with username and endpoint
    - Return HTTP 403 with JSON error response
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 8. Add @PreAuthorize annotations to Ambulance Service controllers
  - [ ] 8.1 Add authorization to AmbulanceController
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") to getAvailableAmbulances (GET /ambulance/available)
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')") to getAmbulance (GET /ambulance/{id})
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'AMBULANCE_DRIVER')") to updateLocation (POST /ambulance/{id}/location)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_
  
  - [ ] 8.2 Add authorization to DiagnosticController
    - Add @PreAuthorize("hasRole('ADMIN')") to initializeFleet (POST /diagnostic/init-fleet)
    - Add @PreAuthorize("hasRole('ADMIN')") to getFleetStatus (GET /diagnostic/fleet-status)
    - Add @PreAuthorize("hasRole('ADMIN')") to any other diagnostic endpoints
    - _Requirements: 3.10, 3.11, 3.12_
  
  - [ ]* 8.3 Write integration tests for Ambulance Service authorization
    - Test DISPATCHER can GET /ambulance/available (should return 200)
    - Test AMBULANCE_DRIVER cannot GET /ambulance/available (should return 403)
    - Test AMBULANCE_DRIVER can POST /ambulance/{id}/location (should return 200)
    - Test DISPATCHER cannot POST /ambulance/{id}/location (should return 403)
    - Test ADMIN can GET /diagnostic/fleet-status (should return 200)
    - Test DISPATCHER cannot GET /diagnostic/fleet-status (should return 403)
    - Test AMBULANCE_DRIVER cannot GET /diagnostic/fleet-status (should return 403)
    - _Requirements: 3.1, 3.2, 3.3, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12, 8.3, 8.4_
  
  - [ ]* 8.4 Write property test for Ambulance Service authorization matrix
    - **Property 4: Ambulance Service Authorization Matrix**
    - **Validates: Requirements 3.1-3.12**
    - Generate all Ambulance Service endpoints and all roles
    - Verify authorization outcome matches defined matrix
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12_

- [ ] 9. Checkpoint - Verify Ambulance Service authorization
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement HeaderAuthenticationFilter for Tracking Service
  - [ ] 10.1 Create HeaderAuthenticationFilter class in tracking-service
    - Create filter extending OncePerRequestFilter with @Component and @Order(1)
    - Extract X-User-Username header (default to "anonymous" if missing)
    - Extract X-User-Roles header and parse comma-separated values
    - Validate roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER) and filter invalid ones
    - Create UsernamePasswordAuthenticationToken with username and GrantedAuthority collection
    - Set SecurityContext with authentication object
    - Clear SecurityContext in finally block
    - Log warnings when headers are missing
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.3, 6.4, 6.5_
  
  - [ ]* 10.2 Write unit tests for HeaderAuthenticationFilter
    - Test with valid username and roles headers
    - Test with missing headers
    - Test with invalid role names
    - Test SecurityContext population and cleanup
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 6.1, 6.3, 6.4_

- [ ] 11. Configure Spring Security for Tracking Service
  - [ ] 11.1 Create SecurityConfiguration class
    - Add @Configuration, @EnableWebSecurity, @EnableMethodSecurity(prePostEnabled = true)
    - Create SecurityFilterChain bean with CSRF disabled
    - Permit health endpoints, set authorizeHttpRequests to permitAll
    - Register HeaderAuthenticationFilter
    - Configure CustomAccessDeniedHandler
    - _Requirements: 5.3, 5.4_
  
  - [ ] 11.2 Create CustomAccessDeniedHandler class
    - Implement AccessDeniedHandler interface
    - Log authorization failures with username and endpoint
    - Return HTTP 403 with JSON error response
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 12. Add @PreAuthorize annotations to Tracking Service REST controllers
  - [ ] 12.1 Add authorization to TrackingController
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')") to getAmbulanceLocation (GET /api/tracking/ambulance/{id})
    - Add @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')") to getAllAmbulances (GET /api/tracking/ambulances)
    - _Requirements: 4.1, 4.2, 4.3_
  
  - [ ]* 12.2 Write integration tests for Tracking Service REST authorization
    - Test DISPATCHER can GET /api/tracking/ambulance/{id} (should return 200)
    - Test AMBULANCE_DRIVER can GET /api/tracking/ambulance/{id} (should return 200)
    - Test ADMIN can GET /api/tracking/ambulance/{id} (should return 200)
    - Test missing X-User-Roles header returns 403
    - _Requirements: 4.1, 4.2, 4.3, 8.5, 8.6, 8.7_

- [ ] 13. Implement WebSocket authorization for Tracking Service
  - [ ] 13.1 Create WebSocketAuthInterceptor class
    - Implement HandshakeInterceptor interface
    - In beforeHandshake method, extract X-User-Roles header from ServletServerHttpRequest
    - Parse comma-separated roles and validate against allowed roles (ADMIN, DISPATCHER, AMBULANCE_DRIVER)
    - If header missing or no valid roles, set response status to 403 and return false
    - If valid roles found, store roles in attributes and return true
    - Log warnings for rejected connections and info for authorized connections
    - _Requirements: 4.4, 4.5, 4.6, 4.7_
  
  - [ ] 13.2 Register WebSocketAuthInterceptor in WebSocketConfig
    - Locate existing WebSocketConfig class
    - Add WebSocketAuthInterceptor to the endpoint registration via addInterceptors()
    - _Requirements: 4.4, 4.5, 4.6, 4.7_
  
  - [ ]* 13.3 Write integration tests for WebSocket authorization
    - Test DISPATCHER can connect to /ws/tracking (should succeed)
    - Test AMBULANCE_DRIVER can connect to /ws/tracking (should succeed)
    - Test ADMIN can connect to /ws/tracking (should succeed)
    - Test connection without X-User-Roles header is rejected (should return 403)
    - Test connection with invalid roles is rejected (should return 403)
    - _Requirements: 4.4, 4.5, 4.6, 4.7, 8.5, 8.6_
  
  - [ ]* 13.4 Write property test for Tracking Service authorization matrix
    - **Property 5: Tracking Service Authorization Matrix**
    - **Validates: Requirements 4.1-4.7**
    - Generate all Tracking Service endpoints (REST and WebSocket) and all roles
    - Verify authorization outcome matches defined matrix
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

- [ ] 14. Checkpoint - Verify Tracking Service authorization
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Write cross-service property-based tests
  - [ ]* 15.1 Write property test for authorization check precedes method execution
    - **Property 6: Authorization Check Precedes Method Execution**
    - **Validates: Requirements 5.4, 5.5**
    - For any protected endpoint, when authorization fails, verify method is not executed and 403 is returned
    - _Requirements: 5.4, 5.5_
  
  - [ ]* 15.2 Write property test for successful authorization executes method
    - **Property 7: Successful Authorization Executes Method**
    - **Validates: Requirements 5.6**
    - For any protected endpoint, when authorization succeeds, verify method executes and returns normal response
    - _Requirements: 5.6_
  
  - [ ]* 15.3 Write property test for missing roles header
    - **Property 8: Missing Roles Header Results in Empty Authorities**
    - **Validates: Requirements 1.4, 6.1, 6.2**
    - For any request missing X-User-Roles header, verify SecurityContext has empty authorities and protected endpoints return 403
    - _Requirements: 1.4, 6.1, 6.2_
  
  - [ ]* 15.4 Write property test for authorization failure response format
    - **Property 9: Authorization Failure Response Format**
    - **Validates: Requirements 7.1, 7.2, 7.3**
    - For any protected endpoint where authorization fails, verify response has status 403, JSON body with error/message/status fields, and message indicates missing role
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [ ]* 15.5 Write property test for authorization failures are logged
    - **Property 10: Authorization Failures Are Logged**
    - **Validates: Requirements 7.5**
    - For any protected endpoint where authorization fails, verify warning log entry contains username and endpoint path
    - _Requirements: 7.5_
  
  - [ ]* 15.6 Write property test for ADMIN universal access
    - **Property 12: ADMIN Role Has Universal Access**
    - **Validates: Requirements 2.1, 2.6, 2.7, 2.8, 3.2, 3.6, 3.8, 3.10, 4.3, 4.6**
    - For any protected endpoint across all services, verify user with ADMIN role can access successfully
    - _Requirements: 2.1, 2.6, 2.7, 2.8, 3.2, 3.6, 3.8, 3.10, 4.3, 4.6_

- [ ] 16. Verify internal services remain unchanged
  - [ ] 16.1 Verify Dispatch Service has no authorization
    - Confirm dispatch-service pom.xml does not include spring-boot-starter-security
    - Confirm no SecurityConfiguration or filters exist in dispatch-service
    - _Requirements: 10.1, 10.3, 10.4_
  
  - [ ] 16.2 Verify Notification Service has no authorization
    - Confirm notification-service pom.xml does not include spring-boot-starter-security
    - Confirm no SecurityConfiguration or filters exist in notification-service
    - _Requirements: 10.2, 10.3, 10.4_
  
  - [ ]* 16.3 Write property test for internal services require no authorization
    - **Property 11: Internal Services Require No Authorization**
    - **Validates: Requirements 10.1, 10.2, 10.3**
    - For any endpoint in Dispatch or Notification services, verify requests succeed regardless of X-User-Roles header presence or content
    - _Requirements: 10.1, 10.2, 10.3_

- [ ] 17. Create authorization documentation
  - [ ] 17.1 Create AUTHORIZATION.md file in project root
    - Document all protected endpoints with required roles for Emergency Service
    - Document all protected endpoints with required roles for Ambulance Service
    - Document all protected endpoints with required roles for Tracking Service
    - Describe ADMIN, DISPATCHER, and AMBULANCE_DRIVER role capabilities
    - Include curl examples for testing each endpoint with different roles
    - Explain how to test authorization using X-User-Username and X-User-Roles headers
    - Document that Dispatch and Notification services are internal-only with no authorization
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 10.4_

- [ ] 18. Final checkpoint - Verify complete authorization implementation
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at service boundaries
- Property tests validate universal correctness properties using jqwik
- Unit tests validate specific examples and edge cases
- The HeaderAuthenticationFilter implementation is identical across all three services
- Internal services (Dispatch, Notification) are explicitly excluded from authorization changes
- All authorization is enforced via @PreAuthorize annotations for declarative security
- The CustomAccessDeniedHandler provides consistent error responses across all services
