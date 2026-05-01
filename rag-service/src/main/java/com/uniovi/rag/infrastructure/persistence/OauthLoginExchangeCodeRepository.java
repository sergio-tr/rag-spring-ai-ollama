package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginExchangeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OauthLoginExchangeCodeRepository extends JpaRepository<OauthLoginExchangeCodeEntity, UUID> {

    Optional<OauthLoginExchangeCodeEntity> findByCodeHash(String codeHash);
}

