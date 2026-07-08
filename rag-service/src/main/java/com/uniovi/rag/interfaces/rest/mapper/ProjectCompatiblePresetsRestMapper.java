package com.uniovi.rag.interfaces.rest.mapper;

import com.uniovi.rag.domain.chat.ChatExperimentalPresetCatalogItem;
import com.uniovi.rag.domain.chat.CompatibleExperimentalPreset;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.PresetIndexCompatibility;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.domain.chat.RuntimePresetIndexRequirements;
import com.uniovi.rag.domain.chat.RuntimeSnapshotCapabilities;
import com.uniovi.rag.domain.preset.UserRagPreset;
import com.uniovi.rag.interfaces.rest.dto.CompatibleExperimentalPresetDto;
import com.uniovi.rag.interfaces.rest.dto.CompatibleProductPresetDto;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.PresetCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectCompatiblePresetsDto;
import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimePresetIndexRequirementsDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import java.util.List;

public final class ProjectCompatiblePresetsRestMapper {

    private ProjectCompatiblePresetsRestMapper() {}

    public static ProjectCompatiblePresetsDto toDto(ProjectCompatiblePresetsCatalog catalog) {
        return new ProjectCompatiblePresetsDto(
                catalog.projectId(),
                catalog.effectiveEmbeddingModelId(),
                catalog.hasActiveIndex(),
                catalog.readyDocumentCount(),
                toSnapshotCapabilitiesDto(catalog.activeSnapshotCapabilities()),
                catalog.productPresets().stream().map(ProjectCompatiblePresetsRestMapper::toProductDto).toList(),
                catalog.experimentalPresets().stream()
                        .map(ProjectCompatiblePresetsRestMapper::toExperimentalDto)
                        .toList());
    }

    private static CompatibleProductPresetDto toProductDto(CompatibleProductPreset item) {
        return new CompatibleProductPresetDto(
                toRagPresetDto(item.preset()),
                toIndexRequirementsDto(item.indexRequirements()),
                toCompatibilityDto(item.compatibility()));
    }

    private static CompatibleExperimentalPresetDto toExperimentalDto(CompatibleExperimentalPreset item) {
        return new CompatibleExperimentalPresetDto(
                toExperimentalPresetDto(item.preset()), toCompatibilityDto(item.compatibility()));
    }

    private static RagPresetDto toRagPresetDto(UserRagPreset preset) {
        return new RagPresetDto(
                preset.id(),
                preset.name(),
                preset.description(),
                preset.tags(),
                preset.values(),
                preset.system(),
                preset.createdAt(),
                preset.updatedAt(),
                List.of());
    }

    private static ExperimentalPresetCatalogItemDto toExperimentalPresetDto(ChatExperimentalPresetCatalogItem preset) {
        return new ExperimentalPresetCatalogItemDto(
                preset.productPresetId(),
                preset.code(),
                preset.family(),
                preset.label(),
                preset.description(),
                toIndexRequirementsDto(preset.indexRequirements()),
                preset.requiredCapabilities(),
                preset.supported(),
                preset.supportStatus(),
                preset.reasonIfUnsupported(),
                preset.requiresMultiTurn(),
                preset.mapsToRuntimeCapabilities(),
                preset.allowedOutcomes(),
                preset.chatSelectable(),
                preset.labSelectable(),
                preset.labOnly(),
                preset.corpusRequired(),
                preset.requiresSnapshot(),
                preset.requiresProjectDocuments(),
                preset.singleTurnBenchmarkSelectable(),
                preset.protocolStageIndex(),
                preset.parentPresetCode(),
                preset.effectiveTerminalRuntimeJson());
    }

    private static PresetCompatibilityDto toCompatibilityDto(PresetIndexCompatibility compatibility) {
        return new PresetCompatibilityDto(
                compatibility.selectable(),
                compatibility.disabledReasonCode(),
                compatibility.disabledReason(),
                toIndexRequirementsDto(compatibility.indexRequirements()),
                compatibility.compatibleWithActiveIndex());
    }

    private static RuntimePresetIndexRequirementsDto toIndexRequirementsDto(RuntimePresetIndexRequirements requirements) {
        if (requirements == null) {
            return null;
        }
        return new RuntimePresetIndexRequirementsDto(
                requirements.requiredMaterializationStrategy(), requirements.requiresMetadataSupport());
    }

    private static RuntimeSnapshotCapabilitiesDto toSnapshotCapabilitiesDto(RuntimeSnapshotCapabilities capabilities) {
        if (capabilities == null) {
            return null;
        }
        return new RuntimeSnapshotCapabilitiesDto(
                capabilities.materializationStrategy(),
                capabilities.supportsMetadata(),
                capabilities.embeddingModelId(),
                capabilities.chunkMaxChars(),
                capabilities.chunkOverlap());
    }
}
