package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.util.List;
import java.util.Optional;

/**
 * Full replace of name, description, and all entries for an existing definition.
 */
public record UpdateDefinitionCommand(String name, Optional<String> description, List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries) {

    public UpdateDefinitionCommand {
        description = description == null ? Optional.empty() : description;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
