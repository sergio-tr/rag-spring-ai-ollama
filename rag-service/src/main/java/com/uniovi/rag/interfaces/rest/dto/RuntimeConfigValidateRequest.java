package com.uniovi.rag.interfaces.rest.dto;

import java.util.Map;
import java.util.UUID;

public record RuntimeConfigValidateRequest(
        UUID conversationId,
        String presetId,
        String experimentalPresetCode,
        Map<String, Object> overrides
) {}

