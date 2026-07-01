package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds task LLM catalog for {@code GET /config/task-llm-catalog}. */
@Component
public class TaskLlmCatalogService {

    public Map<String, Object> buildCatalog() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", task.id());
            entry.put("label", task.label());
            entry.put("inheritsMainModelByDefault", task.inheritsMainModelByDefault());
            entry.put("operationName", task.operationName());
            tasks.add(Map.copyOf(entry));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("tasks", List.copyOf(tasks));
        root.put("overridesMapKey", PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        return Map.copyOf(root);
    }
}
