package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative Chat runtime state for UI rendering.
 *
 * <p>Hard rules:
 * <ul>
 *   <li>{@code configurationMode} is {@code PRESET} when no custom conversation config is stored;
 *       {@code CUSTOM} when {@code runtimeOverride} holds a full conversation configuration snapshot.</li>
 *   <li>{@code runtimeOverride} is the persisted custom conversation configuration snapshot (API field name kept
 *       for compatibility), excluding {@code llmModel} / {@code classifierModelId} (conversation columns).</li>
 *   <li>{@code manualOverrideKeys} lists keys present in the custom conversation configuration snapshot.</li>
 *   <li>{@code isCustom} is true when {@code configurationMode} is {@code CUSTOM} or a conversation model is pinned.</li>
 *   <li>{@code effectiveConfig} includes resolved RAG values plus conversation column model pins when set.</li>
 * </ul>
 */
public record ChatRuntimeStateDto(
        UUID conversationId,
        UUID selectedPresetId,  // persisted selection; null means “Recommended Default”
        UUID effectivePresetId, // deterministic backend default when selectedPresetId is null
        ChatPresetSummaryDto preset,
        Map<String, Object> baseEffectiveConfig,
        Map<String, Object> effectiveConfig,
        String conversationLlmModel,
        String conversationClassifierModelId,
        boolean conversationModelsPinned,
        String configurationMode,
        Map<String, Object> runtimeOverride,
        List<String> manualOverrideKeys,
        boolean isCustom,
        ChatRuntimeValidationDto validation,
        boolean isValid,
        List<RuntimeConfigValidationIssueDto> blockingIssues,
        List<RuntimeConfigValidationIssueDto> warnings,
        String selectedWorkflow,
        RuntimeIndexCompatibilityDto indexCompatibility,
        boolean requiresReindex,
        PresetCompatibilityDto presetCompatibility,
        RuntimeCompatibilityDto runtimeCompatibility,
        List<DisabledRuntimeFeatureDto> disabledRuntimeFeatures,
        String disabledPresetReason,
        EffectiveRetrievalParametersDto effectiveRetrievalParameters
) {}

