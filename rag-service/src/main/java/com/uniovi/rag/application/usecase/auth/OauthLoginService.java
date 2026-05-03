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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OauthLoginService.class);

    private static final String PROVIDER_GOOGLE = "google";
    private static final String REL_PATH_LOGIN_OAUTH_ERROR = "/login?oauth=error";
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
    /** When true, append {@code prompt=select_account} so Google shows the account picker on each login start. */
    private final boolean googlePromptSelectAccount;

    /** Google OAuth 2.0 authorization endpoint (must match provider docs for the configured client). */
    private final String googleAuthorizationUri;

    /** Token endpoint for exchanging an authorization code (RFC 6749). */
    private final String googleTokenUri;

    /** JWK Set URI for validating Google ID tokens. */
    private final String googleJwkSetUri;

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
            @Value("${rag.auth.oauth.google.redirect-path:${rag.api.product-base-path}/auth/oauth/google/callback}")
                    String googleRedirectPath,
            @Value("${rag.auth.oauth.google.prompt-select-account:true}") boolean googlePromptSelectAccount,
            @Value("${rag.auth.oauth.google.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}")
                    String googleAuthorizationUri,
            @Value("${rag.auth.oauth.google.token-uri:https://oauth2.googleapis.com/token}") String googleTokenUri,
            @Value("${rag.auth.oauth.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}")
                    String googleJwkSetUri) {
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
        this.googleRedirectPath = googleRedirectPath != null ? googleRedirectPath : "/auth/oauth/google/callback";
        this.googlePromptSelectAccount = googlePromptSelectAccount;
        this.googleAuthorizationUri =
                nonBlankOrDefault(googleAuthorizationUri, "https://accounts.google.com/o/oauth2/v2/auth");
        this.googleTokenUri = nonBlankOrDefault(googleTokenUri, "https://oauth2.googleapis.com/token");
        this.googleJwkSetUri = nonBlankOrDefault(googleJwkSetUri, "https://www.googleapis.com/oauth2/v3/certs");
    }

    public String googleStartUrl(String locale) {
        String resolvedLocale = resolveLocale(locale);
        if (!oauthEnabled) {
            return webappBaseUrl + "/" + resolvedLocale + "/login";
        }
        if (googleClientId.isBlank()) {
            throw new IllegalStateException("OAuth enabled but Google client-id is empty");
        }
        String redirectUri = buildRedirectUri();
        log.debug(
                "OAuth Google authorization redirect_uri (must exactly match an Authorized redirect URI in Google Cloud Console): {}",
                redirectUri);
        String state = createStateToken(resolvedLocale);
        StringBuilder url = new StringBuilder(googleAuthorizationUri)
                .append("?client_id=")
                .append(urlEncode(googleClientId))
                .append("&redirect_uri=")
                .append(urlEncode(redirectUri))
                .append("&response_type=code")
                .append("&scope=")
                .append(urlEncode("openid email profile"))
                .append("&state=")
                .append(urlEncode(state));
        if (googlePromptSelectAccount) {
            url.append("&prompt=").append(urlEncode("select_account"));
        }
        return url.toString();
    }

    @Transactional
    public String handleGoogleCallback(String code, String state, String error) {
        String resolvedLocale = extractLocaleFromState(state);
        if (!oauthEnabled) {
            return webappBaseUrl + "/" + resolvedLocale + "/login";
        }
        if (error != null && !error.isBlank()) {
            return webappBaseUrl + "/" + resolvedLocale + REL_PATH_LOGIN_OAUTH_ERROR;
        }
        if (code == null || code.isBlank()) {
            return webappBaseUrl + "/" + resolvedLocale + REL_PATH_LOGIN_OAUTH_ERROR;
        }
        if (!consumeStateToken(state)) {
            return webappBaseUrl + "/" + resolvedLocale + "/login?oauth=invalid_state";
        }

        Map<String, Object> tokenResponse = exchangeAuthCodeForTokens(code);
        String idToken = Optional.ofNullable(tokenResponse.get("id_token")).map(Object::toString).orElse("");
        if (idToken.isBlank()) {
            return webappBaseUrl + "/" + resolvedLocale + REL_PATH_LOGIN_OAUTH_ERROR;
        }

        Jwt jwt = googleIdTokenDecoder().decode(idToken);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            return webappBaseUrl + "/" + resolvedLocale + REL_PATH_LOGIN_OAUTH_ERROR;
        }

        UserEntity user = resolveOrCreateUser(subject, email, Boolean.TRUE.equals(emailVerified));
        String exchangeCode = createExchangeCode(user);
        return webappBaseUrl + "/" + resolvedLocale + "/oauth/callback/google?code=" + urlEncode(exchangeCode);
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
                    created.setEmailVerified(false);
                    created.setEmailVerifiedAt(null);
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

    private String createStateToken(String locale) {
        String raw = randomToken(16);
        String state = raw + "." + resolveLocale(locale);
        String hash = sha256Hex(state);
        OauthLoginStateTokenEntity e = new OauthLoginStateTokenEntity();
        e.setStateHash(hash);
        e.setCreatedAt(Instant.now());
        e.setExpiresAt(Instant.now().plusSeconds(STATE_TTL_SECONDS));
        oauthLoginStateTokenRepository.save(e);
        return state;
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

    private String extractLocaleFromState(String state) {
        if (state == null || state.isBlank()) {
            return "en";
        }
        String[] parts = state.trim().split("\\.");
        return resolveLocale(parts.length > 1 ? parts[parts.length - 1] : null);
    }

    private String resolveLocale(String locale) {
        if (locale == null) {
            return "en";
        }
        String normalized = locale.trim().toLowerCase();
        if (normalized.matches("^[a-z]{2}(-[a-z]{2})?$")) {
            return normalized;
        }
        return "en";
    }

    Map<String, Object> exchangeAuthCodeForTokens(String code) {
        String redirectUri = buildRedirectUri();
        return restClient.post()
                .uri(googleTokenUri)
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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(googleJwkSetUri).build();
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

    private static String nonBlankOrDefault(String raw, String def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        return raw.trim();
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

