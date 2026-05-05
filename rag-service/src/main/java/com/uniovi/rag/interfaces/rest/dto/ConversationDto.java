package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        String title,
        Instant updatedAt,
        UUID presetId,
        List<String> documentFilter,
        Map<String, Object> runtimeOverride,
        /** Preset id shown to clients when {@link #presetId} is null — deterministic default ({@code Demo_Best}). */
        UUID effectivePresetId) {}
