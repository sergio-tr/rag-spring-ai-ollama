package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	@InjectMocks
	private AuthService authService;

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

		LoginResponse res = authService.login(new LoginRequest("a@b.com", "pw"));

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

		assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "bad")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void register_duplicateEmail_throws() {
		when(userAccountPort.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(mock(UserEntity.class)));

		assertThatThrownBy(() ->
						authService.register(new RegisterRequest("N", "a@b.com", "password123")))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void refresh_invalidToken_throws() {
		when(jwtService.parseRefreshTokenUserId("bad")).thenThrow(new RuntimeException());

		assertThatThrownBy(() -> authService.refresh(new RefreshRequest("bad")))
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

		LoginResponse res = authService.refresh(new RefreshRequest("rt"));

		assertThat(res.accessToken()).isEqualTo("a");
	}
}
