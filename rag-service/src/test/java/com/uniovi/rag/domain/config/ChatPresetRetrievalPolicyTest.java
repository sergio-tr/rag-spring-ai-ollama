package com.uniovi.rag.domain.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPresetRetrievalPolicyTest {

    @Test
    void isPresetRetrievalLocked_trueForPolicyFlag() {
        assertTrue(
                RetrievalParameterPolicySupport.isPresetRetrievalLocked(
                        Map.of("retrievalParameterPolicy", "PRESET_LOCKED")));
    }

    @Test
    void isPresetRetrievalLocked_trueForBooleanLock() {
        assertTrue(
                RetrievalParameterPolicySupport.isPresetRetrievalLocked(
                        Map.of("lockRetrievalParameters", true)));
    }

    @Test
    void isPresetRetrievalLocked_falseWhenAbsent() {
        assertFalse(RetrievalParameterPolicySupport.isPresetRetrievalLocked(Map.of("topK", 5)));
    }

    @Test
    void mergePresetLayer_includesPresetRetrievalParameters() {
        Map<String, Object> target = new LinkedHashMap<>();
        RetrievalParameterPolicySupport.mergePresetLayer(
                target, Map.of("topK", 3, "similarityThreshold", 0.9, "useAdvisor", true));

        assertEquals(3, target.get("topK"));
        assertEquals(0.9, target.get("similarityThreshold"));
        assertEquals(true, target.get("useAdvisor"));
    }

    @Test
    void mergePresetLayer_omitsPolicyMetadata() {
        Map<String, Object> target = new LinkedHashMap<>();
        RetrievalParameterPolicySupport.mergePresetLayer(
                target,
                Map.of(
                        "topK",
                        3,
                        "similarityThreshold",
                        0.9,
                        "retrievalParameterPolicy",
                        "PRESET_LOCKED"));

        assertEquals(3, target.get("topK"));
        assertFalse(target.containsKey("retrievalParameterPolicy"));
    }

    @Test
    void mergePresetLayer_includesLockedRetrievalParameters() {
        Map<String, Object> target = new LinkedHashMap<>();
        RetrievalParameterPolicySupport.mergePresetLayer(
                target,
                Map.of(
                        "topK",
                        3,
                        "similarityThreshold",
                        0.9,
                        "retrievalParameterPolicy",
                        "PRESET_LOCKED"));

        assertEquals(3, target.get("topK"));
        assertEquals(0.9, target.get("similarityThreshold"));
    }

    @Test
    void sourceForKey_conversationOverrideWins() {
        assertEquals(
                RetrievalParameterPolicy.CONVERSATION_CUSTOM,
                RetrievalParameterPolicySupport.sourceForKey(
                        "topK", Map.of("topK", 5), Map.of("topK", 3)));
    }

    @Test
    void sourceForKey_presetRecommendedWhenNoOverride() {
        assertEquals(
                RetrievalParameterPolicy.PRESET_LOCKED,
                RetrievalParameterPolicySupport.sourceForKey("topK", Map.of(), Map.of("topK", 3)));
    }
}
