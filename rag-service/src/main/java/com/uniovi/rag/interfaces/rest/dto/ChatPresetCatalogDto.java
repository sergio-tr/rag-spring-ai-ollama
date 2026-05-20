package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

/**
 * Unified Chat preset catalog: product presets + thesis experimental presets (P0–P14).
 * <p>
 * Product presets come from {@code /presets}. Experimental presets come from the Lab catalog, but are exposed
 * here so Chat can render them without relying on environment-gated UI flags.
 */
public record ChatPresetCatalogDto(
        List<RagPresetDto> productPresets,
        List<ExperimentalPresetCatalogItemDto> experimentalPresets
) {}

