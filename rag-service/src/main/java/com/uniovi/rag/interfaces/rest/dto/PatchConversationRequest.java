package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

/**
 * Partial update for a conversation. {@code clearPreset true} removes the preset association.
 * When {@code presetId} is non-null, the preset must be visible to the user (owned or system).
 * When {@code documentFilter} is non-null, it replaces the persisted subset (empty list = no restriction).
 */
public record PatchConversationRequest(
        String title, String presetId, Boolean clearPreset, List<String> documentFilter) {}
