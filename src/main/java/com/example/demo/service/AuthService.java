package com.example.demo.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.UserRequestDTO;
import com.example.demo.dto.UserResponseDTO;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateResourceException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for authentication operations (register and login).
 * Handles password hashing, credential validation, and JWT token generation.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    /**
     * Register a new user — validates email uniqueness, hashes password, saves to DB.
     */
    @Transactional
    public UserResponseDTO register(UserRequestDTO requestDTO) {
        if (userRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("User", "email", requestDTO.getEmail());
        }

        User user = User.builder()
            .name(requestDTO.getName())
            .email(requestDTO.getEmail())
            .password(passwordEncoder.encode(requestDTO.getPassword()))
            .build();

        User savedUser = userRepository.save(user);

        return UserResponseDTO.builder()
            .id(savedUser.getId())
            .name(savedUser.getName())
            .email(savedUser.getEmail())
            .createdAt(savedUser.getCreatedAt())
            .updatedAt(savedUser.getUpdatedAt())
            .build();
    }

    /**
     * Authenticate user credentials and return a JWT token.
     */
    public AuthResponseDTO login(LoginRequestDTO loginRequest) {
        // Authenticate via Spring Security's AuthenticationManager
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
            )
        );

        // If authentication succeeds, fetch user and generate token
        User user = userRepository.findByEmail(loginRequest.getEmail())
            .orElseThrow(); // Should never happen after successful auth

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        return AuthResponseDTO.builder()
            .token(token)
            .build();
    }
}
