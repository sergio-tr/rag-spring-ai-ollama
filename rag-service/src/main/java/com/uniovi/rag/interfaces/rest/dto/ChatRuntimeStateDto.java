package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative Chat runtime state for UI rendering.
 *
 * <p>Hard rules:
 * <ul>
 *   <li>{@code runtimeOverride} is persisted manual differences only (diff against {@code baseEffectiveConfig}).</li>
 *   <li>{@code manualOverrideKeys} is the only authoritative list of overridden keys.</li>
 *   <li>{@code isCustom} is true iff {@code manualOverrideKeys} is non-empty.</li>
 * </ul>
 */
public record ChatRuntimeStateDto(
        UUID conversationId,
        UUID selectedPresetId,  // persisted selection; null means “Recommended Default”
        UUID effectivePresetId, // deterministic backend default when selectedPresetId is null
        ChatPresetSummaryDto preset,
        Map<String, Object> baseEffectiveConfig,
        Map<String, Object> effectiveConfig,
        Map<String, Object> runtimeOverride,
        List<String> manualOverrideKeys,
        boolean isCustom,
        ChatRuntimeValidationDto validation,
        String selectedWorkflow,
        RuntimeIndexCompatibilityDto indexCompatibility,
        boolean requiresReindex
) {}

