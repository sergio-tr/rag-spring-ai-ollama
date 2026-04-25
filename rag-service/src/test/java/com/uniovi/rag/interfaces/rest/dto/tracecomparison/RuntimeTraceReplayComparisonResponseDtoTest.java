package com.uniovi.rag.interfaces.rest.dto.tracecomparison;

import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonReplayEcho;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceReplayComparisonResponseDtoTest {

    @Test
    void fromResult_truncates_summary_and_snippets_and_caps_mismatches() {
        String longSummary = "x".repeat(RuntimeTraceReplayComparisonResponseDto.MAX_SUMMARY_CHARS + 100);
        String longSnippet = "y".repeat(RuntimeTraceReplayComparisonMismatchDto.MAX_SNIPPET_CHARS + 50);
        List<RuntimeTraceReplayFieldMismatch> mismatches = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            mismatches.add(
                    new RuntimeTraceReplayFieldMismatch(
                            "f" + i,
                            RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH,
                            longSnippet,
                            longSnippet));
        }
        UUID tid = UUID.randomUUID();
        var result =
                new RuntimeTraceReplayComparisonResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        tid,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RuntimeTraceReplayMode.BY_TRACE_ID,
                        new RuntimeTraceReplayComparisonReplayEcho(Optional.of(tid), Optional.empty(), Optional.empty()),
                        RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH,
                        RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                        RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                        false,
                        longSummary,
                        mismatches,
                        "R1",
                        "R2",
                        "W1",
                        "W2");

        RuntimeTraceReplayComparisonResponseDto dto =
                RuntimeTraceReplayComparisonResponseDto.fromRuntimeTraceReplayComparisonResult(result);

        assertThat(dto.summary().length()).isEqualTo(RuntimeTraceReplayComparisonResponseDto.MAX_SUMMARY_CHARS);
        assertThat(dto.mismatches()).hasSize(RuntimeTraceReplayComparisonResponseDto.MAX_MISMATCHES);
        assertThat(dto.mismatches().getFirst().originalSnippet().length())
                .isEqualTo(RuntimeTraceReplayComparisonMismatchDto.MAX_SNIPPET_CHARS);
        assertThat(dto.originalRouteKind()).isEqualTo("R1");
        assertThat(dto.replayRouteKind()).isEqualTo("R2");
    }
}
