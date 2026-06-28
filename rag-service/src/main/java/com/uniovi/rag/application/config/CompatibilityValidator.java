package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.rules.CompatibilityRule;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityValidatorBridge;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rule engine over injected {@link CompatibilityRule} instances (no ad-hoc combination branching).
 */
@Service
public class CompatibilityValidator {

    private final List<CompatibilityRule> rules;

    public CompatibilityValidator(List<CompatibilityRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public CompatibilityResult validate(CapabilitySet capabilitySet, RagConfig ragConfig) {
        return CompatibilityValidatorBridge.evaluate(rules, capabilitySet, ragConfig);
    }
}
