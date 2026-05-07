package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

/**
 * Optional {@code documentFilter}: project document UUIDs to restrict retrieval for this chat (empty = all).
 *
 * <p>{@code initialPresetId} / {@code initialRuntimeOverride} seed the conversation before the first message;
 * validated against project index state where applicable.
 */
public record CreateConversationRequest(
        String title,
        List<String> documentFilter,
        String initialPresetId,
        Map<String, Object> initialRuntimeOverride) {

    public CreateConversationRequest(String title, List<String> documentFilter) {
        this(title, documentFilter, null, null);
    }
}
