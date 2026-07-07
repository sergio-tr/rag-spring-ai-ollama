package com.uniovi.rag.application.config;

import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds task LLM catalog for {@code GET /config/task-llm-catalog}. */
@Component
public class TaskLlmCatalogService {

    private final TaskModelSettingsService taskModelSettingsService;

    public TaskLlmCatalogService(TaskModelSettingsService taskModelSettingsService) {
        this.taskModelSettingsService = taskModelSettingsService;
    }

    public Map<String, Object> buildCatalog() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (TaskLlmTask task : TaskLlmTask.settingsCatalogTasks()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", task.id());
            entry.put("role", task.name());
            entry.put("label", task.label());
            entry.put("inheritsMainModelByDefault", task.inheritsMainModelByDefault());
            entry.put("operationName", task.operationName());
            entry.put("settingsVisible", task.visibleInSettings());
            var defaults = TaskLlmRoleDefaults.forTask(task);
            entry.put("defaultModelId", defaults.modelId());
            entry.put("defaultParameters", defaults.parameters().toMap());
            entry.put(
                    "supportedParameters",
                    List.of(
                            "temperature",
                            "topP",
                            "seed",
                            "maxTokens",
                            "presencePenalty",
                            "frequencyPenalty",
                            "responseFormat",
                            "stopSequences",
                            "think",
                            "timeoutSeconds"));
            tasks.add(Map.copyOf(entry));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("tasks", List.copyOf(tasks));
        root.put("overridesMapKey", PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        root.put("systemDefaults", taskModelSettingsService.getSystemDefaults());
        return Map.copyOf(root);
    }
}
