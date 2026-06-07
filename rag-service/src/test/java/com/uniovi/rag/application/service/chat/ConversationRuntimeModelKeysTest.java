package com.uniovi.rag.application.service.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuntimeModelKeysTest {

    @Test
    void copyWithoutModelKeys_removesLlmAndClassifierEntries() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("useRetrieval", false);
        raw.put(ConversationRuntimeModelKeys.LLM_MODEL, "x");
        raw.put(ConversationRuntimeModelKeys.CLASSIFIER_MODEL_ID, "y");
        Map<String, Object> out = ConversationRuntimeModelKeys.copyWithoutModelKeys(raw);
        assertThat(out).containsOnlyKeys("useRetrieval");
    }
}
