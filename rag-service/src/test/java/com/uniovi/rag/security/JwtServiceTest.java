package com.uniovi.rag.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "unit-test-secret-key-at-least-32-chars-long!!",
                "test-issuer",
                3600,
                86400);
    }

    @Test
    void accessToken_roundTrip() {
        UUID id = UUID.randomUUID();
        String token = jwtService.createAccessToken(id, "a@b.com", "USER");
        RagPrincipal p = jwtService.parseAccessToken(token);
        assertThat(p.userId()).isEqualTo(id);
        assertThat(p.email()).isEqualTo("a@b.com");
        assertThat(p.roleName()).isEqualTo("USER");
    }

    @Test
    void refreshToken_roundTrip() {
        UUID id = UUID.randomUUID();
        String token = jwtService.createRefreshToken(id);
        assertThat(jwtService.parseRefreshTokenUserId(token)).isEqualTo(id);
    }

    @Test
    void parseAccessToken_rejectsRefreshToken() {
        UUID id = UUID.randomUUID();
        String refresh = jwtService.createRefreshToken(id);
        assertThatThrownBy(() -> jwtService.parseAccessToken(refresh)).isInstanceOf(Exception.class);
    }
}
