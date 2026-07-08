package com.uniovi.rag.interfaces.rest.dto;

public record CompatibleExperimentalPresetDto(
        ExperimentalPresetCatalogItemDto preset,
        PresetCompatibilityDto compatibility
) {}
