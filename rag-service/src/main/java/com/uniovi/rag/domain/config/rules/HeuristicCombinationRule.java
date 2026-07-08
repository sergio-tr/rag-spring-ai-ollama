package com.uniovi.rag.domain.config.rules;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-fatal heuristic warnings carried over from prior validation (classifier, expansion/retrieval).
 */
public record HeuristicCombinationRule(String ruleId) implements CompatibilityRule {

    @Override
    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
        List<CompatibilityViolation> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (ragConfig.metadataEnabled() && !ragConfig.toolsEnabled()) {
            warnings.add(
                    CompatibilityViolation.of(
                            "METADATA_WITHOUT_TOOLS",
                            "metadataEnabled is on but toolsEnabled is off; metadata tooling may be limited",
                            ruleId));
        }

        String classifier = ragConfig.classifierModelId();
        if (ragConfig.metadataEnabled() && (classifier == null || classifier.isBlank())) {
            warnings.add(
                    CompatibilityViolation.of(
                            "METADATA_WITHOUT_CLASSIFIER",
                            "metadataEnabled without a classifier model id may degrade metadata features",
                            ruleId));
            suggestions.add("Set classifierModelId or disable metadata");
        }

        return new CompatibilityRuleOutcome(List.of(), warnings, suggestions);
    }
}
