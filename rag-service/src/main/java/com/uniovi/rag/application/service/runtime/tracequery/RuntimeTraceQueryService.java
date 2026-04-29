package com.uniovi.rag.application.service.runtime.tracequery;

import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RuntimeTraceQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final RuntimeExecutionTraceRepository repository;
    private final ProjectAccessService projectAccessService;

    public RuntimeTraceQueryService(
            RuntimeExecutionTraceRepository repository,
            ProjectAccessService projectAccessService
    ) {
        this.repository = repository;
        this.projectAccessService = projectAccessService;
    }

    public Page<RuntimeExecutionTraceSummaryDto> listConversationTraceSummaries(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName,
            int page,
            int size
    ) {
        projectAccessService.requireConversationForUser(userId, conversationId);

        Pageable pageable = PageRequest.of(Math.max(0, page), capSize(size));
        Instant from = createdAtFrom.orElse(Instant.EPOCH);
        Instant to = createdAtTo.orElse(Instant.ofEpochMilli(Long.MAX_VALUE));
        String wf = workflowName.map(String::trim).orElse("");

        Page<RuntimeExecutionTraceEntity> rows;
        if (!wf.isBlank()) {
            rows =
                    repository.findByUserIdAndConversationIdAndWorkflowNameAndCreatedAtBetweenOrderByCreatedAtDesc(
                            userId, conversationId, wf, from, to, pageable);
        } else {
            rows =
                    repository.findByUserIdAndConversationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                            userId, conversationId, from, to, pageable);
        }
        return rows.map(RuntimeTraceQueryService::toSummaryDto);
    }

    public RuntimeExecutionTraceDetailDto getTraceDetailById(UUID userId, UUID traceId) {
        RuntimeExecutionTraceEntity e =
                repository.findById(traceId)
                        .filter(x -> userId != null && userId.equals(x.getUserId()))
                        .orElseThrow(() -> new NotFoundException("trace not found"));
        return toDetailDto(e);
    }

    public RuntimeExecutionTraceDetailDto getMostRecentTraceDetailByMessageId(
            UUID userId, UUID conversationId, UUID messageId) {
        projectAccessService.requireConversationForUser(userId, conversationId);
        RuntimeExecutionTraceEntity e =
                repository.findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(userId, messageId)
                        .orElseThrow(() -> new NotFoundException("trace not found"));
        if (e.getConversationId() != null && !e.getConversationId().equals(conversationId)) {
            throw new NotFoundException("trace not found");
        }
        return toDetailDto(e);
    }

    public Page<RuntimeExecutionTraceSummaryDto> listTraceSummariesByCorrelationId(
            UUID userId, UUID projectId, String correlationId, int page, int size) {
        projectAccessService.requireOwnedProject(userId, projectId);
        Pageable pageable = PageRequest.of(Math.max(0, page), capSize(size));
        String cid = correlationId != null ? correlationId.trim() : "";
        if (cid.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        return repository
                .findByUserIdAndProjectIdAndCorrelationIdOrderByCreatedAtDesc(userId, projectId, cid, pageable)
                .map(RuntimeTraceQueryService::toSummaryDto);
    }

    public Page<RuntimeExecutionTraceSummaryDto> listTraceSummariesByResolvedConfigSnapshotId(
            UUID userId, UUID projectId, UUID resolvedConfigSnapshotId, int page, int size) {
        projectAccessService.requireOwnedProject(userId, projectId);
        Pageable pageable = PageRequest.of(Math.max(0, page), capSize(size));
        return repository
                .findByUserIdAndProjectIdAndResolvedConfigSnapshotIdOrderByCreatedAtDesc(
                        userId, projectId, resolvedConfigSnapshotId, pageable)
                .map(RuntimeTraceQueryService::toSummaryDto);
    }

    private static int capSize(int requested) {
        if (requested <= 0) {
            return 25;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private static RuntimeExecutionTraceSummaryDto toSummaryDto(RuntimeExecutionTraceEntity e) {
        return new RuntimeExecutionTraceSummaryDto(
                e.getId(),
                e.getCreatedAt(),
                e.getUserId(),
                e.getProjectId(),
                e.getConversationId(),
                e.getMessageId(),
                e.getCorrelationId(),
                e.getResolvedConfigSnapshotId(),
                e.getConfigHash(),
                e.getWorkflowName(),
                e.isMemoryAttempted(),
                e.getMemoryOutcome(),
                e.isRoutingAttempted(),
                e.getRoutingOutcome(),
                e.getRoutingRouteKind(),
                e.isRoutingFallbackApplied(),
                e.isRoutingWorkflowSelectorInvoked(),
                e.getDeterministicToolOutcome(),
                e.getFunctionCallingOutcome(),
                e.getAdvisorOutcome(),
                e.isJudgeAttempted(),
                e.getJudgeCandidateSource(),
                e.getJudgeFinalOutcome(),
                e.isJudgeFinalAnswerFromRetry(),
                e.getClarificationOutcome());
    }

    private static RuntimeExecutionTraceDetailDto toDetailDto(RuntimeExecutionTraceEntity e) {
        return new RuntimeExecutionTraceDetailDto(
                e.getId(),
                e.getCreatedAt(),
                e.getUserId(),
                e.getProjectId(),
                e.getConversationId(),
                e.getMessageId(),
                e.getCorrelationId(),
                e.getResolvedConfigSnapshotId(),
                e.getConfigHash(),
                e.getWorkflowName(),
                e.isMemoryAttempted(),
                e.getMemoryOutcome(),
                e.isRoutingAttempted(),
                e.getRoutingOutcome(),
                e.getRoutingRouteKind(),
                e.isRoutingFallbackApplied(),
                e.isRoutingWorkflowSelectorInvoked(),
                e.getDeterministicToolOutcome(),
                e.getFunctionCallingOutcome(),
                e.getAdvisorOutcome(),
                e.isJudgeAttempted(),
                e.getJudgeCandidateSource(),
                e.getJudgeFinalOutcome(),
                e.isJudgeFinalAnswerFromRetry(),
                e.getClarificationOutcome(),
                e.getSchemaVersion(),
                safeMap(e.getExecutionTraceJsonb()),
                safeListMap(e.getStagesJsonb()));
    }

    private static Map<String, Object> safeMap(Map<String, Object> m) {
        return m == null ? Map.of() : Map.copyOf(m);
    }

    private static List<Map<String, Object>> safeListMap(List<Map<String, Object>> m) {
        return m == null ? List.of() : List.copyOf(m);
    }
}

