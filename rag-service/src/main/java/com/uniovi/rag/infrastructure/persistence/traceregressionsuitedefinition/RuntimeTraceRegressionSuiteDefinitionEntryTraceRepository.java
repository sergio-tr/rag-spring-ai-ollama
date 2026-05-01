package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository
        extends JpaRepository<RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity, UUID> {

    List<RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity> findByEntry_IdOrderByPositionAsc(UUID entryId);
}
