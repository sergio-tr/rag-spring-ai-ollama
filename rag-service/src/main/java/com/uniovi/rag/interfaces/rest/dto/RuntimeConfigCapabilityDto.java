package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

public record RuntimeConfigCapabilityDto(
        String key,
        String label,
        String description,
        String category, // RUNTIME_HOT_SWAPPABLE | INDEX_BOUND | LAB_ONLY | INTERNAL
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
        String reasonIfNotImplemented
) {}

