package com.uniovi.rag.application.service.auth;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.EmailConfirmationTokenRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.PasswordResetTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EmailConfirmationTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.PasswordResetTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.EmailNotVerifiedException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.AuthTokenException;
import com.uniovi.rag.interfaces.rest.auth.FeatureDisabledException;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.ConfirmEmailRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ForgotPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.ResendConfirmationRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResetPasswordRequest;
import com.uniovi.rag.security.JwtService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserAccountPort userAccountPort;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final MailOutboxRepository mailOutboxRepository;

	private final boolean emailConfirmationEnabled;
	private final boolean passwordResetEnabled;
	private final boolean mailEnabled;
	private final String webappBaseUrl;
	private final long emailConfirmationTtlSeconds;
	private final long passwordResetTtlSeconds;
	private final boolean legalAcceptanceRequired;
	private final String requiredPrivacyPolicyVersion;
	private final String requiredTermsVersion;
	private final String defaultLocale;

	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(
			UserAccountPort userAccountPort,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			EmailConfirmationTokenRepository emailConfirmationTokenRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			MailOutboxRepository mailOutboxRepository,
			@Value("${rag.auth.email-confirmation.enabled:false}") boolean emailConfirmationEnabled,
			@Value("${rag.auth.password-reset.enabled:false}") boolean passwordResetEnabled,
			@Value("${rag.auth.mail.enabled:false}") boolean mailEnabled,
			@Value("${rag.auth.webapp-base-url:http://localhost:3000}") String webappBaseUrl,
			@Value("${rag.auth.email-confirmation.token-ttl-seconds:3600}") long emailConfirmationTtlSeconds,
			@Value("${rag.auth.password-reset.token-ttl-seconds:3600}") long passwordResetTtlSeconds,
			@Value("${rag.auth.legal.required:false}") boolean legalAcceptanceRequired,
			@Value("${rag.auth.legal.privacy-policy-version:}") String requiredPrivacyPolicyVersion,
			@Value("${rag.auth.legal.terms-version:}") String requiredTermsVersion,
			@Value("${rag.auth.default-locale:en}") String defaultLocale) {
		this.userAccountPort = userAccountPort;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.emailConfirmationTokenRepository = emailConfirmationTokenRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.mailOutboxRepository = mailOutboxRepository;
		this.emailConfirmationEnabled = emailConfirmationEnabled;
		this.passwordResetEnabled = passwordResetEnabled;
		this.mailEnabled = mailEnabled;
		this.webappBaseUrl = normalizeBaseUrl(webappBaseUrl);
		this.emailConfirmationTtlSeconds = emailConfirmationTtlSeconds;
		this.passwordResetTtlSeconds = passwordResetTtlSeconds;
		this.legalAcceptanceRequired = legalAcceptanceRequired;
		this.requiredPrivacyPolicyVersion = trimToEmpty(requiredPrivacyPolicyVersion);
		this.requiredTermsVersion = trimToEmpty(requiredTermsVersion);
		this.defaultLocale = trimToEmpty(defaultLocale).isEmpty() ? "en" : defaultLocale.trim();
	}

	@Transactional
	public RegisterResponse register(RegisterRequest req) {
		validateLegalAcceptance(req);
		if (userAccountPort.findByEmailIgnoreCase(req.email()).isPresent()) {
			throw new DuplicateEmailException();
		}
		UserEntity u = UserEntityFactory.newRegisteredUser(
				req.email().trim().toLowerCase(),
				req.name().trim(),
				passwordEncoder.encode(req.password()));
		if (emailConfirmationEnabled) {
			u.setEmailVerified(false);
			u.setEmailVerifiedAt(null);
		} else {
			u.setEmailVerified(true);
			if (u.getEmailVerifiedAt() == null) {
				u.setEmailVerifiedAt(Instant.now());
			}
		}
		applyLegalAcceptance(u, req);
		u = userAccountPort.save(u);
		if (emailConfirmationEnabled) {
			issueEmailConfirmation(u, req.locale());
			return new RegisterResponse("PENDING_EMAIL_VERIFICATION", null);
		}
		return new RegisterResponse("REGISTERED", tokensForUser(u));
	}

	@Transactional
	public LoginResponse login(LoginRequest req) {
		UserEntity u = userAccountPort.findByEmailIgnoreCase(req.email().trim().toLowerCase())
				.orElseThrow(InvalidCredentialsException::new);
		if (emailConfirmationEnabled && !u.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		u.setLastLoginAt(Instant.now());
		userAccountPort.save(u);
		return tokensForUser(u);
	}

	@Transactional(readOnly = true)
	public LoginResponse refresh(RefreshRequest req) {
		UUID userId;
		try {
			userId = jwtService.parseRefreshTokenUserId(req.refreshToken());
		} catch (Exception e) {
			throw new InvalidCredentialsException();
		}
		UserEntity u = userAccountPort.findById(userId).orElseThrow(InvalidCredentialsException::new);
		if (emailConfirmationEnabled && !u.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		return tokensForUser(u);
	}

	@Transactional
	public void confirmEmail(ConfirmEmailRequest req) {
		if (!emailConfirmationEnabled) {
			throw new FeatureDisabledException("EMAIL_CONFIRMATION_DISABLED", "Email confirmation disabled");
		}
		String hash = sha256Hex(req.token().trim());
		EmailConfirmationTokenEntity tok = emailConfirmationTokenRepository.findByTokenHash(hash)
				.orElseThrow(() -> new AuthTokenException("CONFIRM_TOKEN_INVALID", "Invalid confirmation token"));
		if (tok.getConsumedAt() != null) {
			throw new AuthTokenException("CONFIRM_TOKEN_ALREADY_USED", "Confirmation token already used");
		}
		if (tok.getExpiresAt().isBefore(Instant.now())) {
			throw new AuthTokenException("CONFIRM_TOKEN_EXPIRED", "Confirmation token expired");
		}
		tok.setConsumedAt(Instant.now());
		emailConfirmationTokenRepository.save(tok);
		UserEntity u = tok.getUser();
		u.setEmailVerified(true);
		u.setEmailVerifiedAt(Instant.now());
		userAccountPort.save(u);
	}

	@Transactional
	public void resendConfirmation(ResendConfirmationRequest req) {
		if (!emailConfirmationEnabled) {
			throw new FeatureDisabledException("EMAIL_CONFIRMATION_DISABLED", "Email confirmation disabled");
		}
		userAccountPort.findByEmailIgnoreCase(req.email().trim().toLowerCase())
				.ifPresent(u -> {
					if (!u.isEmailVerified()) {
							issueEmailConfirmation(u, req.locale());
					}
				});
	}

	@Transactional
	public void forgotPassword(ForgotPasswordRequest req, String requestIp, String requestUserAgent) {
		if (!passwordResetEnabled) {
			return;
		}
		userAccountPort.findByEmailIgnoreCase(req.email().trim().toLowerCase())
				.ifPresent(u -> issuePasswordReset(u, requestIp, requestUserAgent, req.locale()));
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest req) {
		if (!passwordResetEnabled) {
			throw new AuthTokenException("PASSWORD_RESET_DISABLED", "Password reset disabled");
		}
		String hash = sha256Hex(req.token().trim());
		PasswordResetTokenEntity tok = passwordResetTokenRepository.findByTokenHash(hash)
				.orElseThrow(() -> new AuthTokenException("RESET_TOKEN_INVALID", "Invalid reset token"));
		if (tok.getConsumedAt() != null) {
			throw new AuthTokenException("RESET_TOKEN_ALREADY_USED", "Reset token already used");
		}
		if (tok.getExpiresAt().isBefore(Instant.now())) {
			throw new AuthTokenException("RESET_TOKEN_EXPIRED", "Reset token expired");
		}
		tok.setConsumedAt(Instant.now());
		passwordResetTokenRepository.save(tok);
		UserEntity u = tok.getUser();
		u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
		userAccountPort.save(u);
	}

	private LoginResponse tokensForUser(UserEntity u) {
		String roleName = u.getRole() != null ? u.getRole().name() : UserRole.USER.name();
		String access = jwtService.createAccessToken(u.getId(), u.getEmail(), roleName);
		String refresh = jwtService.createRefreshToken(u.getId());
		AuthUserDto dto = new AuthUserDto(u.getId(), u.getEmail(), u.getName(), roleName);
		return new LoginResponse(access, refresh, dto);
	}

	private void issueEmailConfirmation(UserEntity u, String locale) {
		String raw = randomToken();
		String hash = sha256Hex(raw);
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setUser(u);
		tok.setTokenHash(hash);
		tok.setCreatedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(Math.max(60, emailConfirmationTtlSeconds)));
		emailConfirmationTokenRepository.save(tok);
		if (mailEnabled) {
			String link = webappBaseUrl + "/" + resolveLocale(locale) + "/confirm-email?token=" + urlEncode(raw);
			saveOutbox("EMAIL_CONFIRMATION", u.getEmail(), "Confirm your email", "Confirm your email: " + link);
		}
	}

	private void issuePasswordReset(UserEntity u, String requestIp, String requestUserAgent, String locale) {
		String raw = randomToken();
		String hash = sha256Hex(raw);
		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setUser(u);
		tok.setTokenHash(hash);
		tok.setCreatedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(Math.max(60, passwordResetTtlSeconds)));
		if (requestIp != null && !requestIp.isBlank()) {
			tok.setRequestIp(requestIp.trim());
		}
		if (requestUserAgent != null && !requestUserAgent.isBlank()) {
			tok.setRequestUserAgent(requestUserAgent.trim());
		}
		passwordResetTokenRepository.save(tok);
		if (mailEnabled) {
			String link = webappBaseUrl + "/" + resolveLocale(locale) + "/reset-password?token=" + urlEncode(raw);
			saveOutbox("PASSWORD_RESET", u.getEmail(), "Reset your password", "Reset your password: " + link);
		}
	}

	private void saveOutbox(String purpose, String recipient, String subject, String bodyText) {
		MailOutboxEntity e = new MailOutboxEntity();
		e.setCreatedAt(Instant.now());
		e.setPurpose(purpose);
		e.setRecipient(recipient);
		e.setSubject(subject);
		e.setBodyText(bodyText);
		mailOutboxRepository.save(e);
	}

	private String randomToken() {
		byte[] b = new byte[32];
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

	private void validateLegalAcceptance(RegisterRequest req) {
		if (!legalAcceptanceRequired) {
			return;
		}
		if (!Boolean.TRUE.equals(req.acceptedPrivacyPolicy()) || !Boolean.TRUE.equals(req.acceptedTerms())) {
			throw new AuthTokenException("LEGAL_ACCEPTANCE_REQUIRED", "Privacy policy and terms must be accepted");
		}
		String privacyVersion = trimToEmpty(req.privacyPolicyVersion());
		String termsVersion = trimToEmpty(req.termsVersion());
		if (privacyVersion.isEmpty() || termsVersion.isEmpty()) {
			throw new AuthTokenException("LEGAL_VERSION_REQUIRED", "Legal document version is required");
		}
		if (!requiredPrivacyPolicyVersion.isEmpty() && !requiredPrivacyPolicyVersion.equals(privacyVersion)) {
			throw new AuthTokenException("PRIVACY_VERSION_MISMATCH", "Privacy policy version mismatch");
		}
		if (!requiredTermsVersion.isEmpty() && !requiredTermsVersion.equals(termsVersion)) {
			throw new AuthTokenException("TERMS_VERSION_MISMATCH", "Terms version mismatch");
		}
	}

	private static String trimToEmpty(String raw) {
		return raw == null ? "" : raw.trim();
	}

	private static String trimToNull(String raw) {
		String value = trimToEmpty(raw);
		return value.isEmpty() ? null : value;
	}

	private String resolveLocale(String locale) {
		String normalized = trimToEmpty(locale).toLowerCase();
		if (normalized.matches("^[a-z]{2}(-[a-z]{2})?$")) {
			return normalized;
		}
		return defaultLocale;
	}

	private void applyLegalAcceptance(UserEntity user, RegisterRequest req) {
		String privacyVersion = trimToNull(req.privacyPolicyVersion());
		String termsVersion = trimToNull(req.termsVersion());
		if (Boolean.TRUE.equals(req.acceptedPrivacyPolicy()) || Boolean.TRUE.equals(req.acceptedTerms())) {
			Instant now = Instant.now();
			user.setPrivacyAcceptedAt(Boolean.TRUE.equals(req.acceptedPrivacyPolicy()) ? now : null);
			user.setTermsAcceptedAt(Boolean.TRUE.equals(req.acceptedTerms()) ? now : null);
			user.setPrivacyPolicyVersion(privacyVersion);
			user.setTermsVersion(termsVersion);
			return;
		}
		user.setPrivacyAcceptedAt(null);
		user.setTermsAcceptedAt(null);
		user.setPrivacyPolicyVersion(privacyVersion);
		user.setTermsVersion(termsVersion);
	}
}
