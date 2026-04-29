package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.ConfirmEmailRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ForgotPasswordRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.ResetPasswordRequest;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.EmailConfirmationTokenEntity;
import com.uniovi.rag.infrastructure.persistence.EmailConfirmationTokenRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.PasswordResetTokenRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.PasswordResetTokenEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
				"no-reply@local.test",
				"http://localhost:3000",
				3600,
				3600);
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
				"no-reply@local.test",
				"http://localhost:3000",
				3600,
				3600);
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
				"no-reply@local.test",
				"http://localhost:3000/",
				3600,
				3600);
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
	void register_duplicateEmail_throws() {
		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(mock(UserEntity.class)));

		assertThatThrownBy(() ->
						newService().register(new RegisterRequest("N", "a@b.com", "password123")))
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
				.isInstanceOf(InvalidCredentialsException.class);
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

		newServiceEmailAndMailEnabled().register(new RegisterRequest("Name", "new@user.com", "password123"));

		verify(emailConfirmationTokenRepository).save(any(EmailConfirmationTokenEntity.class));
		verify(mailOutboxRepository).save(any());
	}

	@Test
	void confirmEmail_whenDisabled_isNoop() {
		newService().confirmEmail(new ConfirmEmailRequest("token"));
	}

	@Test
	void confirmEmail_consumedToken_throwsInvalidCredentials() {
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setConsumedAt(Instant.now());
		tok.setExpiresAt(Instant.now().plusSeconds(300));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void confirmEmail_expiredToken_throwsInvalidCredentials() {
		EmailConfirmationTokenEntity tok = new EmailConfirmationTokenEntity();
		tok.setConsumedAt(null);
		tok.setExpiresAt(Instant.now().minusSeconds(1));
		when(emailConfirmationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tok));

		assertThatThrownBy(() -> newServiceEmailAndMailEnabled().confirmEmail(new ConfirmEmailRequest("raw")))
				.isInstanceOf(InvalidCredentialsException.class);
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

		newServiceEmailAndMailEnabled().forgotPassword(new ForgotPasswordRequest("new@user.com"));

		verify(passwordResetTokenRepository).save(any(PasswordResetTokenEntity.class));
		verify(mailOutboxRepository).save(any());
	}
}
