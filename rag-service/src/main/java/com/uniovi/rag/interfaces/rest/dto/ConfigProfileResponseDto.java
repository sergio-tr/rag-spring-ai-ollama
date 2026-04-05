package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConfigProfileResponseDto(
        UUID id,
        String profileType,
        int version,
        String label,
        Map<String, Object> payload,
        UUID ownerId,
        boolean immutable,
        Instant createdAt) {
}
