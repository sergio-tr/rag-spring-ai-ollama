package com.uniovi.rag.domain.config.validation;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.rules.CompatibilityRule;
import com.uniovi.rag.domain.config.rules.CompatibilityRuleOutcome;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rule-engine aggregation (no Spring). Used by {@link com.uniovi.rag.application.config.CompatibilityValidator}.
 */
public final class LegacyCompatibilityValidatorBridge {

    private LegacyCompatibilityValidatorBridge() {}

    public static CompatibilityResult evaluate(List<CompatibilityRule> rules, CapabilitySet capabilitySet, RagConfig ragConfig) {
        List<CompatibilityViolation> errors = new ArrayList<>();
        List<CompatibilityViolation> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        for (CompatibilityRule rule : rules) {
            CompatibilityRuleOutcome o = rule.apply(capabilitySet, ragConfig);
            errors.addAll(o.errors());
            warnings.addAll(o.warnings());
            suggestions.addAll(o.suggestions());
        }
        return new CompatibilityResult(errors, warnings, suggestions);
    }
}
