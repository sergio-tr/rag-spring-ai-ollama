package com.uniovi.rag.application.service.runtime.tracequery;

import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeTraceQueryServiceTest {

    @Test
    void listConversationTraceSummaries_requiresConversationOwnership_andCapsPageSize() {
        RuntimeExecutionTraceRepository repo = mock(RuntimeExecutionTraceRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        RuntimeTraceQueryService service = new RuntimeTraceQueryService(repo, access);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        doReturn(null).when(access).requireConversationForUser(userId, conversationId);

        RuntimeExecutionTraceEntity row = RuntimeExecutionTraceEntity.newForInsert();
        row.setUserId(userId);
        row.setProjectId(UUID.randomUUID());
        row.setConversationId(conversationId);
        row.setCorrelationId("corr");
        row.setWorkflowName("wf");
        row.setMemoryOutcome("");
        row.setRoutingOutcome("");
        row.setRoutingRouteKind("");
        row.setDeterministicToolOutcome("");
        row.setFunctionCallingOutcome("");
        row.setAdvisorOutcome("");
        row.setJudgeCandidateSource("");
        row.setJudgeFinalOutcome("");
        row.setClarificationOutcome("");
        Page<RuntimeExecutionTraceEntity> page = new PageImpl<>(List.of(row), PageRequest.of(0, 100), 1);

        when(repo.findByUserIdAndConversationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(userId), eq(conversationId), any(), any(), any()))
                .thenReturn(page);

        var out =
                service.listConversationTraceSummaries(
                        userId,
                        conversationId,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1000);

        verify(access, times(1)).requireConversationForUser(userId, conversationId);
        assertThat(out.getSize()).isEqualTo(100);
        assertThat(out.getContent()).hasSize(1);
    }

    @Test
    void getTraceDetailById_scopesByUser() {
        RuntimeExecutionTraceRepository repo = mock(RuntimeExecutionTraceRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        RuntimeTraceQueryService service = new RuntimeTraceQueryService(repo, access);

        UUID userId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        RuntimeExecutionTraceEntity row = RuntimeExecutionTraceEntity.newForInsert();
        row.setUserId(UUID.randomUUID());
        row.setProjectId(UUID.randomUUID());
        row.setCorrelationId("corr");
        row.setWorkflowName("wf");
        row.setMemoryOutcome("");
        row.setRoutingOutcome("");
        row.setRoutingRouteKind("");
        row.setDeterministicToolOutcome("");
        row.setFunctionCallingOutcome("");
        row.setAdvisorOutcome("");
        row.setJudgeCandidateSource("");
        row.setJudgeFinalOutcome("");
        row.setClarificationOutcome("");
        row.setSchemaVersion(1);
        row.setExecutionTraceJsonb(java.util.Map.of());
        row.setStagesJsonb(List.of());

        when(repo.findById(traceId)).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> service.getTraceDetailById(userId, traceId))
                .isInstanceOf(NotFoundException.class);
    }
}

