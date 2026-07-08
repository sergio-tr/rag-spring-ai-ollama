package com.uniovi.rag.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurablePromptCatalogServiceTest {

    private final ConfigurablePromptCatalogService service = new ConfigurablePromptCatalogService();

    @Test
    void buildCatalog_listsEveryPromptGroupWithMetadata() {
        Map<String, Object> catalog = service.buildCatalog();

        assertThat(catalog.get("version")).isEqualTo(1);
        assertThat(catalog.get("overridesMapKey")).isEqualTo(PromptOverrideKeys.OVERRIDES_MAP_KEY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) catalog.get("groups");
        assertThat(groups).hasSize(ConfigurablePromptGroup.values().length);

        Map<String, Object> first = groups.getFirst();
        assertThat(first)
                .containsKeys(
                        "id",
                        "componentLabel",
                        "description",
                        "defaultContent",
                        "defaultSystemContent",
                        "requiredVariables",
                        "optionalVariables",
                        "storageKey",
                        "runtimeEditable");
        assertThat(first.get("id")).isEqualTo(ConfigurablePromptGroup.values()[0].id());
    }
}
