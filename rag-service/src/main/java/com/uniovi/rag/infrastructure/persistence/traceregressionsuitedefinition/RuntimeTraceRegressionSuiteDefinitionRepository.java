package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeTraceRegressionSuiteDefinitionRepository
        extends JpaRepository<RuntimeTraceRegressionSuiteDefinitionEntity, UUID> {

    Optional<RuntimeTraceRegressionSuiteDefinitionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<RuntimeTraceRegressionSuiteDefinitionEntity> findAllByUserIdOrderByUpdatedAtDescNameAscIdAsc(UUID userId);
}
