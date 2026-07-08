package com.uniovi.rag.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskLlmCatalogServiceTest {

    @Mock private TaskModelSettingsService taskModelSettingsService;

    @InjectMocks private TaskLlmCatalogService service;

    @Test
    void buildCatalog_includesTaskDefaultsAndSystemDefaults() {
        Map<String, Object> systemDefaults = Map.of("roles", List.of());
        when(taskModelSettingsService.getSystemDefaults()).thenReturn(systemDefaults);

        Map<String, Object> catalog = service.buildCatalog();

        assertThat(catalog.get("version")).isEqualTo(2);
        assertThat(catalog.get("overridesMapKey")).isEqualTo(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        assertThat(catalog.get("systemDefaults")).isSameAs(systemDefaults);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) catalog.get("tasks");
        assertThat(tasks).hasSize(TaskLlmTask.settingsCatalogTasks().size());

        Map<String, Object> finalAnswer = tasks.stream()
                .filter(t -> TaskLlmTask.FINAL_ANSWER.id().equals(t.get("id")))
                .findFirst()
                .orElseThrow();
        var defaults = TaskLlmRoleDefaults.forTask(TaskLlmTask.FINAL_ANSWER);
        assertThat(finalAnswer.get("defaultModelId")).isEqualTo(defaults.modelId());
        assertThat(finalAnswer.get("defaultParameters")).isEqualTo(defaults.parameters().toMap());
        assertThat(finalAnswer.get("supportedParameters"))
                .asList()
                .contains("temperature", "responseFormat", "timeoutSeconds");
    }
}
