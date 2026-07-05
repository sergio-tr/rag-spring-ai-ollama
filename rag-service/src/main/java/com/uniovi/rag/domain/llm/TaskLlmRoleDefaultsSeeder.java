package com.uniovi.rag.domain.llm;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds seed {@code taskLlmOverrides} maps from {@link TaskLlmRoleDefaults} (bootstrap/migration only). */
public final class TaskLlmRoleDefaultsSeeder {

    private TaskLlmRoleDefaultsSeeder() {}

    public static Map<String, Object> seedOverrideMap(TaskLlmTask task) {
        TaskLlmRoleDefaults.RoleDefault defaults = TaskLlmRoleDefaults.forTask(task);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", true);
        map.put("inheritModel", task.inheritsMainModelByDefault());
        map.put("model", defaults.modelId());
        map.put("inheritParameters", false);
        map.putAll(defaults.parameters().toMap());
        return Map.copyOf(map);
    }

    public static Map<String, Object> seedAllRoleOverrides() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            overrides.put(task.id(), seedOverrideMap(task));
        }
        return Map.copyOf(overrides);
    }

    /** Patch for {@code default_system_configuration.values} - merges missing roles only. */
    public static Map<String, Object> mergeMissingSystemDefaults(Map<String, Object> existingValues) {
        Map<String, Object> values = new LinkedHashMap<>(existingValues != null ? existingValues : Map.of());
        Map<String, Object> overrides = new LinkedHashMap<>();
        Object nested = values.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (nested instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() instanceof String key && e.getValue() != null) {
                    overrides.put(key, e.getValue());
                }
            }
        }
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            overrides.putIfAbsent(task.id(), seedOverrideMap(task));
        }
        values.put(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY, Map.copyOf(overrides));
        return Map.copyOf(values);
    }

    public static boolean hasCompleteTaskLlmDefaults(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        Object nested = values.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (!(nested instanceof Map<?, ?> map)) {
            return false;
        }
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            if (!map.containsKey(task.id())) {
                return false;
            }
        }
        return true;
    }
}
