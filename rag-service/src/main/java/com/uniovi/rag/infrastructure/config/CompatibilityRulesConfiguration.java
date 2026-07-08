package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.rules.CompatibilityRule;
import com.uniovi.rag.domain.config.rules.HeuristicCombinationRule;
import com.uniovi.rag.domain.config.rules.MutuallyExclusiveCapabilitiesRule;
import com.uniovi.rag.domain.config.rules.NumericRagConfigRule;
import com.uniovi.rag.domain.config.rules.RequiresCapabilitiesRule;
import com.uniovi.rag.domain.config.rules.StructuredSearchRetrievalUnsupportedRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.List;

@Configuration
public class CompatibilityRulesConfiguration {

    @Bean
    public List<CompatibilityRule> compatibilityRules() {
        return List.of(
                new MutuallyExclusiveCapabilitiesRule(
                        "mutual_function_calling_naive_corpus",
                        EnumSet.of(Capability.FUNCTION_CALLING, Capability.NAIVE_FULL_CORPUS_PROMPT)),
                // metadata+tools hard coupling removed for Phase 2.6 product presets (P4 metadata context without tools).
                // HeuristicCombinationRule still warns when metadata is on without tools.
                new RequiresCapabilitiesRule(
                        "post_retrieval_requires_retrieval",
                        EnumSet.of(Capability.POST_RETRIEVAL),
                        EnumSet.of(Capability.USE_RETRIEVAL)),
                new NumericRagConfigRule("numeric_rag_config"),
                new StructuredSearchRetrievalUnsupportedRule("structured_search_retrieval_unsupported"),
                new HeuristicCombinationRule("heuristic_combinations"));
    }
}
