package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.infrastructure.persistence.OauthIdentityRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginExchangeCodeRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginStateTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthIdentityEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginExchangeCodeEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginStateTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.mockito.ArgumentCaptor;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
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

    @BeforeEach
    void resetMocks() {
        Mockito.reset(
                userAccountPort,
                oauthIdentityRepository,
                oauthLoginExchangeCodeRepository,
                oauthLoginStateTokenRepository,
                jwtService,
                passwordEncoder);
    }

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
                "/api/v5/auth/oauth/google/callback", true);

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
                "/api/v5/auth/oauth/google/callback", true);
        assertThat(svc.googleStartUrl("en")).isEqualTo("http://localhost:3000/en/login");
    }

    @Test
    void googleStartUrl_enabledWithoutClientId_throws() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);
        assertThatThrownBy(() -> svc.googleStartUrl("en")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exchange_disabled_throwsInvalidCredentials() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                false, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);
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
                "/api/v5/auth/oauth/google/callback", true);
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
                "/api/v5/auth/oauth/google/callback", true);
        assertThatThrownBy(() -> svc.exchange("raw")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void exchange_unknownCode_throwsInvalidCredentials() {
        when(oauthLoginExchangeCodeRepository.findByCodeHash(any())).thenReturn(Optional.empty());

        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);
        assertThatThrownBy(() -> svc.exchange("unknown-raw-code")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void exchange_secondCallFails_afterSuccessfulExchange_singleUse() {
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
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);

        svc.exchange("raw-code-value");

        assertThat(e.getConsumedAt()).isNotNull();

        assertThatThrownBy(() -> svc.exchange("raw-code-value")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void googleStartUrl_enabled_buildsGoogleAuthorizationUrl() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000/", "http://localhost:9000/", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);

        String url = svc.googleStartUrl("es");
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("client_id=cid");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=openid+email+profile");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fv5%2Fauth%2Foauth%2Fgoogle%2Fcallback");
        assertThat(url).contains("state=");
        assertThat(url).contains("prompt=select_account");
        verify(oauthLoginStateTokenRepository).save(any());
    }

    @Test
    void googleStartUrl_promptSelectAccountDisabled_omitsPromptParameter() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort,
                oauthIdentityRepository,
                oauthLoginExchangeCodeRepository,
                oauthLoginStateTokenRepository,
                jwtService,
                passwordEncoder,
                true,
                "http://localhost:3000/",
                "http://localhost:9000/",
                "cid",
                "secret",
                "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback",
                false);

        String url = svc.googleStartUrl("en");
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("state=");
        assertThat(url).doesNotContain("prompt=");
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
                "/api/v5/auth/oauth/google/callback", true);
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
                "/api/v5/auth/oauth/google/callback", true);

        UserEntity resolved =
                ReflectionTestUtils.invokeMethod(svc, "resolveOrCreateUser", "subject", "User@Example.com", true);

        assertThat(resolved.getEmail()).isEqualTo("user@example.com");
        assertThat(resolved.isEmailVerified()).isTrue();
        assertThat(resolved.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void resolveOrCreateUser_newUser_withEmailVerifiedFalse_keepsUserUnverified() {
        when(oauthIdentityRepository.findByProviderAndProviderSubject("google", "subject-false"))
                .thenReturn(Optional.empty());
        when(userAccountPort.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("pw-hash");
        when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oauthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
                "secret",
                "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);

        UserEntity resolved = ReflectionTestUtils.invokeMethod(
                svc,
                "resolveOrCreateUser",
                "subject-false",
                "false@example.com",
                false);

        assertThat(resolved.isEmailVerified()).isFalse();
        assertThat(resolved.getEmailVerifiedAt()).isNull();
    }

    @Test
    void handleGoogleCallback_oauthDisabled_returnsLoginUrl() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                false, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);

        assertThat(svc.handleGoogleCallback("code", "state", null)).isEqualTo("http://localhost:3000/en/login");
    }

    @Test
    void handleGoogleCallback_errorOrMissingCode_returnsOauthError() {
        OauthLoginService svc = new OauthLoginService(
                userAccountPort, oauthIdentityRepository, oauthLoginExchangeCodeRepository, oauthLoginStateTokenRepository, jwtService, passwordEncoder,
                true, "http://localhost:3000", "http://localhost:9000", "cid", "secret", "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);

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
                "/api/v5/auth/oauth/google/callback", true);

        String redirect = svc.handleGoogleCallback("code", "bad-state", null);
        assertThat(redirect).isEqualTo("http://localhost:3000/en/login?oauth=invalid_state");
        verify(oauthLoginStateTokenRepository).findByStateHash(any());
        verify(oauthLoginExchangeCodeRepository, never()).save(any());
    }

    @Test
    void consumeStateToken_nullBlank_returnsFalse() {
        OauthLoginService svc = oauthEnabledService();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", (Object) null)).isFalse();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "")).isFalse();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "   ")).isFalse();
    }

    @Test
    void consumeStateToken_unknown_returnsFalse() {
        when(oauthLoginStateTokenRepository.findByStateHash(any())).thenReturn(Optional.empty());
        OauthLoginService svc = oauthEnabledService();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "tok")).isFalse();
    }

    @Test
    void consumeStateToken_alreadyConsumed_returnsFalse() {
        OauthLoginStateTokenEntity e = new OauthLoginStateTokenEntity();
        e.setConsumedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(60));
        when(oauthLoginStateTokenRepository.findByStateHash(any())).thenReturn(Optional.of(e));
        OauthLoginService svc = oauthEnabledService();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "tok")).isFalse();
        verify(oauthLoginStateTokenRepository, never()).save(any());
    }

    @Test
    void consumeStateToken_expired_returnsFalse() {
        OauthLoginStateTokenEntity e = new OauthLoginStateTokenEntity();
        e.setConsumedAt(null);
        e.setExpiresAt(Instant.now().minusSeconds(5));
        when(oauthLoginStateTokenRepository.findByStateHash(any())).thenReturn(Optional.of(e));
        OauthLoginService svc = oauthEnabledService();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "tok")).isFalse();
    }

    @Test
    void consumeStateToken_valid_marksConsumedAndReturnsTrue() {
        OauthLoginStateTokenEntity e = new OauthLoginStateTokenEntity();
        e.setConsumedAt(null);
        e.setExpiresAt(Instant.now().plusSeconds(120));
        when(oauthLoginStateTokenRepository.findByStateHash(any())).thenReturn(Optional.of(e));
        OauthLoginService svc = oauthEnabledService();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(svc, "consumeStateToken", "tok")).isTrue();
        verify(oauthLoginStateTokenRepository).save(e);
        assertThat(e.getConsumedAt()).isNotNull();
    }

    @Test
    void createExchangeCode_persistsRowAndReturnsRawSecret() {
        OauthLoginService svc = oauthEnabledService();
        UserEntity u = mock(UserEntity.class);
        when(oauthLoginExchangeCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String raw = (String) ReflectionTestUtils.invokeMethod(svc, "createExchangeCode", u);
        assertThat(raw).isNotBlank();
        verify(oauthLoginExchangeCodeRepository).save(any());
    }

    @Test
    void exchange_validCode_nullRole_defaultsUserRoleInPayload() {
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(UUID.randomUUID());
        when(u.getEmail()).thenReturn("a@b.com");
        when(u.getName()).thenReturn("N");
        when(u.getRole()).thenReturn(null);

        OauthLoginExchangeCodeEntity e = new OauthLoginExchangeCodeEntity();
        e.setUser(u);
        e.setCodeHash("h");
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(60));

        when(oauthLoginExchangeCodeRepository.findByCodeHash(any())).thenReturn(Optional.of(e));
        when(jwtService.createAccessToken(any(), any(), any())).thenReturn("acc");
        when(jwtService.createRefreshToken(any())).thenReturn("ref");

        OauthLoginService svc = oauthEnabledService();
        LoginResponse res = svc.exchange("raw");
        assertThat(res.user().role()).isEqualTo("USER");
    }

    @Test
    void resolveOrCreateUser_existingEmail_keepsEmailVerifiedAtWhenAlreadySet() {
        Instant fixed = Instant.parse("2019-06-01T00:00:00Z");
        UserEntity existing = UserEntityFactory.newRegisteredUser("e@test.com", "N", "pw");
        existing.setEmailVerified(false);
        existing.setEmailVerifiedAt(fixed);

        when(oauthIdentityRepository.findByProviderAndProviderSubject("google", "sub-x")).thenReturn(Optional.empty());
        when(userAccountPort.findByEmailIgnoreCase("e@test.com")).thenReturn(Optional.of(existing));
        when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oauthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OauthLoginService svc = oauthEnabledService();
        UserEntity out = ReflectionTestUtils.invokeMethod(svc, "resolveOrCreateUser", "sub-x", "e@test.com", true);
        assertThat(out.isEmailVerified()).isTrue();
        assertThat(out.getEmailVerifiedAt()).isEqualTo(fixed);
    }

    @Test
    void handleGoogleCallback_blankIdToken_returnsOauthError() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        String rawState = "test-oauth-state-blank-id";
        OauthLoginStateTokenEntity stateRow = validUnconsumedStateRow(rawState);
        when(oauthLoginStateTokenRepository.findByStateHash(stateRow.getStateHash())).thenReturn(Optional.of(stateRow));

        TestOauthLoginService svc =
                new TestOauthLoginService(
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
                        "secret",
                        "https://accounts.google.com",
                        "/api/v5/auth/oauth/google/callback",
                        true,
                        code -> Map.of(),
                        decoder);

        assertThat(svc.handleGoogleCallback("auth-code", rawState, null))
                .isEqualTo("http://localhost:3000/en/login?oauth=error");
        verify(decoder, never()).decode(any());
    }

    @Test
    void handleGoogleCallback_invalidJwtClaims_returnsOauthError() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwtMissingEmail = mock(Jwt.class);
        when(jwtMissingEmail.getSubject()).thenReturn("only-sub");
        when(jwtMissingEmail.getClaimAsString("email")).thenReturn(null);
        when(decoder.decode("id")).thenReturn(jwtMissingEmail);

        String rawState = "test-oauth-state-bad-claims";
        OauthLoginStateTokenEntity stateRow = validUnconsumedStateRow(rawState);
        when(oauthLoginStateTokenRepository.findByStateHash(stateRow.getStateHash())).thenReturn(Optional.of(stateRow));

        TestOauthLoginService svc =
                new TestOauthLoginService(
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
                        "secret",
                        "https://accounts.google.com",
                        "/api/v5/auth/oauth/google/callback",
                        true,
                        code -> Map.of("id_token", "id"),
                        decoder);

        assertThat(svc.handleGoogleCallback("auth-code", rawState, null))
                .isEqualTo("http://localhost:3000/en/login?oauth=error");
    }

    @Test
    void handleGoogleCallback_success_redirectsWithExchangeCode() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("prov-sub");
        when(jwt.getClaimAsString("email")).thenReturn("newoauth@test.com");
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(decoder.decode("id-jwt")).thenReturn(jwt);

        when(oauthIdentityRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(userAccountPort.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("pw-hash");
        when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oauthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String rawState = "test-oauth-state-success";
        OauthLoginStateTokenEntity stateRow = validUnconsumedStateRow(rawState);
        when(oauthLoginStateTokenRepository.findByStateHash(stateRow.getStateHash())).thenReturn(Optional.of(stateRow));

        TestOauthLoginService svc =
                new TestOauthLoginService(
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
                        "secret",
                        "https://accounts.google.com",
                        "/api/v5/auth/oauth/google/callback",
                        true,
                        code -> Map.of("id_token", "id-jwt"),
                        decoder);

        String redirect = svc.handleGoogleCallback("auth-code", rawState, null);

        assertThat(redirect).startsWith("http://localhost:3000/en/oauth/callback/google?code=");
        verify(oauthLoginExchangeCodeRepository).save(any());
        verify(oauthIdentityRepository).save(any());
    }

    @Test
    void handleGoogleCallback_missingEmailVerified_claim_doesNotAutoVerify() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("prov-sub-missing-verified");
        when(jwt.getClaimAsString("email")).thenReturn("missing-verified@test.com");
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(null);
        when(decoder.decode("id-jwt")).thenReturn(jwt);

        when(oauthIdentityRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(userAccountPort.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("pw-hash");
        when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oauthIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String rawState = "test-oauth-state-missing-verified-claim";
        OauthLoginStateTokenEntity stateRow = validUnconsumedStateRow(rawState);
        when(oauthLoginStateTokenRepository.findByStateHash(stateRow.getStateHash())).thenReturn(Optional.of(stateRow));

        TestOauthLoginService svc =
                new TestOauthLoginService(
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
                        "secret",
                        "https://accounts.google.com",
                        "/api/v5/auth/oauth/google/callback",
                        true,
                        code -> Map.of("id_token", "id-jwt"),
                        decoder);

        String redirect = svc.handleGoogleCallback("auth-code", rawState, null);

        assertThat(redirect).startsWith("http://localhost:3000/en/oauth/callback/google?code=");
        ArgumentCaptor<UserEntity> savedUsers = ArgumentCaptor.forClass(UserEntity.class);
        verify(userAccountPort, atLeastOnce()).save(savedUsers.capture());
        UserEntity savedUser = savedUsers.getValue();
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getEmailVerifiedAt()).isNull();
    }

    @Test
    void oauthConstructor_nullIssuerAndRedirect_useDefaults() {
        OauthLoginService svc =
                new OauthLoginService(
                        userAccountPort,
                        oauthIdentityRepository,
                        oauthLoginExchangeCodeRepository,
                        oauthLoginStateTokenRepository,
                        jwtService,
                        passwordEncoder,
                        true,
                        null,
                        "http://localhost:9000",
                        "cid",
                        "secret",
                        (String) null,
                        (String) null,
                        true);
        assertThat(ReflectionTestUtils.getField(svc, "googleIssuer")).isEqualTo("https://accounts.google.com");
        assertThat(ReflectionTestUtils.getField(svc, "googleRedirectPath")).isEqualTo("/auth/oauth/google/callback");
    }

    private static OauthLoginStateTokenEntity validUnconsumedStateRow(String rawState) {
        OauthLoginStateTokenEntity st = new OauthLoginStateTokenEntity();
        st.setStateHash(oauthStateHash(rawState));
        st.setConsumedAt(null);
        st.setExpiresAt(Instant.now().plusSeconds(300));
        return st;
    }

    private static String oauthStateHash(String rawState) {
        return (String) ReflectionTestUtils.invokeMethod(OauthLoginService.class, "sha256Hex", rawState);
    }

    private OauthLoginService oauthEnabledService() {
        return new OauthLoginService(
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
                "secret",
                "https://accounts.google.com",
                "/api/v5/auth/oauth/google/callback", true);
    }

    /** Test double: avoids HTTP and Google's JWKS while covering {@link OauthLoginService#handleGoogleCallback}. */
    private static final class TestOauthLoginService extends OauthLoginService {
        private final Function<String, Map<String, Object>> tokenResponse;
        private final JwtDecoder jwtDecoder;

        private TestOauthLoginService(
                UserAccountPort userAccountPort,
                OauthIdentityRepository oauthIdentityRepository,
                OauthLoginExchangeCodeRepository oauthLoginExchangeCodeRepository,
                OauthLoginStateTokenRepository oauthLoginStateTokenRepository,
                JwtService jwtService,
                PasswordEncoder passwordEncoder,
                boolean oauthEnabled,
                String webappBaseUrl,
                String backendBaseUrl,
                String googleClientId,
                String googleClientSecret,
                String googleIssuer,
                String googleRedirectPath,
                boolean googlePromptSelectAccount,
                Function<String, Map<String, Object>> tokenResponse,
                JwtDecoder jwtDecoder) {
            super(
                    userAccountPort,
                    oauthIdentityRepository,
                    oauthLoginExchangeCodeRepository,
                    oauthLoginStateTokenRepository,
                    jwtService,
                    passwordEncoder,
                    oauthEnabled,
                    webappBaseUrl,
                    backendBaseUrl,
                    googleClientId,
                    googleClientSecret,
                    googleIssuer,
                    googleRedirectPath,
                    googlePromptSelectAccount);
            this.tokenResponse = tokenResponse;
            this.jwtDecoder = jwtDecoder;
        }

        @Override
        Map<String, Object> exchangeAuthCodeForTokens(String code) {
            return tokenResponse.apply(code);
        }

        @Override
        JwtDecoder googleIdTokenDecoder() {
            return jwtDecoder;
        }
    }
}

