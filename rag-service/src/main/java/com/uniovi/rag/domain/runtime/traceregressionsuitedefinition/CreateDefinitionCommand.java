package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Create a persisted suite definition for a user.
 */
public record CreateDefinitionCommand(String name, Optional<String> description, List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries) {

    public CreateDefinitionCommand {
        description = Objects.requireNonNullElseGet(description, Optional::empty);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
