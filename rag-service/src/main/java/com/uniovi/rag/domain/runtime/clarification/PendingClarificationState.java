package com.uniovi.rag.domain.runtime.clarification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical pending clarification payload (P11). Serialized 1:1 to {@code pending_clarification_jsonb}.
 */
public record PendingClarificationState(
        int clarificationStateVersion,
        UUID originatingUserMessageId,
        String baseQueryTextForClarification,
        String clarificationQuestionText,
        ClarificationQuestionKind questionKind,
        List<String> requestedFields,
        List<String> clarificationReasons,
        Instant createdAt,
        String correlationId) {

    public static final int SCHEMA_VERSION = 1;

    public PendingClarificationState {
        if (clarificationStateVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported clarificationStateVersion: " + clarificationStateVersion);
        }
        Objects.requireNonNull(originatingUserMessageId, "originatingUserMessageId");
        baseQueryTextForClarification = Objects.requireNonNull(baseQueryTextForClarification, "baseQueryTextForClarification");
        clarificationQuestionText = Objects.requireNonNull(clarificationQuestionText, "clarificationQuestionText");
        questionKind = Objects.requireNonNull(questionKind, "questionKind");
        requestedFields = List.copyOf(Objects.requireNonNull(requestedFields, "requestedFields"));
        clarificationReasons = List.copyOf(Objects.requireNonNull(clarificationReasons, "clarificationReasons"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        correlationId = Objects.requireNonNull(correlationId, "correlationId");
    }
}
