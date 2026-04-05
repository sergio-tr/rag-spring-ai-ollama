package com.uniovi.rag.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    static final String CLAIM_TYPE = "typ";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(
            @Value("${rag.jwt.secret}") String secret,
            @Value("${rag.jwt.issuer:rag-service}") String issuer,
            @Value("${rag.jwt.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${rag.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("rag.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String createAccessToken(UUID userId, String email, String roleName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", roleName)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public RagPrincipal parseAccessToken(String token) {
        Claims claims = parseAndValidate(token, TYPE_ACCESS);
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        return new RagPrincipal(userId, email != null ? email : "", role != null ? role : "USER");
    }

    public UUID parseRefreshTokenUserId(String token) {
        Claims claims = parseAndValidate(token, TYPE_REFRESH);
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseAndValidate(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String typ = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(typ)) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }
}
