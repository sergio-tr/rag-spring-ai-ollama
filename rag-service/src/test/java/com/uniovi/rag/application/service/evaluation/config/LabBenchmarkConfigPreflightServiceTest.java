package com.uniovi.rag.application.service.evaluation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LabBenchmarkConfigPreflightServiceTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;

    private LabBenchmarkConfigPreflightService service;

    @BeforeEach
    void setUp() {
        service = new LabBenchmarkConfigPreflightService(
                new RagFeatureConfiguration(), knowledgeSnapshotService, embeddingSpaceGuard);
    }

    @Test
    void ragRejectsEmptyPresetList() {
        StartBenchmarkRunRequest req = ragRequest(List.of(), null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.EXPERIMENTAL_PRESET_CODES_EMPTY));
    }

    @Test
    void ragRejectsP13() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P13"), null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED));
    }

    @Test
    void ragRejectsUnknownPreset() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P99"), null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.UNSUPPORTED_PRESET));
    }

    @Test
    void ragAcceptsP0WithAutoReindex() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P0"), "nomic-embed-text");
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
        assertThat(result.requestedPresetCodes()).containsExactly("P0");
        assertThat(result.strictIndexCheck()).isTrue();
    }

    @Test
    void ragRejectsP8WithoutActiveIndexEvenWhenAutoReindexEnabled() {
        UUID corpusId = UUID.randomUUID();
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.empty());
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        corpusId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of("P8"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        true,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException rse = (ResponseStatusException) ex;
                            assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(rse.getReason()).isEqualTo(LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
                        });
    }

    @Test
    void ragRejectsHybridPresetWithChunkOnlySnapshotEvenWhenAutoReindexEnabled() {
        UUID corpusId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity snap = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(snap.getId()).thenReturn(UUID.randomUUID());
        when(snap.getIndexProfileJsonb()).thenReturn(Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", true));
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.of(snap));
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        corpusId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of("P8"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        true,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH));
    }

    @Test
    void embeddingRejectsDimensionMismatch() {
        doThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "EMBEDDING_DIMENSION_MISMATCH: incompatible"))
                .when(embeddingSpaceGuard)
                .assertFitsPhysicalVectorColumn(any());
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        "bad-embed",
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.EMBEDDING_DIMENSION_MISMATCH));
    }

    private static StartBenchmarkRunRequest ragRequest(List<String> presets, String embeddingModelId) {
        return new StartBenchmarkRunRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                EvaluationRunKind.PRODUCT_EXPLORATION,
                "n",
                null,
                null,
                null,
                null,
                presets,
                null,
                embeddingModelId,
                List.of(),
                List.of(),
                false,
                null,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                List.of());
    }
}
