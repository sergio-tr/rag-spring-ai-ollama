package com.uniovi.rag.application.service.runtime.tracequery;

import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final MessageRepository messageRepository;

    public RuntimeTraceQueryService(
            RuntimeExecutionTraceRepository repository,
            ProjectAccessService projectAccessService,
            MessageRepository messageRepository
    ) {
        this.repository = repository;
        this.projectAccessService = projectAccessService;
        this.messageRepository = messageRepository;
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
                        .orElseGet(
                                () -> resolveUserMessageIdFromAssistant(conversationId, messageId)
                                        .flatMap(mid -> repository.findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(userId, mid))
                                        .orElseThrow(() -> new NotFoundException("trace not found")));
        if (e.getConversationId() != null && !e.getConversationId().equals(conversationId)) {
            throw new NotFoundException("trace not found");
        }
        return toDetailDto(e);
    }

    /**
     * Runtime traces are persisted against the USER message id (the request turn). Some clients ask for the
     * assistant message id; when possible, we resolve it to the immediately preceding USER message in the same
     * conversation (seq-1).
     */
    private Optional<UUID> resolveUserMessageIdFromAssistant(UUID conversationId, UUID messageId) {
        if (messageRepository == null || conversationId == null || messageId == null) {
            return Optional.empty();
        }
        Optional<MessageEntity> m = messageRepository.findById(messageId);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        MessageEntity msg = m.get();
        if (msg.getConversation() == null || msg.getConversation().getId() == null) {
            return Optional.empty();
        }
        if (!conversationId.equals(msg.getConversation().getId())) {
            return Optional.empty();
        }
        if (msg.getRole() != MessageRole.ASSISTANT) {
            return Optional.empty();
        }
        int prevSeq = msg.getSeq() - 1;
        if (prevSeq < 0) {
            return Optional.empty();
        }
        return messageRepository.findByConversation_IdAndSeqAndDeletedAtIsNull(conversationId, prevSeq)
                .filter(prev -> prev.getRole() == MessageRole.USER)
                .map(MessageEntity::getId);
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
        if (m == null || m.isEmpty()) {
            return Map.of();
        }
        // Map.copyOf rejects null values; traces may contain nulls.
        return new LinkedHashMap<>(m);
    }

    private static List<Map<String, Object>> safeListMap(List<Map<String, Object>> m) {
        if (m == null || m.isEmpty()) {
            return List.of();
        }
        // List.copyOf rejects null elements; keep the original ordering while tolerating null maps.
        return new ArrayList<>(m);
    }
}

