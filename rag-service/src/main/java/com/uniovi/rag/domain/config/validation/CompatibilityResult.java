package com.uniovi.rag.domain.config.validation;

import java.util.List;

/**
 * Output of {@link CompatibilityValidator}; suitable for persistence in snapshots.
 */
public record CompatibilityResult(
        boolean valid,
        CompatibilitySeverity severity,
        List<String> errors,
        List<String> warnings,
        List<String> fallbackSuggestions
) {

    public static CompatibilityResult ok() {
        return new CompatibilityResult(true, CompatibilitySeverity.OK, List.of(), List.of(), List.of());
    }
}
