package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperFactualVerifierTest {

    @Test
    void mapsVerifierFieldsFromStages() {
        ExecutionTrace trace =
                new ExecutionTrace(
                        List.of(
                                new ExecutionStageTrace(
                                        "factual_verify",
                                        1L,
                                        ExecutionStageOutcome.FAILED,
                                        "verifierAttempted=true verifierPassed=false verifierFailureReason=NEGATIVE_FALSE_POSITIVE verifierFailures=NEGATIVE_FALSE_POSITIVE verifierRevisionAttempted=false verifierForcedAbstention=false constraintCheckPassed=false"),
                                new ExecutionStageTrace(
                                        "final_answer_source",
                                        0L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "finalAnswerSource=FORCED_ABSTENTION")),
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        Optional.empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        0,
                        List.of(),
                        "NEGATIVE_EVIDENCE",
                        100,
                        true,
                        "factual_verifier_forced_abstention");

        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);
        assertThat(telemetry).containsEntry("verifierAttempted", true);
        assertThat(telemetry).containsEntry("verifierPassed", false);
        assertThat(telemetry).containsEntry("verifierFailureReason", "NEGATIVE_FALSE_POSITIVE");
        assertThat(telemetry).containsEntry("finalAnswerSource", "FORCED_ABSTENTION");
        assertThat(telemetry).containsEntry("negativeEvidenceGuardTriggered", true);
    }

    @Test
    void mapsSkippedVerifierFieldsFromDateGuardStage() {
        ExecutionTrace trace =
                new ExecutionTrace(
                        List.of(
                                new ExecutionStageTrace(
                                        "factual_verify_skipped",
                                        0L,
                                        ExecutionStageOutcome.SKIPPED,
                                        "verifierAttempted=false verifierPassed=false verifierFailureReason=date_guard_abstention verifierRevisionAttempted=false verifierForcedAbstention=false constraintCheckPassed=false"),
                                new ExecutionStageTrace(
                                        "final_answer_source",
                                        0L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "finalAnswerSource=DATE_GUARD_ABSTENTION")),
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        Optional.empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        0,
                        List.of(),
                        "NUMERIC_OR_DATE",
                        100,
                        true,
                        "date_mismatch_no_exact_source");

        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);
        assertThat(telemetry).containsEntry("verifierAttempted", false);
        assertThat(telemetry).containsEntry("verifierPassed", false);
        assertThat(telemetry).containsEntry("verifierFailureReason", "date_guard_abstention");
        assertThat(telemetry).containsEntry("finalAnswerSource", "DATE_GUARD_ABSTENTION");
    }

    @Test
    void mapsSkippedVerifierFieldsFromNoContextStage() {
        ExecutionTrace trace =
                new ExecutionTrace(
                        List.of(
                                new ExecutionStageTrace(
                                        "factual_verify_skipped",
                                        0L,
                                        ExecutionStageOutcome.SKIPPED,
                                        "verifierAttempted=false verifierPassed=false verifierFailureReason=no_retrieved_context verifierRevisionAttempted=false verifierForcedAbstention=false constraintCheckPassed=false"),
                                new ExecutionStageTrace(
                                        "final_answer_source",
                                        0L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "finalAnswerSource=GENERATED")),
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        Optional.empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        0,
                        List.of(),
                        "NEGATIVE_EVIDENCE",
                        100,
                        true,
                        "");

        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);
        assertThat(telemetry).containsEntry("verifierAttempted", false);
        assertThat(telemetry).containsEntry("verifierPassed", false);
        assertThat(telemetry).containsEntry("verifierFailureReason", "no_retrieved_context");
        assertThat(telemetry).containsEntry("finalAnswerSource", "GENERATED");
        assertThat(telemetry).containsEntry("negativeEvidenceGuardTriggered", true);
    }

    @Test
    void toolFinalAnswerSource_fromToolTrace() {
        ExecutionTrace toolTrace =
                new ExecutionTrace(
                        List.of(),
                        "deterministic-tool",
                        false,
                        false,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        true,
                        "PRIMARY_ROUTE_SELECTED",
                        "DETERMINISTIC_TOOL_ROUTE",
                        false,
                        "",
                        true,
                        DeterministicToolOutcome.EXECUTED_SUCCESS.name(),
                        "COUNT_DOCUMENTS",
                        "",
                        false,
                        FunctionCallingOutcome.NOT_APPLICABLE.name(),
                        "",
                        false,
                        Optional.empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        0,
                        List.of(),
                        "",
                        0,
                        false,
                        "");

        Map<String, Object> telemetry = ToolExecutionTelemetryMapper.fromTrace(toolTrace);
        assertThat(telemetry).containsEntry("finalAnswerSource", "TOOL_FINAL");
        assertThat(telemetry).doesNotContainKey("verifierAttempted");
    }

    @Test
    void functionFinalAnswerSource_fromFunctionTrace() {
        ExecutionTrace functionTrace =
                new ExecutionTrace(
                        List.of(),
                        "function-calling",
                        false,
                        false,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        true,
                        "PRIMARY_ROUTE_SELECTED",
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                        false,
                        "",
                        false,
                        "",
                        "",
                        "",
                        true,
                        FunctionCallingOutcome.EXECUTED_SUCCESS.name(),
                        "COUNT_DOCUMENTS_TOOL",
                        true,
                        Optional.empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        0,
                        List.of(),
                        "",
                        0,
                        false,
                        "");

        Map<String, Object> telemetry = ToolExecutionTelemetryMapper.fromTrace(functionTrace);
        assertThat(telemetry).containsEntry("finalAnswerSource", "FUNCTION_FINAL");
        assertThat(telemetry).doesNotContainKey("verifierAttempted");
    }
}
