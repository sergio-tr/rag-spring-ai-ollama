package com.uniovi.rag.application.service.runtime.tracecomparison;

import com.uniovi.rag.application.service.runtime.tracereplay.PersistedRuntimeTraceJson;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceReplayComparatorTest {

    private final RuntimeTraceReplayComparator comparator = new RuntimeTraceReplayComparator();

    private static final UUID UID = UUID.randomUUID();
    private static final UUID PID = UUID.randomUUID();
    private static final UUID TID = UUID.randomUUID();
    private static final UUID CID = UUID.randomUUID();
    private static final UUID MID = UUID.randomUUID();
    private static final UUID SNAP = UUID.randomUUID();

    @Test
    void stageNamesFromJson_reads_stageName_and_skips_truncation_marker() {
        List<Map<String, Object>> stages =
                List.of(
                        Map.of("stageName", "A", "durationMs", 1L),
                        Map.of("truncated", true));
        assertThat(RuntimeTraceReplayComparator.stageNamesFromJson(stages)).containsExactly("A");
    }

    @Test
    void exact_match_when_aligned_scalars_and_stages_empty() {
        RuntimeExecutionTraceDetailDto dto = sampleDto(Map.of("functionCallingAttempted", false));
        ExecutionTrace replay = alignedTrace(dto);
        var mismatches = comparator.compare(dto, replay, Optional.of("answer"));
        assertThat(mismatches).isEmpty();
        assertThat(comparator.isCompatibleMismatchOnly(mismatches)).isFalse();
    }

    @Test
    void compatible_mismatch_only_when_classifier_triplet_differs() {
        Map<String, Object> j =
                Map.of(
                        "functionCallingAttempted",
                        false,
                        "classifierStatus",
                        "A",
                        "classifierLabel",
                        "L",
                        "ambiguityStatus",
                        "M");
        RuntimeExecutionTraceDetailDto dto = sampleDto(j);
        ExecutionTrace aligned = alignedTrace(dto);
        ExecutionTrace drift =
                new ExecutionTrace(
                        aligned.stages(),
                        aligned.workflowName(),
                        aligned.retrievalUsed(),
                        aligned.metadataUsed(),
                        aligned.usedKnowledgeSnapshotIds(),
                        aligned.usedResolvedConfigSnapshotId(),
                        aligned.usedConfigHash(),
                        aligned.queryPlanVersion(),
                        "B",
                        aligned.classifierLabel(),
                        aligned.expectedAnswerShape(),
                        aligned.ambiguityStatus(),
                        aligned.compatibilitySeverity(),
                        aligned.memoryAttempted(),
                        aligned.memoryOutcome(),
                        aligned.memoryHistoryLoaded(),
                        aligned.memoryCondensationAttempted(),
                        aligned.memoryCondensationUsed(),
                        aligned.memoryFallbackApplied(),
                        aligned.routingAttempted(),
                        aligned.routingOutcome(),
                        aligned.routingRouteKind(),
                        aligned.routingFallbackApplied(),
                        aligned.routingFallbackRouteKind(),
                        aligned.routingWorkflowSelectorInvoked(),
                        aligned.deterministicToolOutcome(),
                        aligned.deterministicToolKind(),
                        aligned.deterministicToolDetail(),
                        aligned.functionCallingAttempted(),
                        aligned.functionCallingOutcome(),
                        aligned.functionCallingToolKind(),
                        aligned.functionCallingShortCircuited(),
                        aligned.retrievalDiagnostics(),
                        aligned.advisorAttempted(),
                        aligned.advisorShortCircuitedContextPrep(),
                        aligned.advisorKindsExecuted(),
                        aligned.advisorOutcome(),
                        aligned.packedContextBlockCount(),
                        aligned.packedContextSourceCount(),
                        aligned.judgeAttempted(),
                        aligned.judgeCandidateSource(),
                        aligned.judgeRetryRequested(),
                        aligned.judgeRetryAttempted(),
                        aligned.judgeRetrySucceeded(),
                        aligned.judgeFinalOutcome(),
                        aligned.judgeFinalAnswerFromRetry(),
                        aligned.judgeKind(),
                        aligned.judgeDetail(),
                        aligned.clarificationAttempted(),
                        aligned.clarificationOutcome(),
                        aligned.clarificationPendingStateConsumed(),
                        aligned.clarificationQuestionAsked(),
                        aligned.originalQuery(),
                        aligned.retrievalQuery(),
                        aligned.packedContextPreview(),
                        aligned.sourceCount(),
                        aligned.retrievedDocumentNames());
        var mismatches = comparator.compare(dto, drift, Optional.of("x"));
        assertThat(mismatches).hasSize(1);
        assertThat(mismatches.getFirst().fieldPath()).isEqualTo("ExecutionTrace.classifierStatus");
        assertThat(comparator.isCompatibleMismatchOnly(mismatches)).isTrue();
    }

    @Test
    void structural_mismatch_when_workflow_differs() {
        RuntimeExecutionTraceDetailDto dto = sampleDto(Map.of("functionCallingAttempted", false));
        ExecutionTrace replay = alignedTrace(dto);
        ExecutionTrace drift =
                new ExecutionTrace(
                        replay.stages(),
                        "OTHER",
                        replay.retrievalUsed(),
                        replay.metadataUsed(),
                        replay.usedKnowledgeSnapshotIds(),
                        replay.usedResolvedConfigSnapshotId(),
                        replay.usedConfigHash(),
                        replay.queryPlanVersion(),
                        replay.classifierStatus(),
                        replay.classifierLabel(),
                        replay.expectedAnswerShape(),
                        replay.ambiguityStatus(),
                        replay.compatibilitySeverity(),
                        replay.memoryAttempted(),
                        replay.memoryOutcome(),
                        replay.memoryHistoryLoaded(),
                        replay.memoryCondensationAttempted(),
                        replay.memoryCondensationUsed(),
                        replay.memoryFallbackApplied(),
                        replay.routingAttempted(),
                        replay.routingOutcome(),
                        replay.routingRouteKind(),
                        replay.routingFallbackApplied(),
                        replay.routingFallbackRouteKind(),
                        replay.routingWorkflowSelectorInvoked(),
                        replay.deterministicToolOutcome(),
                        replay.deterministicToolKind(),
                        replay.deterministicToolDetail(),
                        replay.functionCallingAttempted(),
                        replay.functionCallingOutcome(),
                        replay.functionCallingToolKind(),
                        replay.functionCallingShortCircuited(),
                        replay.retrievalDiagnostics(),
                        replay.advisorAttempted(),
                        replay.advisorShortCircuitedContextPrep(),
                        replay.advisorKindsExecuted(),
                        replay.advisorOutcome(),
                        replay.packedContextBlockCount(),
                        replay.packedContextSourceCount(),
                        replay.judgeAttempted(),
                        replay.judgeCandidateSource(),
                        replay.judgeRetryRequested(),
                        replay.judgeRetryAttempted(),
                        replay.judgeRetrySucceeded(),
                        replay.judgeFinalOutcome(),
                        replay.judgeFinalAnswerFromRetry(),
                        replay.judgeKind(),
                        replay.judgeDetail(),
                        replay.clarificationAttempted(),
                        replay.clarificationOutcome(),
                        replay.clarificationPendingStateConsumed(),
                        replay.clarificationQuestionAsked(),
                        replay.originalQuery(),
                        replay.retrievalQuery(),
                        replay.packedContextPreview(),
                        replay.sourceCount(),
                        replay.retrievedDocumentNames());
        var mismatches = comparator.compare(dto, drift, Optional.of("a"));
        assertThat(mismatches.stream().anyMatch(m -> "workflowName".equals(m.fieldPath()))).isTrue();
        assertThat(comparator.isCompatibleMismatchOnly(mismatches)).isFalse();
    }

    @Test
    void answer_replay_absent_adds_mismatch_category() {
        RuntimeExecutionTraceDetailDto dto = sampleDto(Map.of("functionCallingAttempted", false));
        ExecutionTrace replay = alignedTrace(dto);
        var mismatches = comparator.compare(dto, replay, Optional.empty());
        assertThat(mismatches.stream().anyMatch(m -> m.category() == RuntimeTraceReplayMismatchCategory.ANSWER_TEXT_MISMATCH))
                .isTrue();
        assertThat(comparator.classifyAnswerStatus(Optional.empty()))
                .isEqualTo(RuntimeTraceReplayAnswerComparisonStatus.REPLAY_ABSENT);
    }

    private static RuntimeExecutionTraceDetailDto sampleDto(Map<String, Object> execJson) {
        return new RuntimeExecutionTraceDetailDto(
                TID,
                Instant.now(),
                UID,
                PID,
                CID,
                MID,
                "corr",
                SNAP,
                "hash",
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
                execJson,
                List.of());
    }

    private static ExecutionTrace alignedTrace(RuntimeExecutionTraceDetailDto d) {
        Map<String, Object> j = d.executionTraceJson();
        List<UUID> kids = PersistedRuntimeTraceJson.readUsedKnowledgeSnapshotIds(j);
        boolean fc = readBool(j, "functionCallingAttempted");
        return new ExecutionTrace(
                List.of(),
                d.workflowName(),
                false,
                false,
                kids,
                Optional.ofNullable(d.resolvedConfigSnapshotId()),
                Optional.ofNullable(emptyToNull(d.configHash())),
                "",
                str(j, "classifierStatus"),
                str(j, "classifierLabel"),
                "",
                str(j, "ambiguityStatus"),
                "",
                d.memoryAttempted(),
                nz(d.memoryOutcome()),
                false,
                false,
                false,
                false,
                d.routingAttempted(),
                nz(d.routingOutcome()),
                nz(d.routingRouteKind()),
                d.routingFallbackApplied(),
                "",
                d.routingWorkflowSelectorInvoked(),
                nz(d.deterministicToolOutcome()),
                nz(PersistedRuntimeTraceJson.readDeterministicToolKind(j)),
                "",
                fc,
                nz(d.functionCallingOutcome()),
                "",
                false,
                Optional.empty(),
                false,
                false,
                "",
                nz(d.advisorOutcome()),
                0,
                0,
                d.judgeAttempted(),
                nz(d.judgeCandidateSource()),
                false,
                false,
                false,
                nz(d.judgeFinalOutcome()),
                d.judgeFinalAnswerFromRetry(),
                "",
                "",
                false,
                nz(d.clarificationOutcome()),
                false,
                false,
                "",
                "",
                "",
                0,
                List.of());
    }

    private static String str(Map<String, Object> j, String key) {
        Object v = j.get(key);
        return v == null ? "" : v.toString();
    }

    private static boolean readBool(Map<String, Object> j, String key) {
        Object v = j.get(key);
        return v instanceof Boolean b && b;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
