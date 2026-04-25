package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.validation.CompatibilityViolation;

import java.util.List;

/**
 * Contributions from one {@link CompatibilityRule} evaluation.
 */
public record CompatibilityRuleOutcome(
        List<CompatibilityViolation> errors,
        List<CompatibilityViolation> warnings,
        List<String> suggestions) {

    public CompatibilityRuleOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static CompatibilityRuleOutcome empty() {
        return new CompatibilityRuleOutcome(List.of(), List.of(), List.of());
    }

    public static CompatibilityRuleOutcome error(String ruleId, CompatibilityViolation v) {
        return new CompatibilityRuleOutcome(List.of(v), List.of(), List.of());
    }

    public static CompatibilityRuleOutcome warning(String ruleId, CompatibilityViolation v) {
        return new CompatibilityRuleOutcome(List.of(), List.of(v), List.of());
    }
}
