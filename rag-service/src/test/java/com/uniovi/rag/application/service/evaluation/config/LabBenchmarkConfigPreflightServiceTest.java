package com.uniovi.rag.application.service.evaluation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
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
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;

    private LabBenchmarkConfigPreflightService service;

    @BeforeEach
    void setUp() {
        LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService =
                new LabIndexSnapshotCompatibilityService(corpusAvailabilityGate, knowledgePipelineOrchestrator);
        service =
                new LabBenchmarkConfigPreflightService(
                        new RagFeatureConfiguration(),
                        knowledgeSnapshotService,
                        embeddingSpaceGuard,
                        indexSnapshotCompatibilityService,
                        evaluationCorpusApplicationService,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory);
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
    void ragRejectsP11() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P11"), null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(
                                                LabRuntimeConfigReasonCodes.PRESET_ADAPTIVE_ROUTING_BENCHMARK_NOT_SUPPORTED));
    }

    @Test
    void ragRejectsP12() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P12"), null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(
                                                LabRuntimeConfigReasonCodes.PRESET_JUDGE_ENHANCED_BENCHMARK_NOT_SUPPORTED));
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
    void ragAcceptsP0ThroughP10() {
        List<String> presets = List.of("P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9", "P10");
        StartBenchmarkRunRequest req = ragRequest(presets, "nomic-embed-text");
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
        assertThat(result.requestedPresetCodes()).containsExactlyElementsOf(presets);
    }

    @Test
    void ragAcceptsP0WithoutStrictIndexCheck() {
        StartBenchmarkRunRequest req = ragRequest(List.of("P0"), "nomic-embed-text");
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
        assertThat(result.requestedPresetCodes()).containsExactly("P0");
        assertThat(result.strictIndexCheck()).isFalse();
    }

    @Test
    void ragAcceptsP8WithoutActiveIndexWhenAutoReindexEnabled() {
        UUID corpusId = UUID.randomUUID();
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
                        List.of(), List.of(), null, null);
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void ragRejectsP8WithoutActiveIndexWhenAutoReindexDisabled() {
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
                        false,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(), List.of(), null, null);
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
    void ragDefersIndexCheckWhenAutoReindexEnabledEvenWithIncompatibleSnapshot() {
        UUID corpusId = UUID.randomUUID();
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
                        List.of(), List.of(), null, null);
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
        assertThat(result.details()).containsEntry("indexPreflight", "DEFERRED_AUTO_REINDEX");
    }

    @Test
    void ragRejectsP1WhenActiveSnapshotHasZeroVectorRowsAndAutoReindexDisabled() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity snap = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(snap.getId()).thenReturn(snapshotId);
        when(snap.getIndexProfileJsonb()).thenReturn(Map.of());
        when(snap.getSignatureHash()).thenReturn("sig-current");
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.of(snap));
        KnowledgeDocumentEntity doc = Mockito.mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(doc)));
        ProjectIndexProfile profile =
                new ProjectIndexProfile(
                        indexProjectId,
                        MaterializationStrategy.HYBRID,
                        true,
                        "meta-v1",
                        "mxbai-embed-large",
                        400,
                        10,
                        ProjectIndexProfile.computeProfileHash(
                                MaterializationStrategy.HYBRID, true, "meta-v1", "mxbai-embed-large", 400, 10),
                        java.time.Instant.now(),
                        java.time.Instant.now());
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(profile);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(any(), any(), any())).thenReturn(profile);
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(false);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(any(), any(), any(), any()))
                .thenReturn("sig-current");

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
                        List.of("P1"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(), List.of(), null, null);
        assertThatThrownBy(() -> service.validateOrThrow(userId, BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX));
    }

    @Test
    void ragRejectsHybridPresetWithChunkOnlySnapshotWhenAutoReindexDisabled() {
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
                        false,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(), List.of(), null, null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH));
    }

    @Test
    void embeddingRejectsDimensionMismatchForAnySelectedModel() {
        doNothing().when(embeddingSpaceGuard).assertFitsPhysicalVectorColumn(eq("mxbai-embed-large:latest"));
        doThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "EMBEDDING_DIMENSION_MISMATCH: incompatible"))
                .when(embeddingSpaceGuard)
                .assertFitsPhysicalVectorColumn(eq("nomic-embed-text:latest"));
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
                        null,
                        List.of(),
                        List.of("mxbai-embed-large:latest", "nomic-embed-text:latest"),
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
                        List.of(), List.of(), null, null);
        assertThatThrownBy(() -> service.validateOrThrow(UUID.randomUUID(), BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabRuntimeConfigReasonCodes.EMBEDDING_DIMENSION_MISMATCH));
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
                        List.of(), List.of(), null, null);
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
                List.of(), List.of(), null, null);
    }
}
