package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.infrastructure.persistence.OauthIdentityRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginExchangeCodeRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginExchangeCodeEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OauthLoginServiceTest {

    @Mock
    private UserAccountPort userAccountPort;

    @Mock
    private OauthIdentityRepository oauthIdentityRepository;

    @Mock
    private OauthLoginExchangeCodeRepository oauthLoginExchangeCodeRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void exchange_validCode_returnsTokens() {
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(UUID.randomUUID());
        when(u.getEmail()).thenReturn("a@b.com");
        when(u.getName()).thenReturn("N");

        OauthLoginExchangeCodeEntity e = new OauthLoginExchangeCodeEntity();
        e.setUser(u);
        e.setCodeHash("h");
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(60));

        when(oauthLoginExchangeCodeRepository.findByCodeHash(any())).thenReturn(Optional.of(e));
        when(jwtService.createAccessToken(any(), any(), any())).thenReturn("acc");
        when(jwtService.createRefreshToken(any())).thenReturn("ref");

        OauthLoginService svc = new OauthLoginService(
                userAccountPort,
                oauthIdentityRepository,
                oauthLoginExchangeCodeRepository,
                jwtService,
                passwordEncoder,
                true,
                "http://localhost:3000",
                "http://localhost:9000",
                "cid",
                "csecret",
                "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        var res = svc.exchange("raw");
        assertThat(res.accessToken()).isEqualTo("acc");
        assertThat(res.refreshToken()).isEqualTo("ref");
        assertThat(res.user().email()).isEqualTo("a@b.com");
    }
}

