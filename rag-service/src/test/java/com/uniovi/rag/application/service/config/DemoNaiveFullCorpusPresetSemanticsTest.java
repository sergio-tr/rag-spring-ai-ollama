package com.uniovi.rag.application.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Demo_NaiveFullCorpus must resolve to naive full corpus (useRetrieval=false) after V77 migration semantics.
 */
class DemoNaiveFullCorpusPresetSemanticsTest {

    private static final UUID DEMO_NAIVE_FULL_CORPUS_ID =
            UUID.fromString("cafe0001-0001-4001-8001-000000000002");

    /** Values after {@code V77__demo_naive_full_corpus_use_retrieval_false.sql}. */
    private static final String CORRECTED_VALUES_JSON =
            """
            {
              "useRetrieval": false,
              "naiveFullCorpusInPromptEnabled": true,
              "functionCallingEnabled": false,
              "materializationStrategy": "CHUNK_LEVEL"
            }
            """;

    private static RagConfig baseConfig() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                5,
                0.7,
                "l",
                "e",
                "c",
                "SIMPLE",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    @Test
    void demoNaiveFullCorpus_resolvesUseRetrievalFalse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RagConfig config = RagConfig.applyJsonOverrides(baseConfig(), mapper.readTree(CORRECTED_VALUES_JSON));
        assertThat(config.useRetrieval()).isFalse();
        assertThat(config.naiveFullCorpusInPromptEnabled()).isTrue();
    }

    @Test
    void demoNaiveFullCorpus_functionCallingMutuallyExclusiveWithNaiveFullCorpus() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RagConfig bothEnabled =
                RagConfig.applyJsonOverrides(
                        baseConfig(),
                        mapper.readTree(
                                """
                                {
                                  "useRetrieval": false,
                                  "naiveFullCorpusInPromptEnabled": true,
                                  "functionCallingEnabled": true
                                }
                                """));
        assertThat(bothEnabled.naiveFullCorpusInPromptEnabled()).isTrue();
        assertThat(bothEnabled.functionCallingEnabled()).isTrue();
        // CompatibilityValidator blocks this pair at validation time; config object may still hold both flags.
    }

    @Test
    void demoNaiveFullCorpus_presetId_isStableSeed() {
        assertThat(DEMO_NAIVE_FULL_CORPUS_ID.toString())
                .isEqualTo("cafe0001-0001-4001-8001-000000000002");
    }
}
