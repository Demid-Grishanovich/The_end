package com.datacrowd.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final SecretKey key;

    // Важно: секрет должен быть минимум 32 байта для HS256
    public JwtService(@Value("${JWT_SECRET:dc_jwt_secret_change_me_please_32_bytes_minimum!}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Генерация access JWT */
    public String generate(String userId, String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(Map.of("userId", userId, "role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60 * 60 * 24))) // 24h (поменяешь позже на 15m)
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
