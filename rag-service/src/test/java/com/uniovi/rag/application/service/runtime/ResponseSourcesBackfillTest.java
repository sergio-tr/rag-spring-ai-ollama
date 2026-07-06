package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceHolder;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.domain.runtime.advisor.PackedContextBlock;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResponseSourcesBackfillTest {

    @AfterEach
    void tearDown() {
        DeterministicToolEvidenceHolder.clear();
    }

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

    @Test
    void fromToolExecutionUsesMatchedMinutesEvidence() {
        DeterministicToolEvidenceHolder.set(
                new DeterministicToolEvidenceHolder.Evidence(
                        List.of(
                                new Minute(
                                        "doc-1",
                                        "ACTA 2.pdf",
                                        "2025-02-25",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(),
                                        0,
                                        Map.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        null)),
                        "context",
                        true));
        List<Map<String, Object>> sources =
                ResponseSourcesBackfill.fromToolExecution(Map.of("source", "MetadataBooleanQueryTool"), "SÍ");
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("filename")).isEqualTo("ACTA 2.pdf");
        assertThat(sources.getFirst().get("documentId")).isEqualTo("doc-1");
    }

    @Test
    void fromToolExecutionParsesActaFilenamesFromAnswerText() {
        List<Map<String, Object>> sources =
                ResponseSourcesBackfill.fromToolExecution(
                        Map.of(),
                        "Se menciona en ACTA 1.pdf y ACTA 6.pdf durante la reunión.");
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).get("filename")).isEqualTo("ACTA 1.pdf");
        assertThat(sources.get(1).get("filename")).isEqualTo("ACTA 6.pdf");
    }

    @Test
    void fromToolExecutionParsesBareActaNumberWithoutPdfExtension() {
        List<Map<String, Object>> sources =
                ResponseSourcesBackfill.fromToolExecution(
                        Map.of(), "Según el acta 3, los asistentes fueron 18 propietarios.");
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("filename")).isEqualTo("ACTA 3.pdf");
    }

    @Test
    void fromPackedContextDedupesByDocument() {
        UUID snapshotId = UUID.randomUUID();
        PackedContextSet packed =
                new PackedContextSet(
                        List.of(
                                new PackedContextBlock("doc-1", "doc-1", "chunk-a", snapshotId, "text a", 0, List.of()),
                                new PackedContextBlock("doc-1", "doc-1", "chunk-b", snapshotId, "text b", 1, List.of())),
                        "test",
                        1,
                        2,
                        List.of(),
                        "prompt");
        List<Map<String, Object>> sources = ResponseSourcesBackfill.fromPackedContext(packed);
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("documentId")).isEqualTo("doc-1");
        assertThat(sources.getFirst().get("snippet")).isEqualTo("text a");
    }

    @Test
    void backfillsFromAnswerTextWhenMetadataUsedWithoutRetrieval() {
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                                "Se menciona en ACTA 2.pdf durante la reunión.",
                                "deterministic-tool",
                                false,
                                true,
                                List.of(),
                                "deterministic-tool",
                                Optional.empty(),
                                List.of())
                        .withResponseSources(List.of());

        List<Map<String, Object>> sources = ResponseSourcesBackfill.resolve(result);

        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("filename")).isEqualTo("ACTA 2.pdf");
    }

    @Test
    void fromPackedContextExtractsFilenameDateAndSection() {
        UUID snapshotId = UUID.randomUUID();
        PackedContextSet packed =
                new PackedContextSet(
                        List.of(
                                new PackedContextBlock(
                                        "doc-1",
                                        "doc-1",
                                        "chunk-a",
                                        snapshotId,
                                        "ACTA 2.pdf del 2025-02-25. Lista de asistentes: Ana, Luis.",
                                        0,
                                        List.of())),
                        "test",
                        1,
                        1,
                        List.of(),
                        "prompt");
        List<Map<String, Object>> sources = ResponseSourcesBackfill.fromPackedContext(packed);
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("filename")).isEqualTo("ACTA 2.pdf");
        assertThat(sources.getFirst().get("detectedDate")).isEqualTo("2025-02-25");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) sources.getFirst().get("metadata");
        assertThat(metadata).containsEntry("sectionType", "attendees");
    }

    @Test
    void mergeDedupesByDocumentId() {
        List<Map<String, Object>> merged =
                ResponseSourcesBackfill.merge(
                        List.of(Map.of("documentId", "doc-1", "filename", "ACTA 1.pdf")),
                        List.of(Map.of("documentId", "doc-1", "snippet", "other")));
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().get("filename")).isEqualTo("ACTA 1.pdf");
    }
    @Test
    void fromCorpusDocumentsBuildsDocumentLevelSources() {
        List<Map<String, Object>> sources =
                ResponseSourcesBackfill.fromCorpusDocuments(
                        List.of(
                                new SnapshotCorpusAssembler.CorpusDocumentRef("doc-1", "ACTA 1.pdf"),
                                new SnapshotCorpusAssembler.CorpusDocumentRef("doc-2", "ACTA 2.pdf")));
        assertThat(sources).hasSize(2);
        assertThat(sources.getFirst().get("filename")).isEqualTo("ACTA 1.pdf");
    }
}
