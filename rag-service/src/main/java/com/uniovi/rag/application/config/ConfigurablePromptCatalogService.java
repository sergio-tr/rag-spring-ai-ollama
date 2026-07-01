package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the prompt catalog for {@code GET /config/prompt-catalog}. */
@Component
public class ConfigurablePromptCatalogService {

    public Map<String, Object> buildCatalog() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ConfigurablePromptGroup group : ConfigurablePromptGroup.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", group.id());
            entry.put("componentLabel", group.componentLabel());
            entry.put("description", group.description());
            entry.put("defaultContent", group.defaultContent());
            entry.put("defaultSystemContent", group.defaultSystemContent());
            entry.put("requiredVariables", group.requiredVariables());
            entry.put("optionalVariables", group.optionalVariables());
            entry.put("storageKey", group.storageKey());
            entry.put("runtimeEditable", group.runtimeEditable());
            groups.add(Map.copyOf(entry));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("groups", List.copyOf(groups));
        root.put("overridesMapKey", PromptOverrideKeys.OVERRIDES_MAP_KEY);
        return Map.copyOf(root);
    }
}
