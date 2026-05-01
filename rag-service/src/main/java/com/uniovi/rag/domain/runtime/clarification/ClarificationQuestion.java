package com.uniovi.rag.domain.runtime.clarification;

import java.util.List;
import java.util.Objects;

public record ClarificationQuestion(
        String questionText, ClarificationQuestionKind questionKind, List<String> requestedFields) {

    public ClarificationQuestion {
        questionText = Objects.requireNonNull(questionText, "questionText");
        questionKind = Objects.requireNonNull(questionKind, "questionKind");
        requestedFields = List.copyOf(Objects.requireNonNull(requestedFields, "requestedFields"));
    }
}
