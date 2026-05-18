package com.uniovi.rag.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LabPresetRunPlanServiceTest {

    @Test
    void plan_p0_p12_groups_expected_keys() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any())).thenReturn(Optional.empty());
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

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
                        LabPresetRunGroupKey.DOCUMENT_LEVEL.name(),
                        LabPresetRunGroupKey.CHUNK_LEVEL.name(),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA.name(),
                        LabPresetRunGroupKey.HYBRID_METADATA.name());
        LabPresetRunPlanModels.LabPresetRunGroup chunkGroup = plan.groups().stream()
                .filter(g -> g.groupKey() == LabPresetRunGroupKey.CHUNK_LEVEL)
                .findFirst()
                .orElseThrow();
        assertThat(chunkGroup.presetCodes()).contains("P0", "P1", "P3");
    }

    @Test
    void snapshot_hybrid_with_metadata_marks_p2_p12_compatible() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "h", UUID.randomUUID())));
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

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
                        RagExperimentalPresetCode.P10,
                        RagExperimentalPresetCode.P11,
                        RagExperimentalPresetCode.P12);
        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, requested);
        assertThat(plan.skippedPresetCodes()).isEmpty();
        assertThat(plan.executablePresetCodes()).containsAll(requested.stream().map(Enum::name).toList());
        assertThat(plan.groups().stream().allMatch(LabPresetRunPlanModels.LabPresetRunGroup::compatible)).isTrue();
    }

    @Test
    void snapshot_chunk_with_metadata_marks_p8_p12_requires_reindex() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "h", UUID.randomUUID())));
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P9, RagExperimentalPresetCode.P12));
        assertThat(plan.executablePresetCodes()).isEmpty();
        assertThat(plan.skippedPresetCodes()).containsKeys("P8", "P9", "P12");
        LabPresetRunPlanModels.LabPresetRunGroup g = plan.groups().get(0);
        assertThat(g.groupKey()).isEqualTo(LabPresetRunGroupKey.HYBRID_METADATA);
        assertThat(g.compatible()).isFalse();
        assertThat(g.requiresReindex()).isTrue();
    }

    @Test
    void snapshot_chunk_without_metadata_marks_p4_p12_requires_reindex() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "h", UUID.randomUUID())));
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P4, RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P12));
        assertThat(plan.executablePresetCodes()).isEmpty();
        assertThat(plan.skippedPresetCodes()).containsKeys("P4", "P8", "P12");
        assertThat(plan.groups().stream().anyMatch(g -> g.requiresReindex())).isTrue();
    }

    @Test
    void compatible_non_active_snapshot_is_selected_instead_of_incompatible_active_snapshot() {
        UUID projectId = UUID.randomUUID();
        UUID compatibleSnapshotId = UUID.randomUUID();
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(projectId))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "active", UUID.randomUUID())));
        when(snapshotService.findCompatibleProjectSnapshot(Mockito.eq(projectId), Mockito.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "compatible", compatibleSnapshotId)));
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(projectId);
        run.setProject(p);

        LabPresetRunPlanModels.LabPresetRunPlan plan = sut.build(run, List.of(RagExperimentalPresetCode.P8));

        assertThat(plan.executablePresetCodes()).containsExactly("P8");
        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).compatibleSnapshotId()).isEqualTo(compatibleSnapshotId);
        assertThat(plan.groups().get(0).activeSnapshotCapabilities()).containsEntry("materializationStrategy", "HYBRID");
    }

    @Test
    void no_snapshot_keeps_p0_p1_compatible_and_marks_p2_requires_reindex() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any())).thenReturn(Optional.empty());
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P0, RagExperimentalPresetCode.P1, RagExperimentalPresetCode.P2));
        assertThat(plan.executablePresetCodes()).isEmpty();
        assertThat(plan.skippedPresetCodes()).containsKeys("P0", "P1", "P2");
        assertThat(plan.groups().stream().anyMatch(g -> g.requiresReindex())).isTrue();
    }

    @Test
    void p13_p14_are_multiturn_unsupported_in_single_turn_plan() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any())).thenReturn(Optional.empty());
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

        LabPresetRunPlanModels.LabPresetRunPlan plan =
                sut.build(run, List.of(RagExperimentalPresetCode.P13, RagExperimentalPresetCode.P14));
        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).groupKey()).isEqualTo(LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN);
        assertThat(plan.skippedPresetCodes()).containsKeys("P13", "P14");
    }

    @Test
    void p0_to_p14_all_receive_run_plan_items() {
        KnowledgeSnapshotService snapshotService = Mockito.mock(KnowledgeSnapshotService.class);
        when(snapshotService.findActiveProjectSnapshot(Mockito.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "h", UUID.randomUUID())));
        LabPresetRunPlanService sut = new LabPresetRunPlanService(snapshotService);

        EvaluationRunEntity run = new EvaluationRunEntity();
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        run.setProject(p);

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

