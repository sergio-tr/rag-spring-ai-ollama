package com.uniovi.rag.domain.config.validation;

import java.util.List;

/**
 * Aggregated output of the compatibility rule engine ({@link com.uniovi.rag.application.config.CompatibilityValidator}).
 */
public record CompatibilityResult(
        List<CompatibilityViolation> errors,
        List<CompatibilityViolation> warnings,
        List<String> fallbackSuggestions) {

    public CompatibilityResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        fallbackSuggestions = fallbackSuggestions == null ? List.of() : List.copyOf(fallbackSuggestions);
    }

    public CompatibilitySeverity severity() {
        if (!errors.isEmpty()) {
            return CompatibilitySeverity.ERROR;
        }
        if (!warnings.isEmpty()) {
            return CompatibilitySeverity.WARNING;
        }
        return CompatibilitySeverity.OK;
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public static CompatibilityResult ok() {
        return new CompatibilityResult(List.of(), List.of(), List.of());
    }
}
