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
    void register_shouldCreateUserAndReturnToken() {
        var req = new RegisterRequest("demo", "demo@example.com", "pass");

        when(users.findByUsername("demo")).thenReturn(Optional.empty());
        when(users.findByEmail("demo@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("hashed");

        // emulate JPA prePersist behavior (id + status default)
        when(users.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            if (u.getStatus() == null) u.setStatus(UserEntity.Status.ACTIVE);
            return u;
        });

        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("jwt-token");

        AuthResponse res = authService.register(req);

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.userId()).isNotBlank();
        assertThat(res.role()).isEqualTo("WORKER");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("demo");
        assertThat(saved.getEmail()).isEqualTo("demo@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo("WORKER");
        assertThat(saved.getStatus()).isEqualTo(UserEntity.Status.ACTIVE);
    }

    @Test
    void register_shouldFail_whenUsernameAlreadyExists() {
        var req = new RegisterRequest("demo", "demo@example.com", "pass");
        when(users.findByUsername("demo")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void register_shouldFail_whenEmailAlreadyExists() {
        var req = new RegisterRequest("demo", "demo@example.com", "pass");
        when(users.findByUsername("demo")).thenReturn(Optional.empty());
        when(users.findByEmail("demo@example.com")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
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
