package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.AllowedModelType;
import java.time.Instant;
import java.util.List;

/**
 * Selectable model for product UIs (filtered: enabled + available).
 */
public record SelectableModelDto(
        String modelId,
        String displayName,
        AllowedModelType type,
        List<String> tags,
        boolean available,
        Instant lastCheckedAt) {}

