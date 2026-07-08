package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-time compatibility shims for task LLM configuration migration. */
public final class TaskLlmConfigurationCompat {

    private TaskLlmConfigurationCompat() {}

    /**
     * Maps legacy {@code llmModel} to {@code taskLlmOverrides.final_answer} when the role override is absent.
     * Does not overwrite an existing explicit final_answer model.
     */
    public static Map<String, Object> applyLlmModelToFinalAnswerShim(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        Object llmModel = values.get(LlmConfigurationKeys.CHAT_MODEL);
        if (!(llmModel instanceof String model) || model.isBlank()) {
            return values;
        }
        Map<String, Object> merged = new LinkedHashMap<>(values);
        Map<String, Object> overrides = new LinkedHashMap<>();
        Object nested = merged.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (nested instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() instanceof String key && e.getValue() != null) {
                    overrides.put(key, e.getValue());
                }
            }
        }
        if (hasExplicitFinalAnswerModel(overrides)) {
            return values;
        }
        Map<String, Object> finalAnswer = new LinkedHashMap<>();
        finalAnswer.put("enabled", true);
        finalAnswer.put("inheritModel", false);
        finalAnswer.put("model", model.trim());
        overrides.put(TaskLlmTask.FINAL_ANSWER.id(), finalAnswer);
        merged.put(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY, Map.copyOf(overrides));
        return Map.copyOf(merged);
    }

    private static boolean hasExplicitFinalAnswerModel(Map<String, Object> overrides) {
        Object raw = overrides.get(TaskLlmTask.FINAL_ANSWER.id());
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        Object model = map.get("model");
        if (model instanceof String s && !s.isBlank()) {
            return true;
        }
        Object modelId = map.get("modelId");
        return modelId instanceof String s && !s.isBlank();
    }
}
