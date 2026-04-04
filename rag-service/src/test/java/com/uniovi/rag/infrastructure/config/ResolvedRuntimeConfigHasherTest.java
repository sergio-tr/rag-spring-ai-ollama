package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexPreview;
import com.uniovi.rag.domain.config.prompt.PromptStack;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedRuntimeConfigHasherTest {

    @Test
    void sha256Hex_stableForSameLogicalConfig() {
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        RagReasoningProperties reasoning = new RagReasoningProperties();
        RagConfig core =
                RagConfig.fromFeatureConfiguration(
                        features, 10, 0.7, "m1", "emb1", "default", reasoning.getStrategy() != null ? reasoning.getStrategy() : "SIMPLE");
        ConfigProvenance prov = new ConfigProvenance(null, null, null, List.of(), null, null);
        ResolvedRuntimeConfig a =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        PromptStack.empty(),
                        new ReindexPreview(false, List.of()),
                        prov,
                        core);
        ResolvedRuntimeConfig b =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        PromptStack.empty(),
                        new ReindexPreview(false, List.of()),
                        prov,
                        core);
        assertThat(ResolvedRuntimeConfigHasher.sha256Hex(a)).isEqualTo(ResolvedRuntimeConfigHasher.sha256Hex(b));
    }
}
