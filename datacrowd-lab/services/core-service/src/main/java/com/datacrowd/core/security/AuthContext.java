package com.datacrowd.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class AuthContext {

    private AuthContext() {}

    public static UUID getUserIdOrThrow() {
        return requireUserId();
    }

    public static UUID requireUserId() {
        return getUserId()
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated user in SecurityContext"));
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
        return Optional.empty();
    }

    public static Optional<String> getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jp) {
            return Optional.ofNullable(jp.username());
        }
        return Optional.empty();
    }

    // НОВОЕ: получить роль текущего пользователя
    public static Optional<String> getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jp) {
            return Optional.ofNullable(jp.role());
        }
        return Optional.empty();
    }
}