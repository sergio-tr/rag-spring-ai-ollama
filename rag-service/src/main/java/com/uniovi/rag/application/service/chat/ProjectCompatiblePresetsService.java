package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.service.ProjectDocumentApplicationService;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.preset.PresetService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.domain.chat.ChatExperimentalPresetCatalogItem;
import com.uniovi.rag.domain.chat.CompatibleExperimentalPreset;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.PresetCatalogCompatibility;
import com.uniovi.rag.domain.chat.PresetDraftCompatibilityResult;
import com.uniovi.rag.domain.chat.PresetIndexCompatibility;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.domain.chat.RuntimeSnapshotCapabilities;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.preset.UserRagPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCompatiblePresetsService {

    private final ProjectAccessService projectAccessService;
    private final PresetService presetService;
    private final LabExperimentalPresetCatalogService labExperimentalPresetCatalogService;
    private final RuntimeConfigValidationService runtimeConfigValidationService;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final ProjectDocumentApplicationService projectDocumentApplicationService;

    public ProjectCompatiblePresetsService(
            ProjectAccessService projectAccessService,
            PresetService presetService,
            LabExperimentalPresetCatalogService labExperimentalPresetCatalogService,
            RuntimeConfigValidationService runtimeConfigValidationService,
            KnowledgeSnapshotService knowledgeSnapshotService,
            ProjectIndexProfileService projectIndexProfileService,
            ProjectDocumentApplicationService projectDocumentApplicationService) {
        this.projectAccessService = projectAccessService;
        this.presetService = presetService;
        this.labExperimentalPresetCatalogService = labExperimentalPresetCatalogService;
        this.runtimeConfigValidationService = runtimeConfigValidationService;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.projectIndexProfileService = projectIndexProfileService;
        this.projectDocumentApplicationService = projectDocumentApplicationService;
    }

    @Transactional(readOnly = true)
    public ProjectCompatiblePresetsCatalog list(UUID userId, UUID projectId, String embeddingModelIdOverride) {
        projectAccessService.requireOwnedProject(userId, projectId);

        boolean hasActiveIndex = knowledgeSnapshotService.hasActiveProjectIndex(projectId);
        Map<String, Object> activeProfile =
                knowledgeSnapshotService
                        .findActiveProjectIndexProfile(projectId)
                        .orElse(Map.of());
        IndexSnapshotCapabilities snapCaps = IndexSnapshotCapabilities.fromIndexProfile(activeProfile);

        ProjectIndexProfile plannedProfile = projectIndexProfileService.ensureDefault(projectId);
        String effectiveEmbeddingModelId = resolveEffectiveEmbeddingModelId(
                embeddingModelIdOverride, snapCaps.embeddingModelId(), plannedProfile.embeddingModelId());

        long readyDocumentCount = projectDocumentApplicationService.countReadyDocuments(projectId);

        List<UserRagPreset> productPresets = presetService.listUserPresets(userId);
        List<CompatibleProductPreset> compatibleProduct = new ArrayList<>();
        for (UserRagPreset preset : productPresets) {
            PresetDraftCompatibilityResult draft =
                    runtimeConfigValidationService.assessPresetDraft(userId, projectId, preset.id());
            compatibleProduct.add(
                    new CompatibleProductPreset(
                            preset,
                            draft.indexRequirements(),
                            PresetCatalogCompatibility.assess(draft, true, null)));
        }

        List<ChatExperimentalPresetCatalogItem> experimentalPresets =
                labExperimentalPresetCatalogService.listChatCatalog();
        List<CompatibleExperimentalPreset> compatibleExperimental = new ArrayList<>();
        for (ChatExperimentalPresetCatalogItem preset : experimentalPresets) {
            UUID presetUuid = parsePresetUuid(preset.productPresetId());
            PresetDraftCompatibilityResult draft =
                    presetUuid != null
                            ? runtimeConfigValidationService.assessPresetDraft(userId, projectId, presetUuid)
                            : null;
            String catalogReason =
                    !preset.chatSelectable()
                            ? (preset.reasonIfUnsupported() != null && !preset.reasonIfUnsupported().isBlank()
                                    ? preset.reasonIfUnsupported()
                                    : preset.supportStatus())
                            : null;
            PresetIndexCompatibility compatibility =
                    PresetCatalogCompatibility.assess(
                            draft, preset.chatSelectable() && preset.supported(), catalogReason);
            compatibleExperimental.add(new CompatibleExperimentalPreset(preset, compatibility));
        }

        RuntimeSnapshotCapabilities activeSnapshotCapabilities =
                hasActiveIndex
                        ? toSnapshotCapabilities(snapCaps, effectiveEmbeddingModelId)
                        : plannedCapabilities(plannedProfile, effectiveEmbeddingModelId);

        return new ProjectCompatiblePresetsCatalog(
                projectId,
                effectiveEmbeddingModelId,
                hasActiveIndex,
                readyDocumentCount,
                activeSnapshotCapabilities,
                List.copyOf(compatibleProduct),
                List.copyOf(compatibleExperimental));
    }

    private static UUID parsePresetUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String resolveEffectiveEmbeddingModelId(
            String override, String snapshotEmbedding, String profileEmbedding) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (snapshotEmbedding != null && !snapshotEmbedding.isBlank()) {
            return snapshotEmbedding.trim();
        }
        if (profileEmbedding != null && !profileEmbedding.isBlank()) {
            return profileEmbedding.trim();
        }
        return null;
    }

    private static RuntimeSnapshotCapabilities plannedCapabilities(
            ProjectIndexProfile profile, String effectiveEmbeddingModelId) {
        if (profile == null) {
            return null;
        }
        return new RuntimeSnapshotCapabilities(
                profile.materializationStrategy() != null ? profile.materializationStrategy().name() : null,
                profile.metadataEnabled(),
                effectiveEmbeddingModelId,
                profile.chunkMaxChars(),
                profile.chunkOverlap());
    }

    private static RuntimeSnapshotCapabilities toSnapshotCapabilities(
            IndexSnapshotCapabilities snapCaps, String effectiveEmbeddingModelId) {
        return new RuntimeSnapshotCapabilities(
                snapCaps.materializationStrategy(),
                snapCaps.supportsMetadata(),
                effectiveEmbeddingModelId,
                snapCaps.chunkMaxChars(),
                snapCaps.chunkOverlap());
    }
}
