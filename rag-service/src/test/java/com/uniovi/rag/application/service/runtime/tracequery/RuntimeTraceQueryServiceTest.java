package com.uniovi.rag.application.service.runtime.tracequery;

import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceQueryServiceTest {

    @Mock private RuntimeExecutionTraceRepository repository;
    @Mock private ProjectAccessService projectAccessService;

    @Test
    void listConversationTraceSummaries_whenWorkflowNameProvided_trimsAndUsesWorkflowRepo_andCapsSize() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        RuntimeExecutionTraceEntity row = entityForSummaryMapping(userId, conversationId);
        when(repository.findByUserIdAndConversationIdAndWorkflowNameAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(userId), eq(conversationId), eq("wf"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repository, projectAccessService);
        Page<?> out = svc.listConversationTraceSummaries(
                userId,
                conversationId,
                Optional.empty(),
                Optional.empty(),
                Optional.of(" wf "),
                -5,
                999);

        assertThat(out.getTotalElements()).isEqualTo(1);
        verify(projectAccessService).requireConversationForUser(userId, conversationId);

        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndConversationIdAndWorkflowNameAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(userId), eq(conversationId), eq("wf"), any(), any(), pageableCap.capture());
        assertThat(pageableCap.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCap.getValue().getPageSize()).isEqualTo(RuntimeTraceQueryService.MAX_PAGE_SIZE);
    }

    @Test
    void listTraceSummariesByCorrelationId_whenBlank_throws() {
        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repository, projectAccessService);
        assertThatThrownBy(() -> svc.listTraceSummariesByCorrelationId(
                UUID.randomUUID(), UUID.randomUUID(), "   ", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void getTraceDetailById_whenWrongUser_throwsNotFound() {
        UUID traceId = UUID.randomUUID();
        RuntimeExecutionTraceEntity row = entityOwnedBy(UUID.randomUUID());
        when(repository.findById(traceId)).thenReturn(Optional.of(row));

        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repository, projectAccessService);
        assertThatThrownBy(() -> svc.getTraceDetailById(UUID.randomUUID(), traceId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTraceDetailById_whenNullJsonFields_returnsEmptyCollections() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        RuntimeExecutionTraceEntity row = entityForDetailMapping(userId, conversationId);
        when(row.getId()).thenReturn(traceId);
        when(row.getExecutionTraceJsonb()).thenReturn(null);
        when(row.getStagesJsonb()).thenReturn(null);
        when(repository.findById(traceId)).thenReturn(Optional.of(row));

        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repository, projectAccessService);
        RuntimeExecutionTraceDetailDto dto = svc.getTraceDetailById(userId, traceId);

        assertThat(dto.executionTraceJson()).isEmpty();
        assertThat(dto.stagesJson()).isEmpty();
    }

    private static RuntimeExecutionTraceEntity entityOwnedBy(UUID userId) {
        RuntimeExecutionTraceEntity e = mock(RuntimeExecutionTraceEntity.class);
        when(e.getUserId()).thenReturn(userId);
        return e;
    }

    private static RuntimeExecutionTraceEntity entityForSummaryMapping(UUID userId, UUID conversationId) {
        RuntimeExecutionTraceEntity e = mock(RuntimeExecutionTraceEntity.class);
        when(e.getId()).thenReturn(UUID.randomUUID());
        when(e.getCreatedAt()).thenReturn(Instant.now());
        when(e.getUserId()).thenReturn(userId);
        when(e.getProjectId()).thenReturn(UUID.randomUUID());
        when(e.getConversationId()).thenReturn(conversationId);
        when(e.getMessageId()).thenReturn(UUID.randomUUID());
        when(e.getCorrelationId()).thenReturn("c");
        when(e.getResolvedConfigSnapshotId()).thenReturn(UUID.randomUUID());
        when(e.getConfigHash()).thenReturn("h");
        when(e.getWorkflowName()).thenReturn("wf");
        when(e.isMemoryAttempted()).thenReturn(false);
        when(e.getMemoryOutcome()).thenReturn("");
        when(e.isRoutingAttempted()).thenReturn(false);
        when(e.getRoutingOutcome()).thenReturn("");
        when(e.getRoutingRouteKind()).thenReturn("");
        when(e.isRoutingFallbackApplied()).thenReturn(false);
        when(e.isRoutingWorkflowSelectorInvoked()).thenReturn(false);
        when(e.getDeterministicToolOutcome()).thenReturn("");
        when(e.getFunctionCallingOutcome()).thenReturn("");
        when(e.getAdvisorOutcome()).thenReturn("");
        when(e.isJudgeAttempted()).thenReturn(false);
        when(e.getJudgeCandidateSource()).thenReturn("");
        when(e.getJudgeFinalOutcome()).thenReturn("");
        when(e.isJudgeFinalAnswerFromRetry()).thenReturn(false);
        when(e.getClarificationOutcome()).thenReturn("");
        return e;
    }

    private static RuntimeExecutionTraceEntity entityForDetailMapping(UUID userId, UUID conversationId) {
        RuntimeExecutionTraceEntity e = entityForSummaryMapping(userId, conversationId);
        when(e.getExecutionTraceJsonb()).thenReturn(Map.of());
        when(e.getStagesJsonb()).thenReturn(List.of());
        when(e.getSchemaVersion()).thenReturn(1);
        return e;
    }
}

