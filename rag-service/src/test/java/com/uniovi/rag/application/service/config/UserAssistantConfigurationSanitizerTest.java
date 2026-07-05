package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserAssistantConfigurationSanitizerTest {

    @Test
    void sanitizeForUserSave_stripsDeprecatedKeys() {
        Map<String, Object> out =
                UserAssistantConfigurationSanitizer.sanitizeForUserSave(
                        Map.of(
                                LlmConfigurationKeys.CHAT_MODEL,
                                "legacy-model",
                                LlmConfigurationKeys.TEMPERATURE,
                                0.8,
                                "topK",
                                5,
                                "toolsEnabled",
                                true,
                                "embeddingModel",
                                "nomic-embed-text",
                                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                                Map.of(
                                        TaskLlmTask.FINAL_ANSWER.id(),
                                        Map.of("model", "gemma4:12b"))));

        assertFalse(out.containsKey("llmModel"));
        assertEquals(5, out.get("topK"));
        assertFalse(out.containsKey("toolsEnabled"));
        assertEquals("nomic-embed-text", out.get("embeddingModel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) out.get("taskLlmOverrides");
        assertEquals("gemma4:12b", ((Map<?, ?>) overrides.get(TaskLlmTask.FINAL_ANSWER.id())).get("model"));
    }
}
