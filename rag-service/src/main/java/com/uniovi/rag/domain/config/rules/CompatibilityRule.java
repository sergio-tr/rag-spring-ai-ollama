package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.runtime.RagConfig;

/**
 * Declarative compatibility constraint evaluated by the rule engine.
 */
public interface CompatibilityRule {

    String ruleId();

    CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig);
}
