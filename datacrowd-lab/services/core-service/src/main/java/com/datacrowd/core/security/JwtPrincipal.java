package com.datacrowd.core.security;

public record JwtPrincipal(
        String userId,
        String username,
        String role
) {}
