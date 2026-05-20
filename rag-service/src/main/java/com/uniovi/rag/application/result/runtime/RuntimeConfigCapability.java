package com.uniovi.rag.application.result.runtime;

import java.util.List;

/**
 * Chat-facing runtime capability descriptor (UX matrix for manual configuration).
 */
public record RuntimeConfigCapability(
        String key,
        String label,
        String description,
        String category,
        boolean visibleInChat,
        boolean configurableInChat,
        boolean implemented,
        boolean engineWired,
        String supportMode,
        int displayOrder,
        List<String> requires,
        List<String> excludes,
        boolean requiresIndexSnapshot,
        boolean requiresReindexWhenChanged,
        String reasonIfDisabled,
        String reasonIfNotImplemented) {}
