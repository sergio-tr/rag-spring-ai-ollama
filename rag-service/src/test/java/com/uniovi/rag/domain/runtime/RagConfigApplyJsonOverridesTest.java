package com.uniovi.rag.domain.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagConfigApplyJsonOverridesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static RagConfig sampleBase() {
        return new RagConfig(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                10,
                0.35,
                "llm-main",
                "emb-main",
                "cls-main",
                "cot",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    @Test
    void applyJsonOverrides_returnsBase_whenJsonNull() {
        RagConfig base = sampleBase();
        assertThat(RagConfig.applyJsonOverrides(base, null)).isSameAs(base);
    }

    @Test
    void applyJsonOverrides_returnsBase_whenJsonEmptyObject() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.readTree("{}");
        assertThat(RagConfig.applyJsonOverrides(base, node)).isSameAs(base);
    }

    @Test
    void applyJsonOverrides_nullNode_returnsBase() {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.nullNode();
        assertThat(RagConfig.applyJsonOverrides(base, node)).isSameAs(base);
    }

    @Test
    void applyJsonOverrides_updatesBooleansNumbersAndStrings() throws Exception {
        RagConfig base = sampleBase();
        String json =
                """
                {
                  "expansionEnabled": false,
                  "topK": 42,
                  "similarityThreshold": 0.91,
                  "llmModel": "other-llm",
                  "reasoningStrategy": "plan"
                }
                """;
        RagConfig out = RagConfig.applyJsonOverrides(base, MAPPER.readTree(json));

        assertThat(out.expansionEnabled()).isFalse();
        assertThat(out.nerEnabled()).isTrue();
        assertThat(out.topK()).isEqualTo(42);
        assertThat(out.similarityThreshold()).isEqualTo(0.91);
        assertThat(out.llmModel()).isEqualTo("other-llm");
        assertThat(out.reasoningStrategy()).isEqualTo("plan");
        assertThat(out.embeddingModel()).isEqualTo("emb-main");
    }

    @Test
    void applyJsonOverrides_ignoresNonBooleanForBooleanFields() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.readTree("{\"useRetrieval\": \"yes\"}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.useRetrieval()).isTrue();
    }

    @Test
    void applyJsonOverrides_ignoresNonNumberForNumericFields() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.readTree("{\"topK\": \"many\", \"similarityThreshold\": false}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.topK()).isEqualTo(10);
        assertThat(out.similarityThreshold()).isEqualTo(0.35);
    }

    @Test
    void applyJsonOverrides_ignoresNonTextForTextFields() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.readTree("{\"llmModel\": 99, \"classifierModelId\": {}}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.llmModel()).isEqualTo("llm-main");
        assertThat(out.classifierModelId()).isEqualTo("cls-main");
    }

    @Test
    void applyJsonOverrides_updatesMaterializationStrategy() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node = MAPPER.readTree("{\"materializationStrategy\": \"DOCUMENT_LEVEL\"}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.materializationStrategy()).isEqualTo(MaterializationStrategy.DOCUMENT_LEVEL);
    }

    @Test
    void applyJsonOverrides_supportsClassifierModelAndEmbedding() throws Exception {
        RagConfig base = sampleBase();
        JsonNode node =
                MAPPER.readTree("{\"embeddingModel\": \"e2\", \"classifierModelId\": \"c2\"}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.embeddingModel()).isEqualTo("e2");
        assertThat(out.classifierModelId()).isEqualTo("c2");
    }

    @Test
    void applyJsonOverrides_updatesClarificationEnabled() throws Exception {
        RagConfig base = sampleBase();
        assertThat(base.clarificationEnabled()).isFalse();
        JsonNode node = MAPPER.readTree("{\"clarificationEnabled\": true}");
        RagConfig out = RagConfig.applyJsonOverrides(base, node);
        assertThat(out.clarificationEnabled()).isTrue();
    }
}
