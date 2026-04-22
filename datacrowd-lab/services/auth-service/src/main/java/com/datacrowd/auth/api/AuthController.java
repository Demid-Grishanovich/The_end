package com.datacrowd.auth.api;

import com.datacrowd.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.datacrowd.auth.api.AuthDtos.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.admin-key:demo-admin-key}")
    private String configuredAdminKey;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req, "WORKER"));
    }

    @PostMapping("/register-client")
    public ResponseEntity<AuthResponse> registerClient(
            @Valid @RequestBody RegisterRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey) {

        if (adminKey == null || !configuredAdminKey.equals(adminKey)) {
            throw new IllegalArgumentException("Forbidden: valid X-Admin-Key header required");
        }

        return ResponseEntity.ok(authService.register(req, "CLIENT"));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return authService.refreshFromAccessToken(authorization);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }
}