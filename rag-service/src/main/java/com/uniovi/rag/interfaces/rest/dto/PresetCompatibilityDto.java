package com.uniovi.rag.interfaces.rest.dto;

public record PresetCompatibilityDto(
        boolean selectable,
        String disabledReasonCode,
        String disabledReason,
        RuntimePresetIndexRequirementsDto indexRequirements,
        boolean compatibleWithActiveIndex
) {}
