package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

/**
 * Partial update for a conversation. {@code clearPreset true} removes the preset association.
 * When {@code presetId} is non-null, the preset must be visible to the user (owned or system).
 * When {@code documentFilter} is non-null, it replaces the persisted subset (empty list = no restriction).
 */
public record PatchConversationRequest(
        String title,
        String presetId,
        Boolean clearPreset,
        List<String> documentFilter,
        Map<String, Object> runtimeOverride,
        Boolean clearRuntimeOverride,
        /** Clears {@code pending_clarification_jsonb} on the conversation (best-effort cancel of clarification flow). */
        Boolean clearPendingClarification) {}
