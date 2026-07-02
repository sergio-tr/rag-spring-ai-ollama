package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.domain.config.RetrievalParameterPolicySupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LabRagUsesExplicitRetrievalParametersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void mergeRetrievalOverrides_appliesExplicitCampaignTopK() {
        ObjectNode terminal = objectMapper.createObjectNode();
        terminal.put("topK", 5);
        terminal.put("similarityThreshold", 0.9);

        ObjectNode merged =
                BenchmarkRuntimeParametersSupport.mergeRetrievalOverrides(
                        terminal, Map.of("topK", 11, "similarityThreshold", 0.55));

        assertEquals(11, merged.get("topK").asInt());
        assertEquals(0.55, merged.get("similarityThreshold").asDouble());
    }

    @Test
    void presetStrip_removesPolicyMetadataOnly() {
        var stripped =
                RetrievalParameterPolicySupport.stripPresetPolicyMetadata(
                        Map.of("topK", 5, "retrievalParameterPolicy", "PRESET_LOCKED"));
        assertEquals(5, stripped.get("topK"));
        assertFalse(stripped.containsKey("retrievalParameterPolicy"));
    }

    @Test
    void labTerminalOverride_isIndependentFromChatUserDefaults() throws Exception {
        ObjectNode terminal = objectMapper.createObjectNode();
        terminal.put("topK", 12);
        terminal.put("similarityThreshold", 0.6);
        try (var ignored = LabBenchmarkExecutionContext.open(terminal)) {
            assertEquals(
                    12,
                    LabBenchmarkExecutionContext.currentTerminalOverride().orElseThrow().get("topK").asInt());
        }
        assertFalse(RetrievalParameterPolicySupport.isPresetRetrievalLocked(Map.of("topK", 5)));
    }
}
