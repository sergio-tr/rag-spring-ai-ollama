package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        String title,
        Instant updatedAt,
        UUID presetId,
        List<String> documentFilter,
        Map<String, Object> runtimeOverride,
        /** Preset id shown to clients when {@link #presetId} is null — deterministic default ({@code Demo_Best}). */
        UUID effectivePresetId,
        /** Resolved runtime projection after create/preview when computed (empty map otherwise). */
        Map<String, Object> effectiveRuntimePreview,
        /** Non-blocking hints from validation at creation time (empty after list/patch). */
        List<RuntimeConfigValidationIssueDto> runtimeWarnings,
        /** Active index snapshot summary when computed at creation time (otherwise {@code null}). */
        RuntimeIndexCompatibilityDto indexCompatibility,
        /**
         * Active deterministic clarification payload when waiting for a follow-up user reply ({@code pending_clarification_jsonb}),
         * otherwise {@code null}.
         */
        Map<String, Object> pendingClarification) {

    public ConversationDto(
            UUID id,
            String title,
            Instant updatedAt,
            UUID presetId,
            List<String> documentFilter,
            Map<String, Object> runtimeOverride,
            UUID effectivePresetId) {
        this(id, title, updatedAt, presetId, documentFilter, runtimeOverride, effectivePresetId, Map.of(), List.of(), null, null);
    }
}
