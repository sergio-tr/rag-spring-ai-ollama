package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceReplayEligibilityResolverTest {

    private final RuntimeTraceReplayEligibilityResolver resolver = new RuntimeTraceReplayEligibilityResolver();

    private static final UUID UID = UUID.randomUUID();
    private static final UUID PID = UUID.randomUUID();
    private static final UUID CID = UUID.randomUUID();
    private static final UUID MID = UUID.randomUUID();
    private static final UUID SNAP = UUID.randomUUID();
    private static final UUID KID = UUID.randomUUID();

    @Test
    void direct_workflow_supported_when_workflow_present_and_knowledge_not_required() {
        var dto =
                dto(
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name(),
                        "DirectLlmWorkflow",
                        Map.of("usedKnowledgeSnapshotIds", List.of()),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().eligible()).isTrue();
        assertThat(r.pin().orElseThrow().workflowName()).isEqualTo("DirectLlmWorkflow");
    }

    @Test
    void retrieval_requires_knowledge_ids_in_payload() {
        var dto =
                dto(
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name(),
                        "ChunkDenseRagWorkflow",
                        Map.of("usedKnowledgeSnapshotIds", List.of()),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().eligible()).isFalse();
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_KNOWLEDGE_BINDING);
    }

    @Test
    void retrieval_supported_with_knowledge_ids() {
        var dto =
                dto(
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name(),
                        "ChunkDenseRagWorkflow",
                        Map.of("usedKnowledgeSnapshotIds", List.of(KID.toString())),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().eligible()).isTrue();
        assertThat(r.pin().orElseThrow().knowledgeSnapshotIds()).containsExactly(KID);
    }

    @Test
    void deterministic_tool_requires_tool_kind_in_payload() {
        var dto =
                dto(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        "",
                        Map.of("deterministicToolKind", ""),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_TOOL_BINDING);
    }

    @Test
    void unsupported_function_calling_route() {
        var dto =
                dto(
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                        "DirectLlmWorkflow",
                        Map.of(),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY);
    }

    @Test
    void unsupported_advisor_route() {
        var dto =
                dto(
                        AdaptiveRouteKind.ADVISOR_ROUTE.name(),
                        "ChunkDenseRagWorkflow",
                        Map.of("usedKnowledgeSnapshotIds", List.of(KID.toString())),
                        "NOT_NEEDED",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY);
    }

    @Test
    void unsupported_clarification_dependent() {
        var dto =
                dto(
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name(),
                        "DirectLlmWorkflow",
                        Map.of(),
                        "ASKED_CLARIFICATION",
                        false);
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_CLARIFICATION_DEPENDENT);
    }

    @Test
    void unsupported_judge_attempted() {
        var dto =
                dto(
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name(),
                        "DirectLlmWorkflow",
                        Map.of(),
                        "NOT_NEEDED",
                        true);
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_JUDGE_AFFECTED);
    }

    @Test
    void unsupported_missing_linkage() {
        var dto =
                new RuntimeExecutionTraceDetailDto(
                        UUID.randomUUID(),
                        Instant.now(),
                        UID,
                        PID,
                        null,
                        MID,
                        "c",
                        SNAP,
                        "h",
                        "DirectLlmWorkflow",
                        true,
                        "OK",
                        true,
                        "OK",
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name(),
                        false,
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        "NOT_NEEDED",
                        RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION,
                        Map.of(),
                        List.of());
        var r = resolver.resolve(dto);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_LINKAGE);
    }

    @Test
    void unsupported_schema_version() {
        var dto =
                dto(
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name(),
                        "DirectLlmWorkflow",
                        Map.of(),
                        "NOT_NEEDED",
                        false);
        var bad =
                new RuntimeExecutionTraceDetailDto(
                        dto.id(),
                        dto.createdAt(),
                        dto.userId(),
                        dto.projectId(),
                        dto.conversationId(),
                        dto.messageId(),
                        dto.correlationId(),
                        dto.resolvedConfigSnapshotId(),
                        dto.configHash(),
                        dto.workflowName(),
                        dto.memoryAttempted(),
                        dto.memoryOutcome(),
                        dto.routingAttempted(),
                        dto.routingOutcome(),
                        dto.routingRouteKind(),
                        dto.routingFallbackApplied(),
                        dto.routingWorkflowSelectorInvoked(),
                        dto.deterministicToolOutcome(),
                        dto.functionCallingOutcome(),
                        dto.advisorOutcome(),
                        dto.judgeAttempted(),
                        dto.judgeCandidateSource(),
                        dto.judgeFinalOutcome(),
                        dto.judgeFinalAnswerFromRetry(),
                        dto.clarificationOutcome(),
                        999,
                        dto.executionTraceJson(),
                        dto.stagesJson());
        var r = resolver.resolve(bad);
        assertThat(r.decision().unsupportedOutcome().orElseThrow())
                .isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_TRACE_SCHEMA_VERSION);
    }

    private static RuntimeExecutionTraceDetailDto dto(
            String routeKind,
            String workflowName,
            Map<String, Object> execJson,
            String clarificationOutcome,
            boolean judgeAttempted) {
        return new RuntimeExecutionTraceDetailDto(
                UUID.randomUUID(),
                Instant.now(),
                UID,
                PID,
                CID,
                MID,
                "corr",
                SNAP,
                "hash",
                workflowName,
                true,
                "OK",
                true,
                "OK",
                routeKind,
                false,
                false,
                "",
                "",
                "",
                judgeAttempted,
                "",
                "",
                false,
                clarificationOutcome,
                RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION,
                execJson,
                List.of());
    }
}
