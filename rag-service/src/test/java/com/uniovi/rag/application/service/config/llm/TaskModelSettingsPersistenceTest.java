package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmGenerationParameters;
import com.uniovi.rag.domain.llm.TaskLlmRoleSettings;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskModelSettingsPersistenceTest {

    @Mock private TaskLlmConfigResolver taskLlmConfigResolver;
    @Mock private UserProjectConfigurationService userProjectConfigurationService;
    @Mock private AdminSystemDefaultsService adminSystemDefaultsService;

    private TaskModelSettingsService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service =
                new TaskModelSettingsService(
                        taskLlmConfigResolver, userProjectConfigurationService, adminSystemDefaultsService);
        userId = UUID.randomUUID();
    }

    @Test
    void putUserSettings_persistsFullParametersForRole() {
        when(userProjectConfigurationService.getEffectiveUserConfig(userId)).thenReturn(Map.of());
        when(userProjectConfigurationService.putUserConfig(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        stubEffective(TaskLlmTask.QUERY_REWRITE);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("temperature", 0.3);
        params.put("topP", 0.95);
        params.put("maxTokens", 400);
        params.put("responseFormat", "json_object");
        params.put("think", false);
        TaskLlmRoleSettings role =
                new TaskLlmRoleSettings(
                        TaskLlmTask.QUERY_REWRITE.name(),
                        TaskLlmTask.QUERY_REWRITE.id(),
                        TaskLlmTask.QUERY_REWRITE.label(),
                        false,
                        "qwen3.5:9b",
                        false,
                        params,
                        true,
                        false);

        service.putUserSettings(userId, List.of(role));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userProjectConfigurationService).putUserConfig(any(), captor.capture());
        Map<String, Object> saved = captor.getValue();
        assertThat(saved).containsKey(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides =
                (Map<String, Object>) saved.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> row =
                (Map<String, Object>) overrides.get(TaskLlmTask.QUERY_REWRITE.id());
        assertThat(row.get("temperature")).isEqualTo(0.3);
        assertThat(row.get("topP")).isEqualTo(0.95);
        assertThat(row.get("maxTokens")).isEqualTo(400);
        assertThat(row.get("responseFormat")).isEqualTo("json_object");
    }

    private void stubEffective(TaskLlmTask task) {
        ResolvedLlmConfig base =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "base-chat",
                        "embed",
                        "KEY",
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());
        when(taskLlmConfigResolver.resolve(any(), any(), any(), any()))
                .thenReturn(
                        new TaskLlmConfigResolver.EffectiveTaskLlmConfig(
                                base,
                                task,
                                null,
                                "qwen3.5:9b",
                                0.3,
                                TaskLlmGenerationParameters.fromMap(
                                        TaskModelSettingsService.displayDefault(task).parameters()),
                                base,
                                true));
    }
}
