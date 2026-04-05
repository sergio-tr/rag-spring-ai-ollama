package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        String title,
        Instant updatedAt,
        UUID presetId,
        List<String> documentFilter) {}
