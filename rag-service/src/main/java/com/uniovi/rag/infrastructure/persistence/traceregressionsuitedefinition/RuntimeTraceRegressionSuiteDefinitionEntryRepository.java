package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuntimeTraceRegressionSuiteDefinitionEntryRepository
        extends JpaRepository<RuntimeTraceRegressionSuiteDefinitionEntryEntity, UUID> {

    List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> findByDefinition_IdOrderByPositionAsc(UUID definitionId);

    void deleteByDefinition_Id(UUID definitionId);

    long countByDefinition_Id(UUID definitionId);
}
