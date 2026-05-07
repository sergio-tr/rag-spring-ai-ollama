package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminModelEntryDto(
        UUID id,
        String modelId,
        String displayName,
        AllowedModelType modelType,
        boolean enabled,
        boolean available,
        Instant lastCheckedAt,
        String lastPullStatus,
        String lastPullError,
        Instant installedAt,
        List<String> tags) {}

