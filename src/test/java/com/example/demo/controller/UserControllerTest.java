package com.example.demo.controller;

import com.example.demo.config.AppConfig;
import com.example.demo.config.SecurityConfig;

import com.example.demo.dto.UserRequestDTO;
import com.example.demo.dto.UserResponseDTO;

import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.security.JwtAuthEntryPoint;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.CustomUserDetailsService;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthEntryPoint.class, AppConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;



    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private UserResponseDTO createSampleResponse() {
        return UserResponseDTO.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser
    void createUser_Returns201() throws Exception {
        UserRequestDTO request = new UserRequestDTO("John Doe", "john@example.com", "pass123");
        when(userService.createUser(any(UserRequestDTO.class))).thenReturn(createSampleResponse());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser
    void getAllUsers_Returns200() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(createSampleResponse()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }

    @Test
    @WithMockUser
    void getUserById_Returns200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(createSampleResponse());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @WithMockUser
    void getUserById_NotFound_Returns404() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new ResourceNotFoundException("User", "id", 999L));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void updateUser_Returns200() throws Exception {
        UserRequestDTO request = new UserRequestDTO("Updated Name", "john@example.com", "newpass");
        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L).name("Updated Name").email("john@example.com")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(userService.updateUser(eq(1L), any(UserRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockUser
    void deleteUser_Returns204() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void accessWithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getUserById_NotFound_SinhalaAcceptLanguage_ReturnsLocalizedMessage() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new ResourceNotFoundException("User", "id", 999L));

        mockMvc.perform(get("/api/users/999").header("Accept-Language", "si"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("පරිශීලකයා")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("හමු නොවීය")));
    }
}
