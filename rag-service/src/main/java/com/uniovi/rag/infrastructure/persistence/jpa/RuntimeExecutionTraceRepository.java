package com.uniovi.rag.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeExecutionTraceRepository extends JpaRepository<RuntimeExecutionTraceEntity, UUID> {

    Page<RuntimeExecutionTraceEntity> findByUserIdAndConversationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, UUID conversationId, Instant createdAtFrom, Instant createdAtTo, Pageable pageable);

    Page<RuntimeExecutionTraceEntity> findByUserIdAndConversationIdAndWorkflowNameAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId,
            UUID conversationId,
            String workflowName,
            Instant createdAtFrom,
            Instant createdAtTo,
            Pageable pageable);

    Optional<RuntimeExecutionTraceEntity> findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(UUID userId, UUID messageId);

    Page<RuntimeExecutionTraceEntity> findByUserIdAndProjectIdAndCorrelationIdOrderByCreatedAtDesc(
            UUID userId, UUID projectId, String correlationId, Pageable pageable);

    Page<RuntimeExecutionTraceEntity> findByUserIdAndProjectIdAndResolvedConfigSnapshotIdOrderByCreatedAtDesc(
            UUID userId, UUID projectId, UUID resolvedConfigSnapshotId, Pageable pageable);
}

