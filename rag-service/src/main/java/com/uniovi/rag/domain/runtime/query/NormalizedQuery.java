package com.uniovi.rag.domain.runtime.query;

import java.util.List;
import java.util.Objects;

public record NormalizedQuery(
        String rawUserQuery,
        String normalizedText,
        List<String> notes) {

    public NormalizedQuery {
        rawUserQuery = Objects.requireNonNull(rawUserQuery, "rawUserQuery");
        normalizedText = Objects.requireNonNull(normalizedText, "normalizedText");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}

