package com.uniovi.rag.domain.config.validation;

import java.util.Optional;

/**
 * Single compatibility finding from a {@link com.uniovi.rag.domain.config.rules.CompatibilityRule}.
 */
public record CompatibilityViolation(String code, String message, Optional<String> ruleId) {

    public static CompatibilityViolation of(String code, String message) {
        return new CompatibilityViolation(code, message, Optional.empty());
    }

    public static CompatibilityViolation of(String code, String message, String ruleId) {
        return new CompatibilityViolation(code, message, Optional.ofNullable(ruleId));
    }
}
