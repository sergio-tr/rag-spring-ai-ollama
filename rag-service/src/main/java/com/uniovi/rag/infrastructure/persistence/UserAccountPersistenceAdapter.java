package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Delegates {@link UserAccountPort} to Spring Data {@link UserRepository}.
 */
@Component
public class UserAccountPersistenceAdapter implements UserAccountPort {

	private final UserRepository userRepository;

	public UserAccountPersistenceAdapter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public Optional<UserEntity> findByEmailIgnoreCase(String email) {
		return userRepository.findByEmailIgnoreCase(email);
	}

	@Override
	public Optional<UserEntity> findById(UUID id) {
		return userRepository.findById(id);
	}

	@Override
	public UserEntity save(UserEntity user) {
		return userRepository.save(user);
	}
}
