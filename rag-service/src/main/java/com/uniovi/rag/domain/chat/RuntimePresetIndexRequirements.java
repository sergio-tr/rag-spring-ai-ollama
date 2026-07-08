package com.uniovi.rag.domain.chat;

/**
 * Preset-declared index requirements (index-time capabilities).
 */
public record RuntimePresetIndexRequirements(
        String requiredMaterializationStrategy, boolean requiresMetadataSupport) {}
