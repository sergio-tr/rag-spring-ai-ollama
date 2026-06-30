package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResponseSourcesBackfillTest {

    @Test
    void backfillsFromRetrievalDiagnosticsWhenResponseSourcesEmpty() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        Optional.empty(),
                        "",
                        3,
                        1,
                        2,
                        2,
                        2,
                        2,
                        2,
                        0,
                        0,
                        false,
                        List.of("chunk-a"),
                        List.of("chunk-a"),
                        Optional.empty(),
                        100,
                        80,
                        false,
                        2);
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                                "answer",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                List.of(UUID.randomUUID()),
                                null,
                                Optional.of(diagnostics),
                                List.of())
                        .withResponseSources(List.of());

        List<Map<String, Object>> sources = ResponseSourcesBackfill.resolve(result);

        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("chunkId")).isEqualTo("chunk-a");
    }

    @Test
    void preservesExistingResponseSources() {
        List<Map<String, Object>> existing =
                List.of(Map.of("documentId", "doc-1", "snippet", "text"));
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                                "answer",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                List.of(),
                                null,
                                Optional.empty(),
                                List.of())
                        .withResponseSources(existing);

        assertThat(ResponseSourcesBackfill.resolve(result)).isEqualTo(existing);
    }
}
