package com.uniovi.rag.domain.llm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Built-in system defaults per {@link TaskLlmTask} role. */
public final class TaskLlmRoleDefaults {

    private static final String DEFAULT_JSON_OBJECT = "json_object";
    private static final String DEFAULT_QWEN_9B = "qwen3.5:9b";

    private static final Map<TaskLlmTask, RoleDefault> DEFAULTS = buildDefaults();

    private TaskLlmRoleDefaults() {}

    public record RoleDefault(String modelId, TaskLlmGenerationParameters parameters) {}

    public static RoleDefault forTask(TaskLlmTask task) {
        return DEFAULTS.getOrDefault(task, DEFAULTS.get(TaskLlmTask.QUERY_REWRITE));
    }

    public static Map<TaskLlmTask, RoleDefault> all() {
        return Map.copyOf(DEFAULTS);
    }

    private static Map<TaskLlmTask, RoleDefault> buildDefaults() {
        Map<TaskLlmTask, RoleDefault> map = new EnumMap<>(TaskLlmTask.class);
        map.put(
                TaskLlmTask.FINAL_ANSWER,
                new RoleDefault(
                        "qwen3.5:9b",
                        params(0.1, 0.9, 42L, 1024, 0.0, 0.0, "text", false, null)));
        map.put(
                TaskLlmTask.QUERY_REWRITE,
                new RoleDefault(
                        "qwen3.5:27b",
                        params(0.1, 0.85, 42L, 384, 0.0, 0.0, "text", false, null)));
        map.put(
                TaskLlmTask.QUERY_EXPANSION,
                new RoleDefault(
                        "qwen3.5:27b",
                        params(0.2, 0.85, 42L, 512, 0.0, 0.0, "text", false, null)));
        map.put(
                TaskLlmTask.MEMORY_CONDENSE,
                new RoleDefault(
                        "ministral-3:14b",
                        params(0.1, 0.9, 42L, 768, 0.0, 0.0, "text", false, null)));
        map.put(
                TaskLlmTask.RUNTIME_JUDGE,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.RUNTIME_JUDGE_RETRY,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.FACTUAL_VERIFIER,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.LLM_RANKER,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.METADATA_REASONING,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.NER_EXTRACTION,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 1024, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.EVALUATION_JUDGE,
                new RoleDefault(
                        DEFAULT_QWEN_9B,
                        params(0.0, 0.9, 42L, 512, 0.0, 0.0, DEFAULT_JSON_OBJECT, false, null)));
        map.put(
                TaskLlmTask.LLM_BASELINE_EVALUATION,
                new RoleDefault(
                        "gemma4:12b",
                        params(0.1, 1.0, null, 1024, 0.0, 0.0, "text", false, null)));
        return Map.copyOf(map);
    }

    private static TaskLlmGenerationParameters params(
            double temperature,
            double topP,
            Long seed,
            int maxTokens,
            double presencePenalty,
            double frequencyPenalty,
            String responseFormat,
            boolean think,
            Integer timeoutSeconds) {
        return new TaskLlmGenerationParameters(
                temperature,
                topP,
                seed,
                maxTokens,
                presencePenalty,
                frequencyPenalty,
                responseFormat,
                List.of(),
                think,
                timeoutSeconds);
    }
}
