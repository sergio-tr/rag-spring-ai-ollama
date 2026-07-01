package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabIndexSnapshotCompatibilityServiceTest {

    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess;

    private LabIndexSnapshotCompatibilityService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID corpusId = UUID.randomUUID();
    private final UUID indexProjectId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new LabIndexSnapshotCompatibilityService(
                        corpusAvailabilityGate, knowledgePipelineOrchestrator, snapshotProfileAccess);
    }

    @Test
    void requiresSnapshotBoundVectorRows_falseForDirectLlmGroup() {
        assertThat(
                        service.requiresSnapshotBoundVectorRows(
                                ExperimentalPresetCanonicalCatalog.IndexRequirements.none(),
                                LabPresetRunGroupKey.DIRECT_LLM))
                .isFalse();
    }

    @Test
    void requiresSnapshotBoundVectorRows_trueForNoIndexGroup() {
        assertThat(
                        service.requiresSnapshotBoundVectorRows(
                                ExperimentalPresetCanonicalCatalog.IndexRequirements.none(),
                                LabPresetRunGroupKey.NO_INDEX))
                .isTrue();
    }

    @Test
    void requiresSnapshotBoundVectorRows_trueForP1Preset() {
        assertThat(service.requiresSnapshotBoundVectorRows(RagExperimentalPresetCode.P1)).isTrue();
    }

    @Test
    void zeroRowSnapshotBlockedForP1Group() {
        KnowledgeIndexSnapshotEntity snapshot = snapshotWithProfile(Map.of());
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(false);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.IndexRequirements.none(),
                        null,
                        effectiveProfile(),
                        LabPresetRunGroupKey.NO_INDEX,
                        false);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reasonCode()).isEqualTo(LabCorpusReasonCodes.SNAPSHOT_EMPTY);
    }

    @Test
    void compatibleSnapshotWithVectorRowsIsReused() {
        KnowledgeIndexSnapshotEntity snapshot =
                snapshotWithProfile(Map.of("materializationStrategy", "CHUNK_LEVEL"));
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P3),
                        null,
                        effectiveProfile(),
                        LabPresetRunGroupKey.CHUNK_LEVEL,
                        false);

        assertThat(eligibility.eligible()).isTrue();
    }

    @Test
    void metadataAwareSnapshotWithVectorRowsIsCompatibleForP4Group() {
        KnowledgeIndexSnapshotEntity snapshot =
                snapshotWithProfile(
                        Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", true));
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P4),
                        null,
                        metadataEffectiveProfile(),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA,
                        true);

        assertThat(eligibility.eligible()).isTrue();
    }

    @Test
    void metadataAwareSnapshotWithoutVectorRowsIsIncompatibleForP4Group() {
        KnowledgeIndexSnapshotEntity snapshot =
                snapshotWithProfile(
                        Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", true));
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(false);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P4),
                        null,
                        metadataEffectiveProfile(),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA,
                        true);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reasonCode()).isEqualTo(LabCorpusReasonCodes.SNAPSHOT_EMPTY);
    }

    @Test
    void chunkSnapshotWithoutMetadataIsIncompatibleForP4Group() {
        KnowledgeIndexSnapshotEntity snapshot =
                snapshotWithProfile(
                        Map.of("materializationStrategy", "CHUNK_LEVEL", "supportsMetadata", false));
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P4),
                        null,
                        metadataEffectiveProfile(),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA,
                        true);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reasonCode()).isEqualTo(LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT);
    }

    @Test
    void profileCompatible_usesTransactionalProfileAccess_notDetachedEntity() {
        KnowledgeIndexSnapshotEntity detachedProxy = mock(KnowledgeIndexSnapshotEntity.class);
        when(detachedProxy.getId()).thenReturn(snapshotId);
        Map<String, Object> profile = Map.of("materializationStrategy", "CHUNK_LEVEL");
        when(snapshotProfileAccess.resolveProfileJsonb(detachedProxy)).thenReturn(profile);

        boolean compatible =
                service.profileCompatible(
                        detachedProxy,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P3),
                        null);

        assertThat(compatible).isTrue();
    }

    @Test
    void staleSnapshotBlocked() {
        KnowledgeIndexSnapshotEntity snapshot =
                snapshotWithProfile(Map.of("materializationStrategy", "CHUNK_LEVEL"));
        when(snapshot.getSignatureHash()).thenReturn("sig-stale");
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(true);
        when(knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        eq(indexProjectId), eq(CorpusScope.PROJECT_SHARED), eq(null), any()))
                .thenReturn("sig-current");

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                service.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                                RagExperimentalPresetCode.P3),
                        null,
                        effectiveProfile(),
                        LabPresetRunGroupKey.CHUNK_LEVEL,
                        false);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reasonCode()).isEqualTo(LabCorpusReasonCodes.SNAPSHOT_STALE);
    }

    private KnowledgeIndexSnapshotEntity snapshotWithProfile(Map<String, Object> profile) {
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(snapshotId);
        when(snapshot.getSignatureHash()).thenReturn("sig-current");
        when(snapshotProfileAccess.resolveProfileJsonb(snapshot)).thenReturn(profile);
        return snapshot;
    }

    private static ProjectIndexProfile effectiveProfile() {
        return new ProjectIndexProfile(
                UUID.randomUUID(),
                MaterializationStrategy.CHUNK_LEVEL,
                false,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.CHUNK_LEVEL, false, "meta-v1", "mxbai-embed-large", 400, 10),
                Instant.now(),
                Instant.now());
    }

    private static ProjectIndexProfile metadataEffectiveProfile() {
        return new ProjectIndexProfile(
                UUID.randomUUID(),
                MaterializationStrategy.CHUNK_LEVEL,
                true,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.CHUNK_LEVEL, true, "meta-v1", "mxbai-embed-large", 400, 10),
                Instant.now(),
                Instant.now());
    }
}
