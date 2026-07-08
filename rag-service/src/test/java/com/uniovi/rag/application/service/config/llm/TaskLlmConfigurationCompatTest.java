package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskLlmConfigurationCompatTest {

    @Test
    void applyLlmModelToFinalAnswerShim_mapsLegacyLlmModelWhenFinalAnswerMissing() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LlmConfigurationKeys.CHAT_MODEL, "legacy-chat-model");

        Map<String, Object> result = TaskLlmConfigurationCompat.applyLlmModelToFinalAnswerShim(values);

        @SuppressWarnings("unchecked")
        Map<String, Object> overrides =
                (Map<String, Object>) result.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> finalAnswer = (Map<String, Object>) overrides.get(TaskLlmTask.FINAL_ANSWER.id());
        assertThat(finalAnswer.get("model")).isEqualTo("legacy-chat-model");
        assertThat(finalAnswer.get("inheritModel")).isEqualTo(false);
    }

    @Test
    void applyLlmModelToFinalAnswerShim_doesNotOverwriteExistingFinalAnswerModel() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LlmConfigurationKeys.CHAT_MODEL, "legacy-chat-model");
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(TaskLlmTask.FINAL_ANSWER.id(), Map.of("model", "explicit-final-answer")));

        Map<String, Object> result = TaskLlmConfigurationCompat.applyLlmModelToFinalAnswerShim(values);

        assertThat(result).isSameAs(values);
    }
}
