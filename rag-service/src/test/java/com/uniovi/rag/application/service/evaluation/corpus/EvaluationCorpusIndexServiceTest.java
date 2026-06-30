package com.uniovi.rag.application.service.evaluation.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationCorpusIndexServiceTest {

    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    @Mock private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private EvaluationCorpusStorageIntegrityService storageIntegrityService;

    private LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService;
    private EvaluationCorpusIndexService service;

    @BeforeEach
    void setUp() {
        indexSnapshotCompatibilityService =
                new LabIndexSnapshotCompatibilityService(corpusAvailabilityGate, knowledgePipelineOrchestrator, org.mockito.Mockito.mock(com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess.class));
        lenient().when(storageIntegrityService.hasReadyDocumentWithMissingBinary(any())).thenReturn(false);
        service =
                new EvaluationCorpusIndexService(
                        evaluationCorpusApplicationService,
                        knowledgePipelineOrchestrator,
                        knowledgeSnapshotService,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory,
                        resolvedConfigSnapshotApplicationService,
                        indexSnapshotCompatibilityService,
                        storageIntegrityService);
    }

    @Test
    void prepareDefaultIndex_failsWhenReadyDocumentBinaryMissing() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
        when(storageIntegrityService.hasReadyDocumentWithMissingBinary(List.of(readyDoc))).thenReturn(true);

        EvaluationCorpusIndexPrepareResult result = service.prepareDefaultIndex(userId, corpusId);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(LabCorpusReasonCodes.DOCUMENT_BINARY_MISSING);
        verify(knowledgePipelineOrchestrator, never()).rebuildScopeWithProfileOverride(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void prepareIndex_passesResolvedConfigSnapshotLinkageToRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID configSnapshotId = UUID.randomUUID();
        UUID builtSnapshotId = UUID.randomUUID();
        String configHash = "a".repeat(64);

        stubReadyCorpus(userId, corpusId, indexProjectId);
        ProjectIndexProfile profile = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(profile);
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(configSnapshotId, configHash));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(indexProjectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        isNull(),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        eq(configSnapshotId),
                        eq(configHash),
                        eq(profile)))
                .thenReturn(builtSnapshotId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtSnapshotId);
        when(built.getIndexProfileHash()).thenReturn(profile.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        EvaluationCorpusIndexPrepareResult result = service.prepareIndex(userId, corpusId);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtSnapshotId);
        assertThat(result.resolvedConfigSnapshotId()).isEqualTo(configSnapshotId);
    }

    @Test
    void prepareForPresetRequirements_reusesCompatibleSnapshotWithoutRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);

        KnowledgeIndexSnapshotEntity compatible = compatibleSnapshot(snapshotId, Map.of("materializationStrategy", "CHUNK_LEVEL"));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Predicate<KnowledgeIndexSnapshotEntity> predicate = inv.getArgument(1);
                            return predicate.test(compatible) ? Optional.of(compatible) : Optional.empty();
                        });

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                        RagExperimentalPresetCode.P3);
        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), eq(req), eq(LabPresetRunGroupKey.CHUNK_LEVEL)))
                .thenReturn(effective);
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), isNull(), eq(effective)))
                .thenReturn("sig-current");

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.CHUNK_LEVEL, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.REUSED);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(snapshotId);
        verify(knowledgePipelineOrchestrator, never())
                .rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void prepareForPresetRequirements_buildsWhenP1CompatibleSnapshotHasZeroVectorRows() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID emptySnapshotId = UUID.randomUUID();
        UUID builtId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);

        KnowledgeIndexSnapshotEntity emptyCompatible = compatibleSnapshot(emptySnapshotId, Map.of());
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Predicate<KnowledgeIndexSnapshotEntity> predicate = inv.getArgument(1);
                            return predicate.test(emptyCompatible) ? Optional.of(emptyCompatible) : Optional.empty();
                        });
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, emptySnapshotId)).thenReturn(false);

        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), any(), eq(LabPresetRunGroupKey.NO_INDEX)))
                .thenReturn(effective);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), isNull(), eq(effective)))
                .thenReturn("sig-current");
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "e".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), eq(effective)))
                .thenReturn(builtId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtId);
        when(built.getIndexProfileHash()).thenReturn(effective.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P1);

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.NO_INDEX, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtId);
    }

    @Test
    void prepareForPresetRequirements_buildsWhenCompatibleSnapshotHasZeroVectorRows() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID emptySnapshotId = UUID.randomUUID();
        UUID builtId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);

        KnowledgeIndexSnapshotEntity emptyCompatible =
                compatibleSnapshot(emptySnapshotId, Map.of("materializationStrategy", "DOCUMENT_LEVEL"));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Predicate<KnowledgeIndexSnapshotEntity> predicate = inv.getArgument(1);
                            return predicate.test(emptyCompatible) ? Optional.of(emptyCompatible) : Optional.empty();
                        });
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, emptySnapshotId)).thenReturn(false);

        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), any(), eq(LabPresetRunGroupKey.DOCUMENT_LEVEL)))
                .thenReturn(effective);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), isNull(), eq(effective)))
                .thenReturn("sig-current");
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "e".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), eq(effective)))
                .thenReturn(builtId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtId);
        when(built.getIndexProfileHash()).thenReturn(effective.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P2);

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.DOCUMENT_LEVEL, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtId);
    }

    @Test
    void prepareForPresetRequirements_buildsWhenSnapshotIsStale() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID staleSnapshotId = UUID.randomUUID();
        UUID builtId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);

        KnowledgeIndexSnapshotEntity stale =
                compatibleSnapshot(staleSnapshotId, Map.of("materializationStrategy", "CHUNK_LEVEL"), "sig-stale");
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Predicate<KnowledgeIndexSnapshotEntity> predicate = inv.getArgument(1);
                            return predicate.test(stale) ? Optional.of(stale) : Optional.empty();
                        });
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, staleSnapshotId)).thenReturn(true);

        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), any(), eq(LabPresetRunGroupKey.CHUNK_LEVEL)))
                .thenReturn(effective);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), isNull(), eq(effective)))
                .thenReturn("sig-current");
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "f".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), eq(effective)))
                .thenReturn(builtId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtId);
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId,
                        corpusId,
                        LabPresetRunGroupKey.CHUNK_LEVEL,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P3),
                        null,
                        true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtId);
    }

    @Test
    void prepareIndex_mapsResolvedConfigLinkageFailureToControlledError() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(defaultProfile(indexProjectId));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "c".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new IllegalArgumentException(
                                "resolved_config_snapshot linkage required for knowledge_index_snapshot"));

        assertThatThrownBy(() -> service.prepareIndex(userId, corpusId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE));
    }

    @Test
    void prepareForPresetRequirements_buildsWhenNoCompatibleSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID builtId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);
        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), any(), eq(LabPresetRunGroupKey.HYBRID_METADATA)))
                .thenReturn(effective);
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "d".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), eq(effective)))
                .thenReturn(builtId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtId);
        when(built.getIndexProfileHash()).thenReturn(effective.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                        RagExperimentalPresetCode.P8);

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.HYBRID_METADATA, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtId);
    }

    private static KnowledgeIndexSnapshotEntity compatibleSnapshot(UUID snapshotId, Map<String, Object> profile) {
        return compatibleSnapshot(snapshotId, profile, "sig-current");
    }

    private static KnowledgeIndexSnapshotEntity compatibleSnapshot(
            UUID snapshotId, Map<String, Object> profile, String signatureHash) {
        KnowledgeIndexSnapshotEntity compatible = mock(KnowledgeIndexSnapshotEntity.class);
        when(compatible.getId()).thenReturn(snapshotId);
        when(compatible.getResolvedConfigSnapshotId()).thenReturn(UUID.randomUUID());
        when(compatible.getResolvedConfigHash()).thenReturn("hash");
        when(compatible.getIndexProfileHash()).thenReturn("profile-hash");
        when(compatible.getIndexProfileJsonb()).thenReturn(profile);
        when(compatible.getSignatureHash()).thenReturn(signatureHash);
        return compatible;
    }

    private void stubReadyCorpus(UUID userId, UUID corpusId, UUID indexProjectId) {
        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
    }

    private static ProjectIndexProfile defaultProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.HYBRID,
                true,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.HYBRID, true, "meta-v1", "mxbai-embed-large", 400, 10),
                Instant.now(),
                Instant.now());
    }
}
