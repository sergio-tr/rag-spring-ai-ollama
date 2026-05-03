package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

import java.util.Map;
import java.util.Objects;

/**
 * Mapper output: validated answer text plus stable payload for tracing / debugging.
 */
public record MappedToolOutput(
        DeterministicToolKind kind,
        String answerText,
        Map<String, Object> normalizedPayload) {

    public MappedToolOutput {
        Objects.requireNonNull(answerText, "answerText");
        normalizedPayload = Map.copyOf(Objects.requireNonNull(normalizedPayload, "normalizedPayload"));
    }
}
