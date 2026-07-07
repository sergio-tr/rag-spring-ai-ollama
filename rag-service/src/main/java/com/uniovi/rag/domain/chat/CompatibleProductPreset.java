package com.uniovi.rag.domain.chat;

import com.uniovi.rag.domain.preset.UserRagPreset;

public record CompatibleProductPreset(
        UserRagPreset preset,
        RuntimePresetIndexRequirements indexRequirements,
        PresetIndexCompatibility compatibility) {}
