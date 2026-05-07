package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MessageDto(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt,
        List<ChatSourceDto> sources,
        String queryType,
        List<Map<String, Object>> pipelineSteps,
        String status,
        Map<String, Object> executionMetadata
) {
}
