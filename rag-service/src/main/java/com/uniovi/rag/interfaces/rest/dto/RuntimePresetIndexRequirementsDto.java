package com.uniovi.rag.interfaces.rest.dto;

/**
 * Preset-declared index requirements (index-time capabilities).
 *
 * <p>These are not hot-swappable runtime toggles; if unmet by the active snapshot, reindex is required.
 */
public record RuntimePresetIndexRequirementsDto(
        String requiredMaterializationStrategy,
        boolean requiresMetadataSupport
) {}

