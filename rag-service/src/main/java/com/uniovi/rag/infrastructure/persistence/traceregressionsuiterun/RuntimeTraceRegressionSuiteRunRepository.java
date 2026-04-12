package com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeTraceRegressionSuiteRunRepository extends JpaRepository<RuntimeTraceRegressionSuiteRunEntity, UUID> {

    Optional<RuntimeTraceRegressionSuiteRunEntity> findByIdAndUserId(UUID id, UUID userId);

    List<RuntimeTraceRegressionSuiteRunEntity> findAllByUserIdOrderByCreatedAtDescIdAsc(UUID userId);

    List<RuntimeTraceRegressionSuiteRunEntity> findAllByUserIdAndDefinitionIdOrderByCreatedAtDescIdAsc(
            UUID userId, UUID definitionId);

    Optional<RuntimeTraceRegressionSuiteRunEntity> findByIdAndUserIdAndDefinitionId(
            UUID id, UUID userId, UUID definitionId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
