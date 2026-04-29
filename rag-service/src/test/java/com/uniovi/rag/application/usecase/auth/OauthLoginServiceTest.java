package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.infrastructure.persistence.OauthIdentityRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginExchangeCodeRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginStateTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginExchangeCodeEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthIdentityEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private OauthLoginStateTokenRepository oauthLoginStateTokenRepository;

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
                oauthLoginStateTokenRepository,
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

    @Test
    void googleStartUrl_disabled_returnsLoginUrl() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                false, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        assertThat(svc.googleStartUrl()).isEqualTo("http://localhost:3000/en/login");
    }

    @Test
    void googleStartUrl_enabledWithoutClientId_throws() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        assertThatThrownBy(svc::googleStartUrl).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exchange_disabled_throwsInvalidCredentials() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                false, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        assertThatThrownBy(() -> svc.exchange("raw")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void exchange_consumedCode_throwsInvalidCredentials() {
        OauthLoginExchangeCodeEntity e = new OauthLoginExchangeCodeEntity();
        e.setCodeHash("h");
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(60));
        e.setConsumedAt(Instant.now());
        when(oauthLoginExchangeCodeRepository.findByCodeHash(any())).thenReturn(Optional.of(e));

        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        assertThatThrownBy(() -> svc.exchange("raw")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void exchange_expiredCode_throwsInvalidCredentials() {
        OauthLoginExchangeCodeEntity e = new OauthLoginExchangeCodeEntity();
        e.setCodeHash("h");
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().minusSeconds(1));
        when(oauthLoginExchangeCodeRepository.findByCodeHash(any())).thenReturn(Optional.of(e));

        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        assertThatThrownBy(() -> svc.exchange("raw")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void googleStartUrl_enabled_buildsGoogleAuthorizationUrl() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000/", "http://localhost:9000/", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        String url = svc.googleStartUrl();
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("client_id=cid");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=openid+email+profile");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fauth%2Foauth%2Fgoogle%2Fcallback");
        assertThat(url).contains("state=");
        verify(oauthLoginStateTokenRepository).save(any());
    }

    @Test
    void resolveOrCreateUser_existingIdentity_updatesLastLogin() {
        UserEntity user = mock(UserEntity.class);
        OauthIdentityEntity identity = new OauthIdentityEntity();
        identity.setUser(user);
        when(oauthIdentityRepository.findByProviderAndProviderSubject("google", "subject"))
                .thenReturn(Optional.of(identity));
        when(userAccountPort.save(user)).thenReturn(user);

        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");
        UserEntity resolved = ReflectionTestUtils.invokeMethod(svc, "resolveOrCreateUser", "subject", "a@b.com", true);
        assertThat(resolved).isEqualTo(user);
    }

    @Test
    void resolveOrCreateUser_newUser_linksIdentity_and_marksEmailVerified() {
        when(oauthIdentityRepository.findByProviderAndProviderSubject("google", "subject"))
                .thenReturn(Optional.empty());
        when(userAccountPort.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("pw-hash");
        when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        UserEntity resolved =
                ReflectionTestUtils.invokeMethod(svc, "resolveOrCreateUser", "subject", "User@Example.com", true);

        assertThat(resolved.getEmail()).isEqualTo("user@example.com");
        assertThat(resolved.isEmailVerified()).isTrue();
        assertThat(resolved.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void handleGoogleCallback_oauthDisabled_returnsLoginUrl() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                false, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        assertThat(svc.handleGoogleCallback("code", "state", null)).isEqualTo("http://localhost:3000/en/login");
    }

    @Test
    void handleGoogleCallback_errorOrMissingCode_returnsOauthError() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        assertThat(svc.handleGoogleCallback("code", "state", "access_denied"))
                .isEqualTo("http://localhost:3000/en/login?oauth=error");
        assertThat(svc.handleGoogleCallback(null, "state", null))
                .isEqualTo("http://localhost:3000/en/login?oauth=error");
        assertThat(svc.handleGoogleCallback("   ", "state", null))
                .isEqualTo("http://localhost:3000/en/login?oauth=error");
    }

    @Test
    void handleGoogleCallback_invalidState_returnsInvalidState_withoutExchangingCode() {
        when(oauthLoginStateTokenRepository.findByStateHash(any())).thenReturn(Optional.empty());
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/auth/oauth/google/callback");

        String redirect = svc.handleGoogleCallback("code", "bad-state", null);
        assertThat(redirect).isEqualTo("http://localhost:3000/en/login?oauth=invalid_state");
        verify(oauthLoginStateTokenRepository).findByStateHash(any());
        verify(oauthLoginExchangeCodeRepository, never()).save(any());
    }
}

