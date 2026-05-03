package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;

import java.util.List;

public record CompatibilityResultDto(
        boolean valid,
        String severity,
        List<String> errors,
        List<String> warnings,
        List<String> fallbackSuggestions) {

    public static CompatibilityResultDto fromDomain(CompatibilityResult r) {
        List<String> err =
                r.errors().stream().map(CompatibilityViolation::message).toList();
        List<String> warn =
                r.warnings().stream().map(CompatibilityViolation::message).toList();
        return new CompatibilityResultDto(
                r.valid(), r.severity().name(), err, warn, r.fallbackSuggestions());
    }

    public CompatibilityResult toDomain() {
        List<CompatibilityViolation> errViol =
                (errors == null ? List.<String>of() : errors).stream()
                        .map(m -> CompatibilityViolation.of("API_ERROR", m))
                        .toList();
        List<CompatibilityViolation> warnViol =
                (warnings == null ? List.<String>of() : warnings).stream()
                        .map(m -> CompatibilityViolation.of("API_WARNING", m))
                        .toList();
        List<String> sug = fallbackSuggestions != null ? fallbackSuggestions : List.of();
        return new CompatibilityResult(errViol, warnViol, sug);
    }
}
