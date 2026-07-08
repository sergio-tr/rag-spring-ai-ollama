package com.uniovi.rag.interfaces.rest.dto;

public record CompatibleProductPresetDto(
        RagPresetDto preset,
        RuntimePresetIndexRequirementsDto indexRequirements,
        PresetCompatibilityDto compatibility
) {}
