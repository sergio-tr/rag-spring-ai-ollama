package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.ExecutionOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;

class ParentCandidateSnapshotTest {

    @Test
    void capture_recordsParentPresetAndHashes() {
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                        "parent answer", "ChunkDenseRagWorkflow", true, false, List.of(UUID.randomUUID()), "none", List.of());
        ExecutionOutcome outcome = new ExecutionOutcome(result, ExecutionTrace.placeholder());

        ParentCandidateSnapshot snapshot =
                ParentCandidateSnapshot.capture(
                        RagExperimentalPresetCode.P7,
                        outcome,
                        Optional.empty(),
                        false);

        assertThat(snapshot.parentFinalAnswerText()).isEqualTo("parent answer");
        assertThat(snapshot.parentMatcherVisibleAnswer()).isEqualTo("parent answer");
        assertThat(snapshot.parentFinalAnswerSource()).isEqualTo(ParentFinalAnswerSources.PARENT_P7_FINAL);
        assertThat(snapshot.parentFinalAnswerHash()).isEqualTo(ParentAnswerFingerprint.sha256Hex("parent answer"));
    }

    @Test
    void toPreservedOutcome_returnsSameOutcomeWhenAnswerUnchanged() {
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                        "verbatim", "ChunkDenseRagWorkflow", true, false, List.of(UUID.randomUUID()), "none", List.of());
        ExecutionOutcome outcome = new ExecutionOutcome(result, ExecutionTrace.placeholder());
        ParentCandidateSnapshot snapshot =
                ParentCandidateSnapshot.capture(
                        RagExperimentalPresetCode.P7,
                        outcome,
                        Optional.empty(),
                        false);

        assertThat(snapshot.toPreservedOutcome()).isSameAs(outcome);
    }

    @Test
    void toPreservedOutcome_rewritesAnswerWhenOutcomeMutatedAfterCapture() {
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                        "original", "ChunkDenseRagWorkflow", true, false, List.of(UUID.randomUUID()), "none", List.of());
        ExecutionOutcome outcome = new ExecutionOutcome(result, ExecutionTrace.placeholder());
        ParentCandidateSnapshot snapshot =
                ParentCandidateSnapshot.capture(
                        RagExperimentalPresetCode.P7,
                        outcome,
                        Optional.empty(),
                        false);

        RagExecutionResult mutated =
                RagExecutionResult.withPlaceholderTrace(
                        "mutated", "ChunkDenseRagWorkflow", true, false, List.of(UUID.randomUUID()), "none", List.of());
        ExecutionOutcome mutatedOutcome = new ExecutionOutcome(mutated, outcome.trace());

        assertThat(mutatedOutcome.result().answerText()).isEqualTo("mutated");
        assertThat(snapshot.toPreservedOutcome().result().answerText()).isEqualTo("original");
    }
}
