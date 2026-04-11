package com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuntimeTraceRegressionSuiteRunEntryRepository
        extends JpaRepository<RuntimeTraceRegressionSuiteRunEntryEntity, UUID> {

    List<RuntimeTraceRegressionSuiteRunEntryEntity> findAllByRunIdOrderByEntryOrderAsc(UUID runId);
}
