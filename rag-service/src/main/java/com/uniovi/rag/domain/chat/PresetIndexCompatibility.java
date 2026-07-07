package com.uniovi.rag.domain.chat;

public record PresetIndexCompatibility(
        boolean selectable,
        String disabledReasonCode,
        String disabledReason,
        RuntimePresetIndexRequirements indexRequirements,
        boolean compatibleWithActiveIndex) {}
