package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LabPresetRunPlanServiceTest {

    @Test
    void plan_p0_p12_groups_expected_keys() {
        LabPresetRunPlanService sut = sutWithResolved(LabEvaluationSnapshotService.ResolvedSnapshot.missing());

        EvaluationRunEntity run = runWithProject();

        List<RagExperimentalPresetCode> requested =
                List.of(
                        RagExperimentalPresetCode.P0,
                        RagExperimentalPresetCode.P1,
                        RagExperimentalPresetCode.P2,
                        RagExperimentalPresetCode.P3,
                        RagExperimentalPresetCode.P4,
                        RagExperimentalPresetCode.P5,
                        RagExperimentalPresetCode.P6,
                        RagExperimentalPresetCode.P7,
                        RagExperimentalPresetCode.P8,
                        RagExperimentalPresetCode.P9,
                        RagExperimentalPresetCode.P10,
                        RagExperimentalPresetCode.P11,
                        RagExperimentalPresetCode.P12);

        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, requested);
        List<String> keys =
                plan.groups().stream().map(g -> g.groupKey().name()).toList();
        assertThat(keys)
                .containsExactly(
                        LabPresetRunGroupKey.DIRECT_LLM.name(),
                        LabPresetRunGroupKey.NO_INDEX.name(),
                        LabPresetRunGroupKey.DOCUMENT_LEVEL.name(),
                        LabPresetRunGroupKey.CHUNK_LEVEL.name(),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA.name(),
                        LabPresetRunGroupKey.HYBRID_METADATA.name(),
                        LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN.name());
        LabPresetRunPlanModels.LabPresetRunGroup hybridGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.HYBRID_METADATA)
                .findFirst()
                .orElseThrow();
        assertThat(hybridGroup.presetCodes()).containsExactly("P8", "P9", "P10");
        LabPresetRunPlanModels.LabPresetRunGroup unsupportedGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN)
                .findFirst()
                .orElseThrow();
        assertThat(unsupportedGroup.presetCodes()).containsExactly("P11", "P12");
        LabPresetRunPlanModels.LabPresetRunGroup directGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.DIRECT_LLM)
                .findFirst()
                .orElseThrow();
        assertThat(directGroup.presetCodes()).containsExactly("P0");
        LabPresetRunPlanModels.LabPresetRunGroup corpusGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.NO_INDEX)
                .findFirst()
                .orElseThrow();
        assertThat(corpusGroup.presetCodes()).containsExactly("P1");
        LabPresetRunPlanModels.LabPresetRunGroup chunkGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.CHUNK_LEVEL)
                .findFirst()
                .orElseThrow();
        assertThat(chunkGroup.presetCodes()).containsExactly("P3");
    }

    @Test
    void snapshot_hybrid_with_metadata_marks_p2_p12_compatible() {
        LabPresetRunPlanService sut =
                sutWithResolved(resolvedFromMockEntity(mockSnapshot("HYBRID", true, "h", UUID.randomUUID())));

        EvaluationRunEntity run = runWithProject();

        List<RagExperimentalPresetCode> requested =
                List.of(
                        RagExperimentalPresetCode.P2,
                        RagExperimentalPresetCode.P3,
                        RagExperimentalPresetCode.P4,
                        RagExperimentalPresetCode.P5,
                        RagExperimentalPresetCode.P6,
                        RagExperimentalPresetCode.P7,
                        RagExperimentalPresetCode.P8,
                        RagExperimentalPresetCode.P9,
                        RagExperimentalPresetCode.P10);
        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, requested);
        assertThat(plan.skippedPresetCodes()).isEmpty();
        assertThat(plan.executablePresetCodes()).containsAll(requested.stream().map(Enum::name).toList());
        assertThat(plan.groups().stream().allMatch(LabPresetRunPlanModels.LabPresetRunGroup::compatible)).isTrue();
    }

    @Test
    void snapshot_chunk_with_metadata_marks_p8_p10_requires_reindex() {
        LabPresetRunPlanService sut =
                sutWithResolved(resolvedFromMockEntity(mockSnapshot("CHUNK_LEVEL", true, "h", UUID.randomUUID())));

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P9, RagExperimentalPresetCode.P10));
        assertThat(plan.executablePresetCodes()).isEmpty();
        assertThat(plan.skippedPresetCodes()).containsKeys("P8", "P9", "P10");
        LabPresetRunPlanModels.LabPresetRunGroup g = plan.groups().get(0);
        assertThat(g.groupKey()).isEqualTo(LabPresetRunGroupKey.HYBRID_METADATA);
        assertThat(g.compatible()).isFalse();
        assertThat(g.requiresReindex()).isTrue();
    }

    @Test
    void snapshot_chunk_without_metadata_marks_p4_p8_requires_reindex() {
        LabPresetRunPlanService sut =
                sutWithResolved(resolvedFromMockEntity(mockSnapshot("CHUNK_LEVEL", false, "h", UUID.randomUUID())));

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P4, RagExperimentalPresetCode.P8));
        assertThat(plan.executablePresetCodes()).isEmpty();
        assertThat(plan.skippedPresetCodes()).containsKeys("P4", "P8");
        assertThat(plan.groups().stream().anyMatch(g -> g.requiresReindex())).isTrue();
    }

    @Test
    void p11_p12_are_unsupported_in_single_turn_plan() {
        LabPresetRunPlanService sut = sutWithResolved(LabEvaluationSnapshotService.ResolvedSnapshot.missing());

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P11, RagExperimentalPresetCode.P12));
        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).groupKey()).isEqualTo(LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN);
        assertThat(plan.skippedPresetCodes()).containsKeys("P11", "P12");
    }

    @Test
    void compatible_non_active_snapshot_is_selected_instead_of_incompatible_active_snapshot() {
        UUID compatibleSnapshotId = UUID.randomUUID();
        LabPresetRunPlanService sut =
                sutWithResolved(resolvedFromMockEntity(mockSnapshot("HYBRID", true, "compatible", compatibleSnapshotId)));

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, List.of(RagExperimentalPresetCode.P8));

        assertThat(plan.executablePresetCodes()).containsExactly("P8");
        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).compatibleSnapshotId()).isEqualTo(compatibleSnapshotId);
        assertThat(plan.groups().get(0).activeSnapshotCapabilities()).containsEntry("materializationStrategy", "HYBRID");
    }

    @Test
    void no_snapshot_marks_p0_executable_p1_and_p2_requires_reindex() {
        LabPresetRunPlanService sut = sutWithResolved(LabEvaluationSnapshotService.ResolvedSnapshot.missing());

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P0, RagExperimentalPresetCode.P1, RagExperimentalPresetCode.P2));
        assertThat(plan.executablePresetCodes()).containsExactly("P0");
        assertThat(plan.skippedPresetCodes()).containsKeys("P1", "P2");
        assertThat(plan.groups().stream().filter(g -> g.groupKey() == LabPresetRunGroupKey.DIRECT_LLM))
                .allMatch(LabPresetRunPlanModels.LabPresetRunGroup::compatible);
        assertThat(plan.groups().stream().filter(g -> g.groupKey() == LabPresetRunGroupKey.NO_INDEX))
                .allMatch(g -> g.requiresReindex());
        assertThat(plan.groups().stream().filter(g -> g.groupKey() == LabPresetRunGroupKey.DOCUMENT_LEVEL))
                .allMatch(g -> g.requiresReindex());
    }

    @Test
    void p13_p14_are_multiturn_unsupported_in_single_turn_plan_group() {
        LabPresetRunPlanService sut = sutWithResolved(LabEvaluationSnapshotService.ResolvedSnapshot.missing());

        EvaluationRunEntity run = runWithProject();

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P13, RagExperimentalPresetCode.P14));
        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).groupKey()).isEqualTo(LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN);
        assertThat(plan.skippedPresetCodes()).containsKeys("P13", "P14");
    }

    @Test
    void p0_to_p14_all_receive_run_plan_items() {
        LabPresetRunPlanService sut =
                sutWithResolved(resolvedFromMockEntity(mockSnapshot("HYBRID", true, "h", UUID.randomUUID())));

        EvaluationRunEntity run = runWithProject();

        List<RagExperimentalPresetCode> requested = List.of(RagExperimentalPresetCode.values());
        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, requested);

        assertThat(plan.items()).hasSize(RagExperimentalPresetCode.values().length);
        assertThat(plan.items().stream().map(LabPresetRunPlanModels.LabPresetRunPlanItem::presetCode).toList())
                .containsExactlyInAnyOrderElementsOf(requested.stream().map(Enum::name).toList());
        assertThat(plan.items().stream()
                        .filter(i -> "P13".equals(i.presetCode()) || "P14".equals(i.presetCode()))
                        .allMatch(LabPresetRunPlanModels.LabPresetRunPlanItem::requiresMultiTurn))
                .isTrue();
    }

    private static LabPresetRunPlanService sutWithResolved(LabEvaluationSnapshotService.ResolvedSnapshot resolved) {
        LabEvaluationSnapshotService labSnap = Mockito.mock(LabEvaluationSnapshotService.class);
        doNothing().when(labSnap).ensureRunIndexProject(any());
        when(labSnap.resolveCorpusId(any())).thenReturn(null);
        when(labSnap.resolveCompatibleSnapshot(any(), any())).thenReturn(resolved);
        when(labSnap.resolveCompatibleSnapshot(any(), any(), nullable(String.class))).thenReturn(resolved);
        when(labSnap.resolveCompatibleSnapshot(any(), any(), nullable(String.class), any())).thenReturn(resolved);
        when(labSnap.hasRequiredVectorRows(any(), any(), any(), any()))
                .thenAnswer(
                        inv ->
                                resolved.hasUsableSnapshot()
                                        && resolved.snapshotId() != null
                                        && resolved.snapshotId().equals(inv.getArgument(1)));
        return new LabPresetRunPlanService(labSnap);
    }

    private static LabEvaluationSnapshotService.ResolvedSnapshot resolvedFromMockEntity(
            KnowledgeIndexSnapshotEntity snap) {
        Map<String, Object> profile = snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
        return new LabEvaluationSnapshotService.ResolvedSnapshot(
                true,
                snap.getId(),
                snap.getIndexProfileHash(),
                IndexSnapshotCapabilities.fromIndexProfile(profile),
                false,
                KnowledgeSnapshotOwnerType.PROJECT,
                UUID.randomUUID());
    }

    private static EvaluationRunEntity runWithProject() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);
        return run;
    }

    private static KnowledgeIndexSnapshotEntity mockSnapshot(
            String strategy,
            boolean supportsMetadata,
            String profileHash,
            UUID snapshotId) {
        KnowledgeIndexSnapshotEntity snap = new KnowledgeIndexSnapshotEntity();
        try {
            var f = KnowledgeIndexSnapshotEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(snap, snapshotId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set snapshot id for test", e);
        }
        snap.setIndexProfileHash(profileHash);
        snap.setIndexProfileJsonb(
                Map.of(
                        "materializationStrategy", strategy,
                        "supportsMetadata", supportsMetadata,
                        "embeddingModelId", "emb",
                        "chunkMaxChars", 400,
                        "chunkOverlap", 40));
        return snap;
    }
}
