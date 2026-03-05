package com.vivek.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.auth.dto.AuthResponse;
import com.vivek.auth.dto.LoginRequest;
import com.vivek.auth.dto.RefreshTokenRequest;
import com.vivek.auth.dto.RegisterRequest;
import com.vivek.auth.entity.User;
import com.vivek.auth.repository.RefreshTokenRepository;
import com.vivek.auth.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUser() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("Test@1234");
        request.setRole("DISPATCHER");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.roles").value("DISPATCHER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        // Verify user was saved in database
        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getRoles()).isEqualTo("DISPATCHER");
        assertThat(user.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should reject duplicate username")
    void shouldRejectDuplicateUsername() throws Exception {
        // Create first user
        RegisterRequest request1 = new RegisterRequest();
        request1.setUsername("duplicate");
        request1.setEmail("user1@example.com");
        request1.setPassword("Pass@1234");
        request1.setRole("ADMIN");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Try to create second user with same username
        RegisterRequest request2 = new RegisterRequest();
        request2.setUsername("duplicate");
        request2.setEmail("user2@example.com");
        request2.setPassword("Pass@1234");
        request2.setRole("DISPATCHER");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() throws Exception {
        // Register user first
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("logintest");
        registerRequest.setEmail("login@example.com");
        registerRequest.setPassword("Login@1234");
        registerRequest.setRole("AMBULANCE_DRIVER");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // Now login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("logintest");
        loginRequest.setPassword("Login@1234");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value("logintest"))
                .andExpect(jsonPath("$.roles").value("AMBULANCE_DRIVER"));
    }

    @Test
    @DisplayName("Should reject login with invalid password")
    void shouldRejectInvalidPassword() throws Exception {
        // Register user
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("passtest");
        registerRequest.setEmail("pass@example.com");
        registerRequest.setPassword("Correct@1234");
        registerRequest.setRole("DISPATCHER");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // Try login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("passtest");
        loginRequest.setPassword("Wrong@1234");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Should refresh token successfully and rotate refresh token")
    void shouldRefreshTokenSuccessfully() throws Exception {
        // Register and get tokens
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("refreshtest");
        registerRequest.setEmail("refresh@example.com");
        registerRequest.setPassword("Refresh@1234");
        registerRequest.setRole("ADMIN");

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // Use refresh token to get new access token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(authResponse.getRefreshToken());

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value("refreshtest"))
                .andReturn();

        AuthResponse refreshResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        assertThat(refreshResponse.getRefreshToken()).isNotEqualTo(authResponse.getRefreshToken());

        // Old refresh token must fail after rotation
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should validate valid token")
    void shouldValidateValidToken() throws Exception {
        // Register and get token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("validatetest");
        registerRequest.setEmail("validate@example.com");
        registerRequest.setPassword("Validate@1234");
        registerRequest.setRole("DISPATCHER");

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // Validate the token
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", "Bearer " + authResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("validatetest"))
                .andExpect(jsonPath("$.roles").value("DISPATCHER"))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    @DisplayName("Should reject invalid token")
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should logout successfully")
    void shouldLogoutSuccessfully() throws Exception {
        // Register and get tokens
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("logouttest");
        registerRequest.setEmail("logout@example.com");
        registerRequest.setPassword("Logout@1234");
        registerRequest.setRole("ADMIN");

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // Logout
        RefreshTokenRequest logoutRequest = new RefreshTokenRequest();
        logoutRequest.setRefreshToken(authResponse.getRefreshToken());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Try to use the refresh token again - should fail
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should validate password requirements")
    void shouldValidatePasswordRequirements() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("weakpass");
        request.setEmail("weak@example.com");
        request.setPassword("123"); // Too short
        request.setRole("DISPATCHER");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate username format")
    void shouldValidateUsernameFormat() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("invalid user!"); // Contains spaces and special chars
        request.setEmail("test@example.com");
        request.setPassword("Test@1234");
        request.setRole("DISPATCHER");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate role values")
    void shouldValidateRoleValues() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("invalidrole");
        request.setEmail("role@example.com");
        request.setPassword("Test@1234");
        request.setRole("INVALID_ROLE");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
