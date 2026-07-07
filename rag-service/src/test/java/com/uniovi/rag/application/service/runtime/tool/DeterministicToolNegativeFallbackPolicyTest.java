package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.AnswerFinality;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeterministicToolNegativeFallbackPolicyTest {

    @Test
    void prefersRetrievalWhenToolNegativeAndSourcesExist() {
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        "No se encontraron asistentes registrados.",
                        Map.of(),
                        List.of());
        RagExecutionResult retrieval =
                new RagExecutionResult(
                        "Asistieron 17 propietarios, presidió Jorge Moreno Navarro.",
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        ExecutionTrace.placeholder(),
                        "",
                        null,
                        false,
                        List.of(),
                        Optional.empty(),
                        List.of(Map.of("filename", "ACTA_5__ACTA_5_ATTENDEES.eval-gold.txt")),
                        AnswerFinality.STANDARD);

        assertThat(
                        DeterministicToolNegativeFallbackPolicy.shouldPreferRetrievalOverTool(
                                toolResult, retrieval, false))
                .isTrue();
    }

    @Test
    void doesNotPreferRetrievalWhenToolAnswerIsGrounded() {
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        "Dos actas mencionan el ascensor.",
                        Map.of("count", 2),
                        List.of());
        RagExecutionResult retrieval =
                new RagExecutionResult(
                        "Dos actas.",
                        "ChunkDenseRagWorkflow",
                        true,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        ExecutionTrace.placeholder(),
                        "",
                        null,
                        false,
                        List.of(),
                        Optional.empty(),
                        List.of(Map.of("filename", "ACTA 1.pdf")),
                        AnswerFinality.STANDARD);

        assertThat(
                        DeterministicToolNegativeFallbackPolicy.shouldPreferRetrievalOverTool(
                                toolResult, retrieval, false))
                .isFalse();
    }
}
