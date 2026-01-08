package com.datacrowd.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final SecretKey key;

    @Value("${app.jwt.ttl-minutes:60}")
    private long ttlMinutes;

    // Важно: секрет должен быть минимум 32 байта для HS256
    public JwtService(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET env var is not set. Set it in .env / docker-compose / IDE env.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET is too short. Use at least 32 characters for HS256.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Генерация access JWT */
    public String generate(String userId, String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(Map.of("userId", userId, "role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60))) // 24h (поменяешь позже на 15m)
                .signWith(key)
                .compact();
    }

    /** Валидация + разбор JWT */
    public Claims parseAndValidate(String token) throws JwtException {
        // JJWT 0.12.x API
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


}
