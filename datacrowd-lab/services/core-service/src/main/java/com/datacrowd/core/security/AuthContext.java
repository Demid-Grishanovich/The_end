package com.datacrowd.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Small helper to extract user info from Spring Security context.
 *
 * In this project Core-service authenticates requests via JwtAuthenticationFilter which sets JwtPrincipal as principal.
 */
public final class AuthContext {

    private AuthContext() {}

    /** Backward-compatible alias (controllers already use this). */
    public static UUID getUserIdOrThrow() {
        return requireUserId();
    }

    public static UUID requireUserId() {
        return getUserId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user in SecurityContext"));
    }

    public static Optional<UUID> getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jp) {
            try {
                return Optional.of(UUID.fromString(jp.userId()));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }

        // Sometimes principal can be just a username string (depends on security setup)
        return Optional.empty();
    }

    public static Optional<String> getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jp) return Optional.ofNullable(jp.username());

        return Optional.empty();
    }
}
