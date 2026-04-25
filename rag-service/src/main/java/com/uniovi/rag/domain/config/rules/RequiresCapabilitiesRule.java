package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.Set;

/**
 * If any trigger capability is active, all required capabilities must be active.
 */
public record RequiresCapabilitiesRule(String ruleId, Set<Capability> triggers, Set<Capability> required)
        implements CompatibilityRule {

    @Override
    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
        Set<Capability> active = capabilitySet.activeCapabilities();
        boolean anyTrigger = triggers.stream().anyMatch(active::contains);
        if (!anyTrigger) {
            return CompatibilityRuleOutcome.empty();
        }
        for (Capability req : required) {
            if (!active.contains(req)) {
                return CompatibilityRuleOutcome.error(
                        ruleId,
                        CompatibilityViolation.of(
                                "REQUIRES_CAPABILITY",
                                "When " + triggers + " is active, " + req + " must be enabled",
                                ruleId));
            }
        }
        return CompatibilityRuleOutcome.empty();
    }
}
