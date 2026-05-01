package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AllowedModelRepository extends JpaRepository<AllowedModelEntity, UUID> {

    Optional<AllowedModelEntity> findByNameAndType(String name, AllowedModelType type);
}
