package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.domain.config.RetrievalParameterPolicySupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Lab retrieval parameters remain explicit and independent from chat defaults. */
class LabRagExplicitRetrievalParametersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void campaignRetrievalOverrides_applyOnTerminalLayer() {
        ObjectNode terminal = objectMapper.createObjectNode();
        terminal.put("topK", 5);
        ObjectNode merged =
                BenchmarkRuntimeParametersSupport.mergeRetrievalOverrides(
                        terminal, Map.of("topK", 11, "similarityThreshold", 0.55));
        assertEquals(11, merged.get("topK").asInt());
        assertEquals(0.55, merged.get("similarityThreshold").asDouble());
    }

    @Test
    void labContext_doesNotDependOnChatPresetPolicy() throws Exception {
        ObjectNode terminal = objectMapper.createObjectNode();
        terminal.put("topK", 12);
        try (var ignored = LabBenchmarkExecutionContext.open(terminal)) {
            assertEquals(
                    12,
                    LabBenchmarkExecutionContext.currentTerminalOverride().orElseThrow().get("topK").asInt());
        }
        assertFalse(RetrievalParameterPolicySupport.isPresetRetrievalLocked(Map.of("topK", 5)));
    }
}
