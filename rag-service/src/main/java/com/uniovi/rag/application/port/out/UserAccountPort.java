package com.uniovi.rag.application.port.out;

import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for user account persistence (auth flows).
 */
public interface UserAccountPort {

	Optional<UserEntity> findByEmailIgnoreCase(String email);

	Optional<UserEntity> findById(UUID id);

	UserEntity save(UserEntity user);
}
