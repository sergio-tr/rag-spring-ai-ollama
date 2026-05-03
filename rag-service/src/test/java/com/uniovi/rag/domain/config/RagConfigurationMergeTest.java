package com.uniovi.rag.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagConfigurationMergeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mergeCascade_orderMatchesResolver() {
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        features.setExpansionEnabled(true);
        features.setNerEnabled(false);
        RagConfig base =
                RagConfig.fromFeatureConfiguration(
                        features, 10, 0.7, "base-chat", "base-embed", "default", "SIMPLE");

        Optional<Map<String, Object>> system = Optional.of(Map.of("topK", 3));
        Optional<Map<String, Object>> user = Optional.of(Map.of("llmModel", "user-llm"));
        Optional<Map<String, Object>> project = Optional.of(Map.of("topK", 9));
        ObjectNode runtime = objectMapper.createObjectNode();
        runtime.put("topK", 42);

        RagConfig out =
                RagConfigurationMerge.mergeCascade(
                        base, system, user, project, runtime, objectMapper);

        assertEquals(42, out.topK());
        assertEquals("user-llm", out.llmModel());
        assertTrue(out.expansionEnabled());
    }
}
