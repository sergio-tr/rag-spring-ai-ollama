package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilitySeverity;

import java.util.List;

public record CompatibilityResultDto(
        boolean valid,
        String severity,
        List<String> errors,
        List<String> warnings,
        List<String> fallbackSuggestions) {

    public static CompatibilityResultDto fromDomain(CompatibilityResult r) {
        return new CompatibilityResultDto(
                r.valid(),
                r.severity().name(),
                r.errors(),
                r.warnings(),
                r.fallbackSuggestions());
    }

    public CompatibilityResult toDomain() {
        return new CompatibilityResult(
                valid,
                CompatibilitySeverity.valueOf(severity),
                errors != null ? errors : List.of(),
                warnings != null ? warnings : List.of(),
                fallbackSuggestions != null ? fallbackSuggestions : List.of());
    }
}
