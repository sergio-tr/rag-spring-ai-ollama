package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative Chat runtime state for UI rendering.
 *
 * <p>Hard rules:
 * <ul>
 *   <li>{@code runtimeOverride} is persisted manual differences only (diff against {@code baseEffectiveConfig}),
 *       excluding {@code llmModel} / {@code classifierModelId} (those live on conversation columns).</li>
 *   <li>{@code manualOverrideKeys} lists keys in {@code runtimeOverride} only.</li>
 *   <li>{@code isCustom} is true when there are manual override keys or a pinned conversation model selection.</li>
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

