package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.ChatPresetSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeStateDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeValidationDto;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatRuntimeStateService {

    private final ProjectAccessService projectAccessService;
    private final ChatPresetDefaults chatPresetDefaults;
    private final LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    private final RuntimeConfigValidationService runtimeConfigValidationService;

    public ChatRuntimeStateService(
            ProjectAccessService projectAccessService,
            ChatPresetDefaults chatPresetDefaults,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService,
            RuntimeConfigValidationService runtimeConfigValidationService) {
        this.projectAccessService = projectAccessService;
        this.chatPresetDefaults = chatPresetDefaults;
        this.experimentalPresetCatalogService = experimentalPresetCatalogService;
        this.runtimeConfigValidationService = runtimeConfigValidationService;
    }

    public ChatRuntimeStateDto getRuntimeState(UUID userId, UUID conversationId) {
        ConversationEntity c = projectAccessService.requireConversationForUser(userId, conversationId);
        UUID projectId = c.getProject() != null ? c.getProject().getId() : null;
        if (projectId == null) {
            throw new IllegalStateException("conversation missing project");
        }

        UUID selectedPresetId = c.getPreset() != null ? c.getPreset().getId() : null;
        UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(selectedPresetId);

        // Base effective config = resolved without conversation overrides.
        RuntimeConfigValidateResponse baseVr =
                runtimeConfigValidationService.validate(
                        userId,
                        new RuntimeConfigValidateRequest(
                                conversationId,
                                effectivePresetId != null ? effectivePresetId.toString() : null,
                                null,
                                Map.of()));
        Map<String, Object> baseEffectiveConfig =
                baseVr.effectiveConfig() != null ? Map.copyOf(baseVr.effectiveConfig()) : Map.of();

        Map<String, Object> persistedOverride =
                c.getRuntimeOverride() != null && !c.getRuntimeOverride().isEmpty()
                        ? new LinkedHashMap<>(c.getRuntimeOverride())
                        : Map.of();
        RuntimeOverrideNormalizer.NormalizedOverride normalized =
                RuntimeOverrideNormalizer.normalize(persistedOverride, baseEffectiveConfig);

        RuntimeConfigValidateResponse effectiveVr =
                runtimeConfigValidationService.validate(
                        userId,
                        new RuntimeConfigValidateRequest(
                                conversationId,
                                effectivePresetId != null ? effectivePresetId.toString() : null,
                                null,
                                normalized.runtimeOverride()));

        Map<String, Object> effectiveConfig =
                effectiveVr.effectiveConfig() != null ? Map.copyOf(effectiveVr.effectiveConfig()) : Map.of();

        ChatPresetSummaryDto presetSummary =
                presetSummary(selectedPresetId, effectivePresetId, c.getPreset());

        ChatRuntimeValidationDto validation =
                new ChatRuntimeValidationDto(
                        effectiveVr.valid(),
                        effectiveVr.supported(),
                        effectiveVr.errors() != null ? List.copyOf(effectiveVr.errors()) : List.of(),
                        effectiveVr.warnings() != null ? List.copyOf(effectiveVr.warnings()) : List.of());

        return new ChatRuntimeStateDto(
                conversationId,
                selectedPresetId,
                effectivePresetId,
                presetSummary,
                baseEffectiveConfig,
                effectiveConfig,
                normalized.runtimeOverride(),
                normalized.manualOverrideKeys(),
                !normalized.manualOverrideKeys().isEmpty(),
                validation,
                effectiveVr.selectedWorkflow(),
                effectiveVr.indexCompatibility(),
                effectiveVr.requiresReindex());
    }

    private ChatPresetSummaryDto presetSummary(
            UUID selectedPresetId, UUID effectivePresetId, RagPresetEntity selectedPresetEntity) {
        if (selectedPresetId == null) {
            // User did not pick a preset; Chat uses deterministic default, but UI label remains “Recommended Default”.
            return new ChatPresetSummaryDto(
                    "DEFAULT",
                    null,
                    "Recommended Default",
                    true,
                    true,
                    null,
                    null);
        }

        if (selectedPresetEntity == null) {
            return new ChatPresetSummaryDto(
                    "MISSING",
                    null,
                    "Missing preset",
                    false,
                    false,
                    "MISSING",
                    "Preset not found");
        }

        if (isExperimental(selectedPresetEntity)) {
            ExperimentalPresetCatalogItemDto item =
                    experimentalPresetCatalogService.list().stream()
                            .filter(p -> p != null && Objects.equals(p.productPresetId(), selectedPresetId.toString()))
                            .findFirst()
                            .orElse(null);
            if (item == null) {
                return new ChatPresetSummaryDto(
                        "MISSING",
                        null,
                        selectedPresetEntity.getName(),
                        false,
                        false,
                        "NOT_FOUND_IN_CATALOG",
                        "Experimental preset not found in catalog");
            }
            return new ChatPresetSummaryDto(
                    "EXPERIMENTAL",
                    item.code(),
                    item.label(),
                    item.chatSelectable(),
                    item.supported(),
                    item.supportStatus(),
                    item.reasonIfUnsupported());
        }

        return new ChatPresetSummaryDto(
                "PRODUCT",
                null,
                selectedPresetEntity.getName(),
                true,
                true,
                null,
                null);
    }

    private static boolean isExperimental(RagPresetEntity preset) {
        if (preset == null || preset.getTags() == null) {
            return false;
        }
        return preset.getTags().stream().anyMatch(t -> t != null && t.trim().equalsIgnoreCase("experimental"));
    }

    // Override normalization lives in RuntimeOverrideNormalizer (shared with PATCH validation).
}

