package com.datacrowd.auth.service;

import com.datacrowd.auth.jwt.JwtService;
import com.datacrowd.auth.user.UserEntity;
import com.datacrowd.auth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static com.datacrowd.auth.api.AuthDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest("testuser", "test@mail.com", "123456");

        when(users.findByUsername("testuser")).thenReturn(Optional.empty());
        when(users.findByEmail("test@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("hashed");

        UserEntity saved = new UserEntity();
        saved.setId(UUID.randomUUID());
        saved.setUsername("testuser");
        saved.setEmail("test@mail.com");
        saved.setRole("WORKER");
        saved.setPasswordHash("hashed");

        when(users.save(any(UserEntity.class))).thenReturn(saved);
        when(jwtService.generate(any(), any(), any())).thenReturn("token123");

        AuthResponse resp = authService.register(req);

        assertEquals("token123", resp.token());
        assertNotNull(resp.userId());
        assertEquals("WORKER", resp.role());

        verify(users).save(any(UserEntity.class));
        verify(jwtService).generate(any(), eq("test@mail.com"), eq("WORKER"));
    }

    @Test
    void register_fail_usernameTaken() {
        RegisterRequest req = new RegisterRequest("testuser", "test@mail.com", "123456");

        when(users.findByUsername("testuser")).thenReturn(Optional.of(new UserEntity()));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(req)
        );

        assertEquals("username taken", ex.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void register_fail_emailTaken() {
        RegisterRequest req = new RegisterRequest("testuser", "test@mail.com", "123456");

        when(users.findByUsername("testuser")).thenReturn(Optional.empty());
        when(users.findByEmail("test@mail.com")).thenReturn(Optional.of(new UserEntity()));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(req)
        );

        assertEquals("email taken", ex.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequest req = new LoginRequest("test@mail.com", "123456");

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole("WORKER");
        user.setPasswordHash("hashed");

        when(users.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);
        when(jwtService.generate(any(), any(), any())).thenReturn("token123");

        AuthResponse resp = authService.login(req);

        assertEquals("token123", resp.token());
        assertEquals("WORKER", resp.role());
        assertNotNull(resp.userId());
    }

    @Test
    void login_fail_badCredentials_whenNoUser() {
        LoginRequest req = new LoginRequest("no@mail.com", "123");

        when(users.findByEmail("no@mail.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(req)
        );

        assertEquals("bad credentials", ex.getMessage());
    }

    @Test
    void login_fail_badCredentials_whenPasswordMismatch() {
        LoginRequest req = new LoginRequest("test@mail.com", "wrong");

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole("WORKER");
        user.setPasswordHash("hashed");

        when(users.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(req)
        );

        assertEquals("bad credentials", ex.getMessage());
    }
}
