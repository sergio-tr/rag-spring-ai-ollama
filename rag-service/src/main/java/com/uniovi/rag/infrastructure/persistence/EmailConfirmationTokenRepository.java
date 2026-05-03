package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EmailConfirmationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationTokenEntity, UUID> {

    Optional<EmailConfirmationTokenEntity> findByTokenHash(String tokenHash);
}

