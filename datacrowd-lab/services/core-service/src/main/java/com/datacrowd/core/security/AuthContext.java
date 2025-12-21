package com.datacrowd.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class AuthContext {

    private AuthContext() {}

    public static UUID requireUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("User is not authenticated"));
    }

    public static Optional<UUID> getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal == null) return Optional.empty();

        if (principal instanceof JwtPrincipal p) {
            try {
                return Optional.of(UUID.fromString(p.userId()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public static Optional<String> getRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal p) {
            return Optional.ofNullable(p.role());
        }

        return Optional.empty();
    }

    public static Optional<String> getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal p) {
            return Optional.ofNullable(p.username());
        }

        return Optional.empty();
    }
}
