package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.OauthIdentityRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginExchangeCodeRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginStateTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthIdentityEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginExchangeCodeEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginStateTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.security.JwtService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class OauthLoginService {

    private static final String PROVIDER_GOOGLE = "google";
    private static final long EXCHANGE_TTL_SECONDS = 120;
    private static final long STATE_TTL_SECONDS = 300;

    private final UserAccountPort userAccountPort;
    private final OauthIdentityRepository oauthIdentityRepository;
    private final OauthLoginExchangeCodeRepository oauthLoginExchangeCodeRepository;
    private final OauthLoginStateTokenRepository oauthLoginStateTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private final boolean oauthEnabled;
    private final String webappBaseUrl;
    private final String backendBaseUrl;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleIssuer;
    private final String googleRedirectPath;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RestClient restClient = RestClient.create();

    public OauthLoginService(
            UserAccountPort userAccountPort,
            OauthIdentityRepository oauthIdentityRepository,
            OauthLoginExchangeCodeRepository oauthLoginExchangeCodeRepository,
            OauthLoginStateTokenRepository oauthLoginStateTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            @Value("${rag.auth.oauth.enabled:false}") boolean oauthEnabled,
            @Value("${rag.auth.webapp-base-url:http://localhost:3000}") String webappBaseUrl,
            @Value("${rag.auth.backend-base-url:http://localhost:9000}") String backendBaseUrl,
            @Value("${rag.auth.oauth.google.client-id:}") String googleClientId,
            @Value("${rag.auth.oauth.google.client-secret:}") String googleClientSecret,
            @Value("${rag.auth.oauth.google.issuer:https://accounts.google.com}") String googleIssuer,
            @Value("${rag.auth.oauth.google.redirect-path:/api/auth/oauth/google/callback}") String googleRedirectPath) {
        this.userAccountPort = userAccountPort;
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.oauthLoginExchangeCodeRepository = oauthLoginExchangeCodeRepository;
        this.oauthLoginStateTokenRepository = oauthLoginStateTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.oauthEnabled = oauthEnabled;
        this.webappBaseUrl = normalizeBaseUrl(webappBaseUrl);
        this.backendBaseUrl = normalizeBaseUrl(backendBaseUrl);
        this.googleClientId = googleClientId != null ? googleClientId : "";
        this.googleClientSecret = googleClientSecret != null ? googleClientSecret : "";
        this.googleIssuer = googleIssuer != null ? googleIssuer : "https://accounts.google.com";
        this.googleRedirectPath = googleRedirectPath != null ? googleRedirectPath : "/api/auth/oauth/google/callback";
    }

    public String googleStartUrl() {
        if (!oauthEnabled) {
            return webappBaseUrl + "/en/login";
        }
        if (googleClientId.isBlank()) {
            throw new IllegalStateException("OAuth enabled but Google client-id is empty");
        }
        String redirectUri = buildRedirectUri();
        String state = createStateToken();
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + urlEncode(googleClientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode("openid email profile")
                + "&state=" + urlEncode(state);
    }

    @Transactional
    public String handleGoogleCallback(String code, String state, String error) {
        if (!oauthEnabled) {
            return webappBaseUrl + "/en/login";
        }
        if (error != null && !error.isBlank()) {
            return webappBaseUrl + "/en/login?oauth=error";
        }
        if (code == null || code.isBlank()) {
            return webappBaseUrl + "/en/login?oauth=error";
        }
        if (!consumeStateToken(state)) {
            return webappBaseUrl + "/en/login?oauth=invalid_state";
        }

        Map<String, Object> tokenResponse = exchangeAuthCodeForTokens(code);
        String idToken = Optional.ofNullable(tokenResponse.get("id_token")).map(Object::toString).orElse("");
        if (idToken.isBlank()) {
            return webappBaseUrl + "/en/login?oauth=error";
        }

        Jwt jwt = googleIdTokenDecoder().decode(idToken);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            return webappBaseUrl + "/en/login?oauth=error";
        }

        UserEntity user = resolveOrCreateUser(subject, email, Boolean.TRUE.equals(emailVerified));
        String exchangeCode = createExchangeCode(user);
        return webappBaseUrl + "/en/oauth/callback/google?code=" + urlEncode(exchangeCode);
    }

    @Transactional
    public LoginResponse exchange(String code) {
        if (!oauthEnabled) {
            throw new InvalidCredentialsException();
        }
        String hash = sha256Hex(code.trim());
        OauthLoginExchangeCodeEntity e = oauthLoginExchangeCodeRepository.findByCodeHash(hash)
                .orElseThrow(InvalidCredentialsException::new);
        if (e.getConsumedAt() != null) {
            throw new InvalidCredentialsException();
        }
        if (e.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException();
        }
        e.setConsumedAt(Instant.now());
        oauthLoginExchangeCodeRepository.save(e);
        return tokensForUser(e.getUser());
    }

    private UserEntity resolveOrCreateUser(String providerSubject, String email, boolean emailVerified) {
        Optional<OauthIdentityEntity> existing = oauthIdentityRepository.findByProviderAndProviderSubject(
                PROVIDER_GOOGLE, providerSubject);
        if (existing.isPresent()) {
            UserEntity u = existing.get().getUser();
            u.setLastLoginAt(Instant.now());
            return userAccountPort.save(u);
        }

        UserEntity u = userAccountPort.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseGet(() -> {
                    String randomPw = randomToken(24);
                    UserEntity created = UserEntityFactory.newRegisteredUser(
                            email.trim().toLowerCase(),
                            email.trim(),
                            passwordEncoder.encode(randomPw));
                    created.setRole(UserRole.USER);
                    created.setCreatedAt(Instant.now());
                    return userAccountPort.save(created);
                });

        if (emailVerified) {
            u.setEmailVerified(true);
            if (u.getEmailVerifiedAt() == null) {
                u.setEmailVerifiedAt(Instant.now());
            }
        }

        OauthIdentityEntity ident = new OauthIdentityEntity();
        ident.setUser(u);
        ident.setProvider(PROVIDER_GOOGLE);
        ident.setProviderSubject(providerSubject);
        ident.setEmailAtLinkTime(email);
        ident.setCreatedAt(Instant.now());
        oauthIdentityRepository.save(ident);

        u.setLastLoginAt(Instant.now());
        return userAccountPort.save(u);
    }

    private String createExchangeCode(UserEntity user) {
        String raw = randomToken(32);
        String hash = sha256Hex(raw);
        OauthLoginExchangeCodeEntity e = new OauthLoginExchangeCodeEntity();
        e.setUser(user);
        e.setCodeHash(hash);
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(EXCHANGE_TTL_SECONDS));
        oauthLoginExchangeCodeRepository.save(e);
        return raw;
    }

    private String createStateToken() {
        String raw = randomToken(16);
        String hash = sha256Hex(raw);
        OauthLoginStateTokenEntity e = new OauthLoginStateTokenEntity();
        e.setStateHash(hash);
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(STATE_TTL_SECONDS));
        oauthLoginStateTokenRepository.save(e);
        return raw;
    }

    private boolean consumeStateToken(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String hash = sha256Hex(state.trim());
        Optional<OauthLoginStateTokenEntity> maybe = oauthLoginStateTokenRepository.findByStateHash(hash);
        if (maybe.isEmpty()) {
            return false;
        }
        OauthLoginStateTokenEntity e = maybe.get();
        if (e.getConsumedAt() != null) {
            return false;
        }
        if (e.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        e.setConsumedAt(Instant.now());
        oauthLoginStateTokenRepository.save(e);
        return true;
    }

    Map<String, Object> exchangeAuthCodeForTokens(String code) {
        String redirectUri = buildRedirectUri();
        return restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + urlEncode(code)
                        + "&client_id=" + urlEncode(googleClientId)
                        + "&client_secret=" + urlEncode(googleClientSecret)
                        + "&redirect_uri=" + urlEncode(redirectUri)
                        + "&grant_type=authorization_code")
                .retrieve()
                .body(Map.class);
    }

    JwtDecoder googleIdTokenDecoder() {
        String jwks = "https://www.googleapis.com/oauth2/v3/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(googleIssuer));
        return decoder;
    }

    private LoginResponse tokensForUser(UserEntity u) {
        String roleName = u.getRole() != null ? u.getRole().name() : UserRole.USER.name();
        String access = jwtService.createAccessToken(u.getId(), u.getEmail(), roleName);
        String refresh = jwtService.createRefreshToken(u.getId());
        AuthUserDto dto = new AuthUserDto(u.getId(), u.getEmail(), u.getName(), roleName);
        return new LoginResponse(access, refresh, dto);
    }

    private String buildRedirectUri() {
        return backendBaseUrl + googleRedirectPath;
    }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        secureRandom.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte bb : dig) {
                sb.append(String.format("%02x", bb));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    private static String normalizeBaseUrl(String raw) {
        String s = raw != null ? raw.trim() : "";
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "http://localhost:3000" : s;
    }

    private static String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}

