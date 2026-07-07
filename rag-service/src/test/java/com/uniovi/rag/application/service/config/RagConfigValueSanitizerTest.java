package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagConfigValueSanitizerTest {

    @Test
    void sanitize_allowsLlmReferenceKeys() {
        Map<String, Object> out =
                RagConfigValueSanitizer.sanitize(
                        Map.of(
                                LlmConfigurationKeys.PROVIDER,
                                "OPENAI_COMPATIBLE",
                                LlmConfigurationKeys.API_KEY_ENV,
                                "MY_KEY_ENV",
                                LlmConfigurationKeys.BASE_URL,
                                "http://litellm:4000"));

        assertEquals(3, out.size());
        assertEquals("MY_KEY_ENV", out.get(LlmConfigurationKeys.API_KEY_ENV));
    }

    @Test
    void sanitize_rejectsPlainApiKey() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RagConfigValueSanitizer.sanitize(Map.of("apiKey", "sk-secret")));
        assertTrue(ex.getMessage().contains("llmApiKeyEnv"));
    }

    @Test
    void sanitize_reportsDroppedKeys() {
        assertThat(RagConfigValueSanitizer.droppedKeys(Map.of("unknownField", "x", "topK", 5)))
                .containsExactly("unknownField");
    }

    @Test
    void sanitize_stripsUnknownKeys() {
        Map<String, Object> out = RagConfigValueSanitizer.sanitize(Map.of("unknownField", "x", "topK", 5));
        assertEquals(Map.of("topK", 5), out);
    }
}