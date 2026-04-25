package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.Set;

/**
 * Fails when two or more capabilities from the set are active simultaneously.
 */
public record MutuallyExclusiveCapabilitiesRule(String ruleId, Set<Capability> mutuallyExclusive)
        implements CompatibilityRule {

    @Override
    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
        long count =
                mutuallyExclusive.stream()
                        .filter(c -> capabilitySet.activeCapabilities().contains(c))
                        .count();
        if (count >= 2) {
            return CompatibilityRuleOutcome.error(
                    ruleId,
                    CompatibilityViolation.of(
                            "MUTUALLY_EXCLUSIVE",
                            "Capabilities cannot be enabled together: " + mutuallyExclusive,
                            ruleId));
        }
        return CompatibilityRuleOutcome.empty();
    }
}
