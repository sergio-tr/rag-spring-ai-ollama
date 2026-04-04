package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.interfaces.rest.auth.DuplicateEmailException;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RefreshRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

	private final UserAccountPort userAccountPort;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserAccountPort userAccountPort, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userAccountPort = userAccountPort;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public LoginResponse register(RegisterRequest req) {
		if (userAccountPort.findByEmailIgnoreCase(req.email()).isPresent()) {
			throw new DuplicateEmailException();
		}
		UserEntity u = UserEntityFactory.newRegisteredUser(
				req.email().trim().toLowerCase(),
				req.name().trim(),
				passwordEncoder.encode(req.password()));
		u = userAccountPort.save(u);
		return tokensForUser(u);
	}

	@Transactional
	public LoginResponse login(LoginRequest req) {
		UserEntity u = userAccountPort.findByEmailIgnoreCase(req.email().trim().toLowerCase())
				.orElseThrow(InvalidCredentialsException::new);
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
		return tokensForUser(u);
	}

	private LoginResponse tokensForUser(UserEntity u) {
		String roleName = u.getRole() != null ? u.getRole().name() : UserRole.USER.name();
		String access = jwtService.createAccessToken(u.getId(), u.getEmail(), roleName);
		String refresh = jwtService.createRefreshToken(u.getId());
		AuthUserDto dto = new AuthUserDto(u.getId(), u.getEmail(), u.getName(), roleName);
		return new LoginResponse(access, refresh, dto);
	}
}
