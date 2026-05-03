package com.uniovi.rag.domain.runtime.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EntityExtractionResult(
        List<String> people,
        List<String> dates,
        List<String> locations,
        List<String> topics,
        List<String> organizations,
        Optional<String> temporalContext,
        Optional<String> answerTypeHint,
        Optional<String> comparisonTypeHint,
        List<String> notes) {

    public EntityExtractionResult {
        people = List.copyOf(Objects.requireNonNull(people, "people"));
        dates = List.copyOf(Objects.requireNonNull(dates, "dates"));
        locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
        topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
        organizations = List.copyOf(Objects.requireNonNull(organizations, "organizations"));
        temporalContext = Objects.requireNonNullElseGet(temporalContext, Optional::empty);
        answerTypeHint = Objects.requireNonNullElseGet(answerTypeHint, Optional::empty);
        comparisonTypeHint = Objects.requireNonNullElseGet(comparisonTypeHint, Optional::empty);
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }

    public static EntityExtractionResult emptyWithNote(String note) {
        return new EntityExtractionResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                note == null ? List.of() : List.of(note));
    }
}

