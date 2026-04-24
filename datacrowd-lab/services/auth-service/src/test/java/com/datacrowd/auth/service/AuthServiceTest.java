package com.datacrowd.auth.service;

import com.datacrowd.auth.api.AuthDtos.AuthResponse;
import com.datacrowd.auth.api.AuthDtos.LoginRequest;
import com.datacrowd.auth.api.AuthDtos.RegisterRequest;
import com.datacrowd.auth.jwt.JwtService;
import com.datacrowd.auth.user.UserEntity;
import com.datacrowd.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository  userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService      jwtService;
    private AuthService     authService;

    @BeforeEach
    void setUp() {
        userRepository  = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService      = mock(JwtService.class);
        authService     = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void login_returnsToken_whenCredentialsValid() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("test@test.com");
        user.setPasswordHash("$2a$hash");
        user.setRole("WORKER");
        user.setUsername("testuser");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Test1234!", "$2a$hash")).thenReturn(true);
        when(jwtService.generate(userId.toString(), "test@test.com", "WORKER"))
                .thenReturn("jwt-token");

        // ИСПРАВЛЕНО: records создаются через конструктор
        AuthResponse response = authService.login(new LoginRequest("test@test.com", "Test1234!"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("WORKER");
    }

    @Test
    void login_throwsException_whenPasswordInvalid() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setPasswordHash("$2a$hash");
        user.setRole("WORKER");
        user.setUsername("testuser");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("test@test.com", "wrongpass")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_throwsException_whenUserNotFound() {
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("notfound@test.com", "anypass")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_throwsException_whenEmailAlreadyExists() {
        UserEntity existing = new UserEntity();
        existing.setId(UUID.randomUUID());
        existing.setEmail("exists@test.com");

        when(userRepository.findByEmail("exists@test.com"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("newuser", "exists@test.com", "Test1234!"), "WORKER"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}