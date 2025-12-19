package com.datacrowd.auth.service;

import com.datacrowd.auth.jwt.JwtService;
import com.datacrowd.auth.user.UserEntity;
import com.datacrowd.auth.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.datacrowd.auth.api.AuthDtos.*;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        users.findByUsername(request.username())
                .ifPresent(u -> { throw new IllegalArgumentException("username taken"); });

        users.findByEmail(request.email())
                .ifPresent(u -> { throw new IllegalArgumentException("email taken"); });

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("WORKER");

        // если у тебя есть status в entity — можно тут поставить ACTIVE
        // user.setStatus(UserEntity.Status.ACTIVE);

        UserEntity saved = users.save(user);

        String userId = saved.getId().toString();
        String token = jwtService.generate(userId, saved.getEmail(), saved.getRole());
        return new AuthResponse(token, userId, saved.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = users.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("bad credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("bad credentials");
        }

        String userId = user.getId().toString();
        String token = jwtService.generate(userId, user.getEmail(), user.getRole());
        return new AuthResponse(token, userId, user.getRole());
    }

    /**
     * "Refresh" без отдельного refresh-token:
     * принимает текущий access JWT (который ещё валиден) и перевыпускает новый.
     */
    public AuthResponse refreshFromAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing Authorization: Bearer <token>");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        final Claims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (JwtException e) {
            throw new SecurityException("Invalid token");
        }

        String subject = claims.getSubject(); // у тебя это email
        Object userIdObj = claims.get("userId");
        Object roleObj = claims.get("role");

        if (subject == null || userIdObj == null || roleObj == null) {
            throw new SecurityException("Token missing required claims");
        }

        String userId = userIdObj.toString();
        String role = roleObj.toString();

        String newToken = jwtService.generate(userId, subject, role);
        return new AuthResponse(newToken, userId, role);
    }

    /**
     * Logout для JWT без хранения refresh/blacklist — это noop.
     * Клиент просто удаляет токен.
     */
    public void logout() {
        // noop
    }
}
