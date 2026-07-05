package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.ProjectDocumentApplicationService;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.preset.PresetService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.domain.chat.ChatExperimentalPresetCatalogItem;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.PresetDraftCompatibilityResult;
import com.uniovi.rag.domain.chat.PresetValidationIssue;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.domain.chat.RuntimePresetIndexRequirements;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.preset.UserRagPreset;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectCompatiblePresetsServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private PresetService presetService;

    @Mock
    private LabExperimentalPresetCatalogService labExperimentalPresetCatalogService;

    @Mock
    private RuntimeConfigValidationService runtimeConfigValidationService;

    @Mock
    private KnowledgeSnapshotService knowledgeSnapshotService;

    @Mock
    private ProjectIndexProfileService projectIndexProfileService;

    @Mock
    private ProjectDocumentApplicationService projectDocumentApplicationService;

    @InjectMocks
    private ProjectCompatiblePresetsService projectCompatiblePresetsService;

    private UUID userId;
    private UUID projectId;
    private UUID compatiblePresetId;
    private UUID incompatiblePresetId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        compatiblePresetId = UUID.randomUUID();
        incompatiblePresetId = UUID.randomUUID();
    }

    @Test
    void list_marksIncompatibleProductPresetsAsNotSelectable() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UserRagPreset compatible =
                new UserRagPreset(
                        compatiblePresetId,
                        "Chunk preset",
                        null,
                        List.of(),
                        Map.of("useRetrieval", true),
                        true,
                        now,
                        now);
        UserRagPreset incompatible =
                new UserRagPreset(
                        incompatiblePresetId,
                        "Hybrid preset",
                        null,
                        List.of(),
                        Map.of("useRetrieval", true),
                        false,
                        now,
                        now);

        when(knowledgeSnapshotService.hasActiveProjectIndex(projectId)).thenReturn(true);
        when(knowledgeSnapshotService.findActiveProjectIndexProfile(projectId))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        "materializationStrategy",
                                        "CHUNK_LEVEL",
                                        "supportsMetadata",
                                        false,
                                        "embeddingModelId",
                                        "mxbai-embed-large")));
        when(projectIndexProfileService.ensureDefault(projectId))
                .thenReturn(
                        new ProjectIndexProfile(
                                projectId,
                                MaterializationStrategy.CHUNK_LEVEL,
                                false,
                                null,
                                "mxbai-embed-large",
                                400,
                                40,
                                "hash",
                                now,
                                now));
        when(projectDocumentApplicationService.countReadyDocuments(projectId)).thenReturn(2L);
        when(presetService.listUserPresets(userId)).thenReturn(List.of(compatible, incompatible));
        when(labExperimentalPresetCatalogService.listChatCatalog()).thenReturn(List.of());

        when(runtimeConfigValidationService.assessPresetDraft(userId, projectId, compatiblePresetId))
                .thenReturn(
                        new PresetDraftCompatibilityResult(
                                new RuntimePresetIndexRequirements("CHUNK_LEVEL", false),
                                true,
                                "COMPATIBLE",
                                List.of()));
        when(runtimeConfigValidationService.assessPresetDraft(userId, projectId, incompatiblePresetId))
                .thenReturn(
                        new PresetDraftCompatibilityResult(
                                new RuntimePresetIndexRequirements("HYBRID", true),
                                false,
                                "MATERIALIZATION_NOT_SUPPORTED",
                                List.of(
                                        new PresetValidationIssue(
                                                "MATERIALIZATION_NOT_SUPPORTED",
                                                "Requires HYBRID index"))));

        ProjectCompatiblePresetsCatalog catalog =
                projectCompatiblePresetsService.list(userId, projectId, null);

        assertThat(catalog.projectId()).isEqualTo(projectId);
        assertThat(catalog.productPresets()).hasSize(2);

        CompatibleProductPreset compatibleItem =
                catalog.productPresets().stream()
                        .filter(item -> item.preset().id().equals(compatiblePresetId))
                        .findFirst()
                        .orElseThrow();
        CompatibleProductPreset incompatibleItem =
                catalog.productPresets().stream()
                        .filter(item -> item.preset().id().equals(incompatiblePresetId))
                        .findFirst()
                        .orElseThrow();

        assertThat(compatibleItem.compatibility().selectable()).isTrue();
        assertThat(incompatibleItem.compatibility().selectable()).isFalse();
        assertThat(incompatibleItem.compatibility().disabledReasonCode())
                .isEqualTo("MATERIALIZATION_NOT_SUPPORTED");
    }

    @Test
    void list_marksUnsupportedExperimentalPresetAsNotSelectable() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(knowledgeSnapshotService.hasActiveProjectIndex(projectId)).thenReturn(false);
        when(knowledgeSnapshotService.findActiveProjectIndexProfile(projectId)).thenReturn(Optional.empty());
        when(projectIndexProfileService.ensureDefault(projectId))
                .thenReturn(
                        new ProjectIndexProfile(
                                projectId,
                                MaterializationStrategy.CHUNK_LEVEL,
                                false,
                                null,
                                "mxbai-embed-large",
                                400,
                                40,
                                "hash",
                                now,
                                now));
        when(projectDocumentApplicationService.countReadyDocuments(projectId)).thenReturn(0L);
        when(presetService.listUserPresets(userId)).thenReturn(List.of());

        UUID experimentalId = UUID.fromString("cafe0001-0001-4001-8001-000000000018");
        ChatExperimentalPresetCatalogItem experimental =
                new ChatExperimentalPresetCatalogItem(
                        experimentalId.toString(),
                        "P8",
                        "S2",
                        "Hybrid retrieval",
                        "desc",
                        new RuntimePresetIndexRequirements("HYBRID", false),
                        List.of("USE_RETRIEVAL"),
                        true,
                        "EXECUTABLE",
                        null,
                        false,
                        Map.of(),
                        List.of("EXECUTED"),
                        true,
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        0,
                        null,
                        null);
        when(labExperimentalPresetCatalogService.listChatCatalog()).thenReturn(List.of(experimental));
        when(runtimeConfigValidationService.assessPresetDraft(eq(userId), eq(projectId), eq(experimentalId)))
                .thenReturn(
                        new PresetDraftCompatibilityResult(
                                new RuntimePresetIndexRequirements("HYBRID", false),
                                false,
                                "NO_ACTIVE_INDEX",
                                List.of()));

        ProjectCompatiblePresetsCatalog catalog =
                projectCompatiblePresetsService.list(userId, projectId, "nomic-embed-text");

        assertThat(catalog.effectiveEmbeddingModelId()).isEqualTo("nomic-embed-text");
        assertThat(catalog.experimentalPresets()).hasSize(1);
        assertThat(catalog.experimentalPresets().getFirst().compatibility().selectable()).isFalse();
        assertThat(catalog.experimentalPresets().getFirst().compatibility().disabledReasonCode())
                .isEqualTo("NO_ACTIVE_INDEX");
    }
}
