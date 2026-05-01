package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.OauthLoginStateTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthLoginStateTokenRepository extends JpaRepository<OauthLoginStateTokenEntity, UUID> {
    Optional<OauthLoginStateTokenEntity> findByStateHash(String stateHash);
}

