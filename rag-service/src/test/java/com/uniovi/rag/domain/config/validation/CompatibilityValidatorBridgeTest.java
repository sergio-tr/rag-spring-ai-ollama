package com.uniovi.rag.domain.config.validation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.rules.CompatibilityRule;
import com.uniovi.rag.domain.config.rules.CompatibilityRuleOutcome;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompatibilityValidatorBridgeTest {

    @Test
    void evaluate_emptyRules_returnsOkAggregate() {
        RagFeatureConfiguration features = mock(RagFeatureConfiguration.class);
        when(features.isExpansionEnabled()).thenReturn(false);
        when(features.isNerEnabled()).thenReturn(false);
        when(features.isToolsEnabled()).thenReturn(false);
        when(features.isMetadataEnabled()).thenReturn(false);
        when(features.isReasoningEnabled()).thenReturn(false);
        when(features.isRankerEnabled()).thenReturn(false);
        when(features.isPostRetrievalEnabled()).thenReturn(false);
        when(features.isFunctionCallingEnabled()).thenReturn(false);
        when(features.isUseRetrieval()).thenReturn(true);
        when(features.isUseAdvisor()).thenReturn(false);
        when(features.isClarificationEnabled()).thenReturn(false);
        when(features.isMemoryEnabled()).thenReturn(false);
        when(features.isAdaptiveRoutingEnabled()).thenReturn(false);
        when(features.isJudgeEnabled()).thenReturn(false);

        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(features, 5, 0.5, "lm", "em", null, "NONE");
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);

        CompatibilityResult out =
                CompatibilityValidatorBridge.evaluate(List.of(), caps, cfg);

        assertTrue(out.valid());
        assertTrue(out.errors().isEmpty());
        assertTrue(out.warnings().isEmpty());
    }

    @Test
    void evaluate_mergesRuleOutcomes() {
        RagFeatureConfiguration features = mock(RagFeatureConfiguration.class);
        when(features.isExpansionEnabled()).thenReturn(false);
        when(features.isNerEnabled()).thenReturn(false);
        when(features.isToolsEnabled()).thenReturn(false);
        when(features.isMetadataEnabled()).thenReturn(false);
        when(features.isReasoningEnabled()).thenReturn(false);
        when(features.isRankerEnabled()).thenReturn(false);
        when(features.isPostRetrievalEnabled()).thenReturn(false);
        when(features.isFunctionCallingEnabled()).thenReturn(false);
        when(features.isUseRetrieval()).thenReturn(true);
        when(features.isUseAdvisor()).thenReturn(false);
        when(features.isClarificationEnabled()).thenReturn(false);
        when(features.isMemoryEnabled()).thenReturn(false);
        when(features.isAdaptiveRoutingEnabled()).thenReturn(false);
        when(features.isJudgeEnabled()).thenReturn(false);

        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(features, 5, 0.5, "lm", "em", null, "NONE");
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);

        CompatibilityViolation err = CompatibilityViolation.of("E1", "bad", "r1");
        CompatibilityViolation warn = CompatibilityViolation.of("W1", "careful", "r2");

        CompatibilityRule r1 =
                new CompatibilityRule() {
                    @Override
                    public String ruleId() {
                        return "r1";
                    }

                    @Override
                    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
                        return new CompatibilityRuleOutcome(
                                List.of(err), List.of(), List.of("s1"));
                    }
                };
        CompatibilityRule r2 =
                new CompatibilityRule() {
                    @Override
                    public String ruleId() {
                        return "r2";
                    }

                    @Override
                    public CompatibilityRuleOutcome apply(CapabilitySet capabilitySet, RagConfig ragConfig) {
                        return new CompatibilityRuleOutcome(
                                List.of(), List.of(warn), List.of("s2"));
                    }
                };

        CompatibilityResult out =
                CompatibilityValidatorBridge.evaluate(List.of(r1, r2), caps, cfg);

        assertEquals(1, out.errors().size());
        assertEquals(1, out.warnings().size());
        assertEquals(List.of("s1", "s2"), out.fallbackSuggestions());
    }
}
