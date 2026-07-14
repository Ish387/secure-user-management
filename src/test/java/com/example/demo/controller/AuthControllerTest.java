package com.example.demo.controller;

import com.example.demo.config.AppConfig;
import com.example.demo.config.SecurityConfig;

import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.UserRequestDTO;
import com.example.demo.dto.UserResponseDTO;
import com.example.demo.exception.DuplicateResourceException;
import com.example.demo.security.JwtAuthEntryPoint;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.AuthService;
import com.example.demo.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthEntryPoint.class, AppConfig.class})
@TestPropertySource(properties = "rate-limit.auth.max-requests=1000")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;



    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void register_Returns201() throws Exception {
        UserRequestDTO request = new UserRequestDTO("John Doe", "john@example.com", "pass123");
        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L).name("John Doe").email("john@example.com")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(authService.register(any(UserRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void register_DuplicateEmail_Returns409() throws Exception {
        UserRequestDTO request = new UserRequestDTO("John Doe", "john@example.com", "pass123");

        when(authService.register(any(UserRequestDTO.class)))
                .thenThrow(new DuplicateResourceException("User", "email", "john@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_Returns200_WithToken() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("john@example.com", "pass123");
        AuthResponseDTO authResponse = AuthResponseDTO.builder().token("jwt-token").build();

        when(authService.login(any(LoginRequestDTO.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_BadCredentials_Returns401() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("john@example.com", "wrongpass");

        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_InvalidInput_Returns400() throws Exception {
        UserRequestDTO request = new UserRequestDTO("", "not-email", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_BadCredentials_SinhalaAcceptLanguage_ReturnsLocalizedMessage() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("john@example.com", "wrongpass");

        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .header("Accept-Language", "si")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("වලංගු නොවන විද්‍යුත් තැපෑල හෝ මුරපදය"));
    }

    @Test
    void register_InvalidInput_UnsupportedAcceptLanguage_FallsBackToEnglish() throws Exception {
        UserRequestDTO request = new UserRequestDTO("", "not-email", "");

        mockMvc.perform(post("/api/auth/register")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("required")));
    }
}
