package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Numeric bounds and similar checks on {@link RagConfig} (declarative single rule class).
 */
public record NumericRagConfigRule(String ruleId) implements CompatibilityRule {

    @Override
    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
        List<CompatibilityViolation> errors = new ArrayList<>();
        if (ragConfig.topK() < 1 || ragConfig.topK() > 2000) {
            errors.add(
                    CompatibilityViolation.of(
                            "TOPK_RANGE",
                            "topK must be between 1 and 2000",
                            ruleId));
        }
        return errors.isEmpty()
                ? CompatibilityRuleOutcome.empty()
                : new CompatibilityRuleOutcome(errors, List.of(), List.of());
    }
}
