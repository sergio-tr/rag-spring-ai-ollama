package com.uniovi.rag.application.service.auth;

import com.uniovi.rag.interfaces.rest.auth.AuthTokenException;
import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.FeatureDisabledException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.EmailNotVerifiedException;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.ConfirmEmailRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ForgotPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.ResendConfirmationRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResetPasswordRequest;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.EmailConfirmationTokenEntity;
import com.uniovi.rag.infrastructure.persistence.EmailConfirmationTokenRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.PasswordResetTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.PasswordResetTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserAccountPort userAccountPort;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private EmailConfirmationTokenRepository emailConfirmationTokenRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private MailOutboxRepository mailOutboxRepository;

	private AuthService newService() {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				false,
				false,
				false,
				"http://localhost:3000",
				3600,
				3600,
				false,
				"",
				"",
				"en");
	}

	private AuthService newServicePasswordResetEnabled() {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				false,
				true,
				false,
				"http://localhost:3000",
				3600,
				3600,
				false,
				"",
				"",
				"en");
	}

	private AuthService newServiceEmailAndMailEnabled() {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				true,
				true,
				true,
				"http://localhost:3000/",
				3600,
				3600,
				false,
				"",
				"",
				"en");
	}

	private AuthService newServiceEmailConfirmationEnabled() {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				true,
				false,
				true,
				"http://localhost:3000/",
				3600,
				3600,
				false,
				"",
				"",
				"en");
	}

	private AuthService newServiceLegalRequired() {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				false,
				false,
				false,
				"http://localhost:3000",
				3600,
				3600,
				true,
				"v1",
				"v1",
				"en");
	}

	private AuthService newServiceEmailAndMailEnabledDefaultLocale(String defaultLocale) {
		return new AuthService(
				userAccountPort,
				passwordEncoder,
				jwtService,
				emailConfirmationTokenRepository,
				passwordResetTokenRepository,
				mailOutboxRepository,
				true,
				true,
				true,
				"http://localhost:3000/",
				3600,
				3600,
				false,
				"",
				"",
				defaultLocale);
	}

	private static RegisterRequest registerRequest(String email) {
		return new RegisterRequest("Name", email, "password123", "en", true, true, "v1", "v1");
	}

	private static RegisterRequest registerRequestWithLocale(String email, String locale) {
		return new RegisterRequest("Name", email, "password123", locale, true, true, "v1", "v1");
	}

	@Test
	void login_success_returnsTokens() {
		UUID id = UUID.randomUUID();
		UserEntity u = mock(UserEntity.class);
		when(u.getId()).thenReturn(id);
		when(u.getEmail()).thenReturn("a@b.com");
		when(u.getName()).thenReturn("N");
		when(u.getPasswordHash()).thenReturn("encoded");
		when(u.getRole()).thenReturn(UserRole.USER);

		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("pw", "encoded")).thenReturn(true);
		when(jwtService.createAccessToken(any(), any(), any())).thenReturn("acc");
		when(jwtService.createRefreshToken(any())).thenReturn("ref");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		LoginResponse res = newService().login(new LoginRequest("a@b.com", "pw"));

		assertThat(res.accessToken()).isEqualTo("acc");
		assertThat(res.user().email()).isEqualTo("a@b.com");
		verify(userAccountPort).save(u);
	}

	@Test
	void login_wrongPassword_throws() {
		UserEntity u = mock(UserEntity.class);
		when(u.getPasswordHash()).thenReturn("encoded");
		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);

		assertThatThrownBy(() -> newService().login(new LoginRequest("a@b.com", "bad")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_unverifiedUserWhenConfirmationEnabled_throws() {
		UserEntity u = mock(UserEntity.class);
		when(u.isEmailVerified()).thenReturn(false);
		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));

		assertThatThrownBy(() -> newServiceEmailConfirmationEnabled().login(new LoginRequest("a@b.com", "pw")))
				.isInstanceOf(EmailNotVerifiedException.class);
	}

	@Test
	void register_duplicateEmail_throws() {
		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(mock(UserEntity.class)));

		assertThatThrownBy(() ->
						newService().register(registerRequest("a@b.com")))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void refresh_invalidToken_throws() {
		when(jwtService.parseRefreshTokenUserId("bad")).thenThrow(new RuntimeException());

		assertThatThrownBy(() -> newService().refresh(new RefreshRequest("bad")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void refresh_success_returnsTokens() {
		UUID id = UUID.randomUUID();
		when(jwtService.parseRefreshTokenUserId("rt")).thenReturn(id);
		UserEntity u = mock(UserEntity.class);
		when(u.getId()).thenReturn(id);
		when(u.getEmail()).thenReturn("a@b.com");
		when(u.getName()).thenReturn("N");
		when(u.getRole()).thenReturn(UserRole.USER);
		when(userAccountPort.findById(id)).thenReturn(Optional.of(u));
		when(jwtService.createAccessToken(any(), any(), any())).thenReturn("a");
		when(jwtService.createRefreshToken(any())).thenReturn("r");

		LoginResponse res = newService().refresh(new RefreshRequest("rt"));

		assertThat(res.accessToken()).isEqualTo("a");
	}

	@Test
	void resetPassword_disabled_throws() {
		assertThatThrownBy(() -> newService().resetPassword(new ResetPasswordRequest("t", "password123")))
				.isInstanceOf(AuthTokenException.class);
	}

	@Test
	void resetPassword_valid_updatesPasswordAndConsumesToken() {
		UserEntity u = mock(UserEntity.class);
		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setUser(u);
		tok.setTokenHash("h");
		tok.setCreatedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(300));

		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServicePasswordResetEnabled().resetPassword(new ResetPasswordRequest("raw", "password123"));

		verify(passwordResetTokenRepository).save(any());
		verify(userAccountPort).save(eq(u));
		verify(passwordEncoder).encode("password123");
	}

	@Test
	void register_withEmailConfirmationEnabled_issuesConfirmationToken() {
		when(userAccountPort.findByEmailIgnoreCase("new@user.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		RegisterResponse res = newServiceEmailAndMailEnabled().register(registerRequest("new@user.com"));

		assertThat(res.status()).isEqualTo("PENDING_EMAIL_VERIFICATION");
		assertThat(res.login()).isNull();
		verify(jwtService, never()).createAccessToken(any(), any(), any());
		verify(jwtService, never()).createRefreshToken(any());
		verify(emailConfirmationTokenRepository).save(any(EmailConfirmationTokenEntity.class));
		ArgumentCaptor<MailOutboxEntity> outbox = ArgumentCaptor.forClass(MailOutboxEntity.class);
		verify(mailOutboxRepository).save(outbox.capture());
		assertThat(outbox.getValue().getPurpose()).isEqualTo("EMAIL_CONFIRMATION");
		assertThat(outbox.getValue().getBodyText()).contains("/en/confirm-email?token=");
	}

	@Test
	void register_withEmailConfirmationEnabled_createsUserAsUnverified() {
		when(userAccountPort.findByEmailIgnoreCase("new@user.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServiceEmailAndMailEnabled().register(registerRequest("new@user.com"));

		ArgumentCaptor<UserEntity> savedUsers = ArgumentCaptor.forClass(UserEntity.class);
		verify(userAccountPort, times(1)).save(savedUsers.capture());
		UserEntity created = savedUsers.getValue();
		assertThat(created.isEmailVerified()).isFalse();
		assertThat(created.getEmailVerifiedAt()).isNull();
	}

	@Test
	void confirmEmail_whenDisabled_throwsFeatureDisabled() {
		assertThatThrownBy(() -> newService().confirmEmail(new ConfirmEmailRequest("token")))
				.isInstanceOf(FeatureDisabledException.class);
	}

	@Test
	void confirmEmail_consumedToken_throwsInvalidCredentials() {
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setConsumedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(300));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("CONFIRM_TOKEN_ALREADY_USED"));
	}

	@Test
	void confirmEmail_expiredToken_throwsInvalidCredentials() {
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().minusSeconds(1));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("CONFIRM_TOKEN_EXPIRED"));
	}

	@Test
	void confirmEmail_unknownToken_throwsConfirmTokenInvalid() {
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("CONFIRM_TOKEN_INVALID"));
	}

	@Test
	void confirmEmail_secondUseFails_afterSuccessfulConsumption() {
		UserEntity u = mock(UserEntity.class);
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setUser(u);
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().plusSeconds(60));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw"));

		assertThat(tok.getConsumedAt()).isNotNull();

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("CONFIRM_TOKEN_ALREADY_USED"));
	}

	@Test
	void confirmEmail_validToken_marksUserVerified() {
		UserEntity u = mock(UserEntity.class);
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setUser(u);
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().plusSeconds(60));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw"));

		verify(emailConfirmationTokenRepository).save(any(EmailConfirmationTokenEntity.class));
		verify(u).setEmailVerified(true);
		verify(userAccountPort).save(u);
	}

	@Test
	void forgotPassword_enabled_existingUser_issuesResetTokenAndMail() {
		UserEntity user = mock(UserEntity.class);
		when(user.getEmail()).thenReturn("new@user.com");
		when(userAccountPort.findByEmailIgnoreCase("new@user.com")).thenReturn(Optional.of(user));

		newServiceEmailAndMailEnabled().forgotPassword(
				new ForgotPasswordRequest("new@user.com", "es"), "127.0.0.1", "test-agent");

		verify(passwordResetTokenRepository).save(any(PasswordResetTokenEntity.class));
		ArgumentCaptor<MailOutboxEntity> outbox = ArgumentCaptor.forClass(MailOutboxEntity.class);
		verify(mailOutboxRepository).save(outbox.capture());
		assertThat(outbox.getValue().getPurpose()).isEqualTo("PASSWORD_RESET");
		assertThat(outbox.getValue().getBodyText()).contains("/es/reset-password?token=");
	}

	@Test
	void forgotPassword_unknownEmail_doesNotPersistTokenOrMail() {
		when(userAccountPort.findByEmailIgnoreCase("ghost@user.com")).thenReturn(Optional.empty());

		newServiceEmailAndMailEnabled().forgotPassword(
				new ForgotPasswordRequest("ghost@user.com", "en"), "127.0.0.1", "test-agent");

		verify(passwordResetTokenRepository, never()).save(any(PasswordResetTokenEntity.class));
		verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
	}

	@Test
	void forgotPassword_whenDisabled_doesNotPersistResetOrMail_evenWhenUserExists() {
		newService().forgotPassword(
				new ForgotPasswordRequest("exists@user.com", "en"), "127.0.0.1", "test-agent");

		verify(userAccountPort, never()).findByEmailIgnoreCase(any());
		verify(passwordResetTokenRepository, never()).save(any(PasswordResetTokenEntity.class));
		verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
	}

	@Test
	void resetPassword_invalidToken_throws() {
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> newServicePasswordResetEnabled()
						.resetPassword(new ResetPasswordRequest("raw", "password123")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("RESET_TOKEN_INVALID"));
	}

	@Test
	void resetPassword_reusedToken_throws() {
		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setConsumedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(300));
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServicePasswordResetEnabled()
						.resetPassword(new ResetPasswordRequest("raw", "password123")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("RESET_TOKEN_ALREADY_USED"));
	}

	@Test
	void resetPassword_expiredToken_throws() {
		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().minusSeconds(1));
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServicePasswordResetEnabled()
						.resetPassword(new ResetPasswordRequest("raw", "password123")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("RESET_TOKEN_EXPIRED"));
	}

	@Test
	void resetPassword_secondUseFails_afterSuccessfulConsumption() {
		UserEntity u = mock(UserEntity.class);
		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setUser(u);
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().plusSeconds(300));
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));
		when(passwordEncoder.encode("newpw")).thenReturn("encoded-new");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServicePasswordResetEnabled().resetPassword(new ResetPasswordRequest("raw", "newpw"));

		assertThat(tok.getConsumedAt()).isNotNull();

		assertThatThrownBy(() -> newServicePasswordResetEnabled()
						.resetPassword(new ResetPasswordRequest("raw", "newpw")))
				.isInstanceOfSatisfying(AuthTokenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("RESET_TOKEN_ALREADY_USED"));
	}

	@Test
	void resetPassword_then_login_oldPasswordFails_newPasswordSucceeds() {
		UUID id = UUID.randomUUID();
		UserEntity u = mock(UserEntity.class);
		when(u.getId()).thenReturn(id);
		lenient().when(u.getEmail()).thenReturn("user@test.com");
		when(u.getName()).thenReturn("U");
		when(u.getRole()).thenReturn(UserRole.USER);

		PasswordResetTokenEntity tok = new PasswordResetTokenEntity();
		tok.setUser(u);
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().plusSeconds(300));
		when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));
		when(passwordEncoder.encode("newpw")).thenReturn("encoded-new");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServicePasswordResetEnabled().resetPassword(new ResetPasswordRequest("raw", "newpw"));

		when(userAccountPort.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(u));
		when(u.getPasswordHash()).thenReturn("encoded-new");
		when(passwordEncoder.matches("oldpw", "encoded-new")).thenReturn(false);

		assertThatThrownBy(() -> newServicePasswordResetEnabled().login(new LoginRequest("user@test.com", "oldpw")))
				.isInstanceOf(InvalidCredentialsException.class);

		when(passwordEncoder.matches("newpw", "encoded-new")).thenReturn(true);
		when(jwtService.createAccessToken(any(), any(), any())).thenReturn("acc");
		when(jwtService.createRefreshToken(any())).thenReturn("ref");

		LoginResponse loggedIn =
				newServicePasswordResetEnabled().login(new LoginRequest("user@test.com", "newpw"));
		assertThat(loggedIn.accessToken()).isEqualTo("acc");
	}

	@Test
	void register_withNullLocale_usesDefaultLocaleInConfirmationLink() {
		when(userAccountPort.findByEmailIgnoreCase("loc@user.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServiceEmailAndMailEnabledDefaultLocale("fr").register(
				registerRequestWithLocale("loc@user.com", null));

		ArgumentCaptor<MailOutboxEntity> outbox = ArgumentCaptor.forClass(MailOutboxEntity.class);
		verify(mailOutboxRepository).save(outbox.capture());
		assertThat(outbox.getValue().getBodyText()).contains("/fr/confirm-email?token=");
	}

	@Test
	void register_withInvalidLocaleString_fallsBackToDefaultLocaleInConfirmationLink() {
		when(userAccountPort.findByEmailIgnoreCase("loc2@user.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password123")).thenReturn("encoded");
		when(userAccountPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

		newServiceEmailAndMailEnabledDefaultLocale("de").register(
				registerRequestWithLocale("loc2@user.com", "###not-a-locale###"));

		ArgumentCaptor<MailOutboxEntity> outbox = ArgumentCaptor.forClass(MailOutboxEntity.class);
		verify(mailOutboxRepository).save(outbox.capture());
		assertThat(outbox.getValue().getBodyText()).contains("/de/confirm-email?token=");
	}

	@Test
	void resendConfirmation_unverifiedUser_issuesNewTokenAndMail() {
		UserEntity u = mock(UserEntity.class);
		when(u.isEmailVerified()).thenReturn(false);
		when(u.getEmail()).thenReturn("new@user.com");
		when(userAccountPort.findByEmailIgnoreCase("new@user.com")).thenReturn(Optional.of(u));

		newServiceEmailAndMailEnabled().resendConfirmation(new ResendConfirmationRequest("new@user.com", "es"));

		verify(emailConfirmationTokenRepository).save(any(EmailConfirmationTokenEntity.class));
		verify(mailOutboxRepository).save(any(MailOutboxEntity.class));
	}

	@Test
	void resendConfirmation_unverifiedUser_twice_createsTwoOutboxRows() {
		UserEntity u = mock(UserEntity.class);
		when(u.isEmailVerified()).thenReturn(false);
		when(u.getEmail()).thenReturn("twice@user.com");
		when(userAccountPort.findByEmailIgnoreCase("twice@user.com")).thenReturn(Optional.of(u));

		AuthService svc = newServiceEmailAndMailEnabled();
		svc.resendConfirmation(new ResendConfirmationRequest("twice@user.com", "en"));
		svc.resendConfirmation(new ResendConfirmationRequest("twice@user.com", "en"));

		verify(mailOutboxRepository, times(2)).save(any(MailOutboxEntity.class));
	}

	@Test
	void resendConfirmation_verifiedUser_doesNotIssueNewToken() {
		UserEntity u = mock(UserEntity.class);
		when(u.isEmailVerified()).thenReturn(true);
		when(userAccountPort.findByEmailIgnoreCase("verified@user.com")).thenReturn(Optional.of(u));

		newServiceEmailAndMailEnabled().resendConfirmation(new ResendConfirmationRequest("verified@user.com", "en"));

		verify(emailConfirmationTokenRepository, never()).save(any(EmailConfirmationTokenEntity.class));
		verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
	}

	@Test
	void resendConfirmation_unknownEmail_doesNothing() {
		when(userAccountPort.findByEmailIgnoreCase("missing@user.com")).thenReturn(Optional.empty());

		newServiceEmailAndMailEnabled().resendConfirmation(new ResendConfirmationRequest("missing@user.com", "en"));

		verify(emailConfirmationTokenRepository, never()).save(any(EmailConfirmationTokenEntity.class));
		verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
	}

	@Test
	void register_whenLegalRequiredAndNotAccepted_throws() {
		assertThatThrownBy(() -> newServiceLegalRequired().register(
				new RegisterRequest("Name", "user@example.com", "password123", "en", false, true, "v1", "v1")))
				.isInstanceOf(AuthTokenException.class)
				.hasMessageContaining("Privacy policy and terms must be accepted");
	}

	@Test
	void register_whenLegalRequiredAndVersionMismatch_throws() {
		assertThatThrownBy(() -> newServiceLegalRequired().register(
				new RegisterRequest("Name", "user@example.com", "password123", "en", true, true, "v2", "v1")))
				.isInstanceOf(AuthTokenException.class)
				.hasMessageContaining("Privacy policy version mismatch");
	}
}
