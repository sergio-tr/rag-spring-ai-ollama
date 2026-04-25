package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.OauthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OauthIdentityRepository extends JpaRepository<OauthIdentityEntity, UUID> {

    Optional<OauthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}

