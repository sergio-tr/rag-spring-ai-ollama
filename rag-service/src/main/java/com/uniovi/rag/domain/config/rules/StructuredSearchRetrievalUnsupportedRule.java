package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;

/**
 * STRUCTURED_SEARCH with retrieval is not supported on the orchestrated runtime path.
 * Aligns with {@link com.uniovi.rag.application.service.runtime.WorkflowSelector}.
 */
public record StructuredSearchRetrievalUnsupportedRule(String ruleId) implements CompatibilityRule {

    @Override
    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
        if (ragConfig.useRetrieval()
                && ragConfig.materializationStrategy() == MaterializationStrategy.STRUCTURED_SEARCH) {
            return CompatibilityRuleOutcome.error(
                    CompatibilityViolation.of(
                            "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
                            "materializationStrategy STRUCTURED_SEARCH with useRetrieval is not supported in the orchestrated engine",
                            ruleId));
        }
        return CompatibilityRuleOutcome.empty();
    }
}
