package com.datacrowd.auth.service;

import com.datacrowd.auth.api.AuthDtos.AuthResponse;
import com.datacrowd.auth.api.AuthDtos.LoginRequest;
import com.datacrowd.auth.api.AuthDtos.RegisterRequest;
import com.datacrowd.auth.jwt.JwtService;
import com.datacrowd.auth.user.UserEntity;
import com.datacrowd.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository users;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);

        authService = new AuthService(users, passwordEncoder, jwtService);
    }





    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setEmail("demo@example.com");
        u.setUsername("demo");
        u.setPasswordHash("hashed");
        u.setRole("WORKER");
        u.setStatus(UserEntity.Status.ACTIVE);

        when(users.findByEmail("demo@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("jwt-token");

        var req = new LoginRequest("demo@example.com", "pass");
        AuthResponse res = authService.login(req);

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.userId()).isEqualTo(u.getId().toString());
        assertThat(res.role()).isEqualTo("WORKER");
    }

    @Test
    void login_shouldFail_whenUserNotFound() {
        when(users.findByEmail("demo@example.com")).thenReturn(Optional.empty());

        var req = new LoginRequest("demo@example.com", "pass");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad credentials");
    }

    @Test
    void login_shouldFail_whenPasswordInvalid() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setEmail("demo@example.com");
        u.setUsername("demo");
        u.setPasswordHash("hashed");
        u.setRole("WORKER");
        u.setStatus(UserEntity.Status.ACTIVE);

        when(users.findByEmail("demo@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(false);

        var req = new LoginRequest("demo@example.com", "pass");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad credentials");
    }
}
