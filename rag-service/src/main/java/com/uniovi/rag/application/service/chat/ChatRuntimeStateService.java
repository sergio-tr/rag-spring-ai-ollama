package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigCapabilitiesService;
import com.uniovi.rag.interfaces.rest.mapper.RuntimeConfigRestMapper;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.DisabledRuntimeFeatureDto;
import com.uniovi.rag.interfaces.rest.dto.ChatPresetSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeStateDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeValidationDto;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.PresetCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeCompatibilityDto;
import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatRuntimeStateService {

    private static final String MISSING = "MISSING";

    private final ProjectAccessService projectAccessService;
    private final ChatPresetDefaults chatPresetDefaults;
    private final LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    private final RuntimeConfigValidationService runtimeConfigValidationService;
    private final RuntimeConfigCapabilitiesService runtimeConfigCapabilitiesService;

    ChatRuntimeStateService(
            ProjectAccessService projectAccessService,
            ChatPresetDefaults chatPresetDefaults,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService,
            RuntimeConfigValidationService runtimeConfigValidationService) {
        this(
                projectAccessService,
                chatPresetDefaults,
                experimentalPresetCatalogService,
                runtimeConfigValidationService,
                new RuntimeConfigCapabilitiesService());
    }

    @Autowired
    public ChatRuntimeStateService(
            ProjectAccessService projectAccessService,
            ChatPresetDefaults chatPresetDefaults,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService,
            RuntimeConfigValidationService runtimeConfigValidationService,
            RuntimeConfigCapabilitiesService runtimeConfigCapabilitiesService) {
        this.projectAccessService = projectAccessService;
        this.chatPresetDefaults = chatPresetDefaults;
        this.experimentalPresetCatalogService = experimentalPresetCatalogService;
        this.runtimeConfigValidationService = runtimeConfigValidationService;
        this.runtimeConfigCapabilitiesService = runtimeConfigCapabilitiesService;
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

        Map<String, Object> persistedForNormalize =
                ChatRuntimeCompatibilitySupport.copyWithoutNonRuntimeOverrideKeys(
                        c.getRuntimeOverride() != null && !c.getRuntimeOverride().isEmpty()
                                ? new LinkedHashMap<>(c.getRuntimeOverride())
                                : new LinkedHashMap<>());
        RuntimeOverrideNormalizer.NormalizedOverride normalized =
                RuntimeOverrideNormalizer.normalize(persistedForNormalize, baseEffectiveConfig);

        RuntimeConfigValidateResponse effectiveVr =
                runtimeConfigValidationService.validate(
                        userId,
                        new RuntimeConfigValidateRequest(
                                conversationId,
                                effectivePresetId != null ? effectivePresetId.toString() : null,
                                null,
                                normalized.runtimeOverride()));

        Map<String, Object> effectiveConfig =
                effectiveVr.effectiveConfig() != null ? new LinkedHashMap<>(effectiveVr.effectiveConfig()) : new LinkedHashMap<>();
        applyConversationModelColumnsToEffective(c, effectiveConfig);

        String conversationLlmModel = blankToNull(c.getLlmModel());
        String conversationClassifierModelId = blankToNull(c.getClassifierModelId());
        boolean conversationModelsPinned = conversationLlmModel != null || conversationClassifierModelId != null;
        boolean isCustom = !normalized.manualOverrideKeys().isEmpty() || conversationModelsPinned;

        ChatPresetSummaryDto presetSummary = presetSummary(selectedPresetId, c.getPreset());

        ChatRuntimeValidationDto validation =
                new ChatRuntimeValidationDto(
                        effectiveVr.valid(),
                        effectiveVr.supported(),
                        effectiveVr.errors() != null ? List.copyOf(effectiveVr.errors()) : List.of(),
                        effectiveVr.warnings() != null ? List.copyOf(effectiveVr.warnings()) : List.of());
        List<RuntimeConfigValidationIssueDto> blockingIssues =
                ChatRuntimeCompatibilitySupport.blockingIssues(effectiveVr);
        List<RuntimeConfigValidationIssueDto> warnings =
                effectiveVr.warnings() != null ? List.copyOf(effectiveVr.warnings()) : List.of();
        RuntimeCompatibilityDto runtimeCompatibility =
                ChatRuntimeCompatibilitySupport.runtimeCompatibility(effectiveVr);
        PresetCompatibilityDto presetCompatibility =
                ChatRuntimeCompatibilitySupport.presetCompatibility(
                        presetSummary,
                        effectiveVr.indexCompatibility(),
                        blockingIssues);
        List<DisabledRuntimeFeatureDto> disabledRuntimeFeatures =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        runtimeConfigCapabilitiesService.getCapabilities().capabilities().stream()
                                .map(RuntimeConfigRestMapper::toCapabilityDto)
                                .toList(),
                        effectiveConfig);
        String disabledPresetReason = presetCompatibility.disabledReason();

        return new ChatRuntimeStateDto(
                conversationId,
                selectedPresetId,
                effectivePresetId,
                presetSummary,
                baseEffectiveConfig,
                Map.copyOf(effectiveConfig),
                conversationLlmModel,
                conversationClassifierModelId,
                conversationModelsPinned,
                normalized.runtimeOverride(),
                normalized.manualOverrideKeys(),
                isCustom,
                validation,
                blockingIssues.isEmpty(),
                blockingIssues,
                warnings,
                effectiveVr.selectedWorkflow(),
                effectiveVr.indexCompatibility(),
                effectiveVr.requiresReindex(),
                presetCompatibility,
                runtimeCompatibility,
                disabledRuntimeFeatures,
                disabledPresetReason);
    }

    private static void applyConversationModelColumnsToEffective(ConversationEntity c, Map<String, Object> effectiveConfig) {
        if (c.getLlmModel() != null && !c.getLlmModel().isBlank()) {
            effectiveConfig.put(ConversationRuntimeModelKeys.LLM_MODEL, c.getLlmModel().trim());
        }
        if (c.getClassifierModelId() != null && !c.getClassifierModelId().isBlank()) {
            effectiveConfig.put(ConversationRuntimeModelKeys.CLASSIFIER_MODEL_ID, c.getClassifierModelId().trim());
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ChatPresetSummaryDto presetSummary(UUID selectedPresetId, RagPresetEntity selectedPresetEntity) {
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
                    MISSING,
                    null,
                    "Missing preset",
                    false,
                    false,
                    MISSING,
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
                    MISSING,
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

