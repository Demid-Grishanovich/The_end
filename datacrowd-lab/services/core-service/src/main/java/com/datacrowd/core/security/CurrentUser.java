package com.datacrowd.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication in security context");
        }

        // 1) Самый частый случай: auth.getName() = userId
        String name = auth.getName();
        if (name != null) {
            try {
                return UUID.fromString(name);
            } catch (IllegalArgumentException ignored) {
                // fallback below
            }
        }

        // 2) Если principal = UUID / String UUID
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        if (principal instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        }

        // 3) Если principal кастомный (например, JwtPrincipal(userId, role, email))
        // Попробуем reflection на getUserId()
        try {
            var m = principal.getClass().getMethod("getUserId");
            Object value = m.invoke(principal);
            if (value instanceof UUID uuid) return uuid;
            if (value instanceof String s) return UUID.fromString(s);
        } catch (Exception ignored) {}

        throw new IllegalStateException("Cannot extract userId from Authentication. auth.getName()=" + auth.getName());
    }

    public static String requireRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("No authentication");

        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .findFirst()
                .orElse("UNKNOWN");
    }
}
