package com.uniovi.rag.application.service.evaluation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.embedding.EmbeddingOptionsValidator;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import java.time.Instant;
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

/** Regression for stock RAG campaign preflight with bge-m3 evaluation corpus snapshots. */
@ExtendWith(MockitoExtension.class)
class FeatureRequiresReindexCompatibilityTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private EvaluationModelCatalogService evaluationModelCatalogService;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess;

    private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private LabBenchmarkConfigPreflightService service;

    @BeforeEach
    void setUp() {
        labIndexProfileOverrideFactory = new LabIndexProfileOverrideFactory();
        LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService =
                new LabIndexSnapshotCompatibilityService(
                        corpusAvailabilityGate, knowledgePipelineOrchestrator, snapshotProfileAccess);
        service =
                new LabBenchmarkConfigPreflightService(
                        new RagFeatureConfiguration(),
                        knowledgeSnapshotService,
                        embeddingSpaceGuard,
                        indexSnapshotCompatibilityService,
                        evaluationCorpusApplicationService,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory,
                        corpusAvailabilityGate,
                        evaluationModelCatalogService,
                        Mockito.mock(EmbeddingOptionsValidator.class),
                        knowledgeIndexSnapshotRepository,
                        snapshotProfileAccess);
        doNothing().when(evaluationModelCatalogService).assertHasCompatibleEmbeddingWhenRequired(any());
        doNothing()
                .when(evaluationModelCatalogService)
                .assertEmbeddingCompatibleWithVectorStore(any(), eq("bge-m3"));
        doNothing().when(evaluationModelCatalogService).assertChatModelInCatalog(any(), eq("gemma4:12b"));
    }

    @Test
    void acceptsP3P8P10WhenBgeM3GroupSnapshotsMatchRequestEmbedding() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID chunkSnapshotId = UUID.fromString("3bc97dd6-908c-4828-b777-3f81cd3e312f");
        UUID hybridSnapshotId = UUID.fromString("40dc0824-223f-4df6-ab3d-13cf0a915322");

        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(), List.of()));

        ProjectIndexProfile deploymentDefault = profile(indexProjectId, "mxbai-embed-large:latest");
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(deploymentDefault);

        String chunkSig =
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.CHUNK_LEVEL, false, "meta-v1", "bge-m3", 400, 10);
        String hybridSig =
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.HYBRID, true, "meta-v1", "bge-m3", 400, 10);
        KnowledgeIndexSnapshotEntity chunkSnapshot =
                snapshot(chunkSnapshotId, "CHUNK_LEVEL", false, chunkSig, "bge-m3");
        KnowledgeIndexSnapshotEntity hybridSnapshot =
                snapshot(hybridSnapshotId, "HYBRID", true, hybridSig, "bge-m3");

        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.of(hybridSnapshot));
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId))
                .thenReturn(List.of(hybridSnapshot, chunkSnapshot));
        when(snapshotProfileAccess.resolveProfileJsonb(any())).thenAnswer(inv -> inv.getArgument(0, KnowledgeIndexSnapshotEntity.class).getIndexProfileJsonb());
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, chunkSnapshotId)).thenReturn(true);
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, hybridSnapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(3, ProjectIndexProfile.class).profileHash());

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        corpusId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "phase52b",
                        null,
                        null,
                        null,
                        null,
                        List.of("P3", "P8", "P10"),
                        "gemma4:12b",
                        "bge-m3",
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
                        List.of(),
                        List.of(),
                        null,
                        null,
                        Map.of());

        assertThatCode(() -> service.validateOrThrow(userId, BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .doesNotThrowAnyException();
        LabBenchmarkConfigPreflightResult result =
                service.validateOrThrow(userId, BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(result.passed()).isTrue();
        assertThat(result.details()).containsEntry("indexPreflightEmbeddingModelId", "bge-m3");
    }

    private static ProjectIndexProfile profile(UUID projectId, String embeddingModelId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.CHUNK_LEVEL,
                false,
                "meta-v1",
                embeddingModelId,
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.CHUNK_LEVEL, false, "meta-v1", embeddingModelId, 400, 10),
                Instant.now(),
                Instant.now());
    }

    private static KnowledgeIndexSnapshotEntity snapshot(
            UUID id, String materialization, boolean metadata, String signature, String embeddingModelId) {
        KnowledgeIndexSnapshotEntity entity = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getSignatureHash()).thenReturn(signature);
        when(entity.getIndexProfileJsonb())
                .thenReturn(
                        Map.of(
                                "materializationStrategy", materialization,
                                "supportsMetadata", metadata,
                                "embeddingModelId", embeddingModelId,
                                "chunkMaxChars", 400));
        return entity;
    }
}
