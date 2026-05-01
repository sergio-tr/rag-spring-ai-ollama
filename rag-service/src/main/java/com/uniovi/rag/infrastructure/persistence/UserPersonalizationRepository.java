package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPersonalizationRepository extends JpaRepository<UserPersonalizationEntity, UUID> {
}
