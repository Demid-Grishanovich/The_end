package com.datacrowd.auth.api;

import com.datacrowd.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.datacrowd.auth.api.AuthDtos.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Обновление access JWT по текущему access JWT (пока он валиден).
     * Authorization: Bearer <token>
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return authService.refreshFromAccessToken(authorization);
    }

    /**
     * Logout (JWT stateless): клиент удаляет токен. Мы возвращаем 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }
}
