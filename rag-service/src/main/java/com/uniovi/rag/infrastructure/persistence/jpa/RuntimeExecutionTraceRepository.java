package com.uniovi.rag.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RuntimeExecutionTraceRepository extends JpaRepository<RuntimeExecutionTraceEntity, UUID> {
}

