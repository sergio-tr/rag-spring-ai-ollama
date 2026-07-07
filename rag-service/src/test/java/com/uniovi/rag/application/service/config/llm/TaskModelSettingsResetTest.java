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
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskModelSettingsResetTest {

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
        stubAllRoles();
    }

    @Test
    void resetUserRole_removesOnlyThatRoleOverride() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(
                TaskLlmTask.QUERY_REWRITE.id(),
                Map.of("model", "custom", "temperature", 0.2));
        overrides.put(TaskLlmTask.LLM_RANKER.id(), Map.of("temperature", 0.0));
        Map<String, Object> current = Map.of(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY, overrides);
        when(userProjectConfigurationService.getEffectiveUserConfig(userId)).thenReturn(current);
        when(userProjectConfigurationService.putUserConfig(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        service.resetUserRole(userId, TaskLlmTask.QUERY_REWRITE);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userProjectConfigurationService).putUserConfig(any(), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> savedOverrides =
                (Map<String, Object>) captor.getValue().get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        assertThat(savedOverrides).doesNotContainKey(TaskLlmTask.QUERY_REWRITE.id());
        assertThat(savedOverrides).containsKey(TaskLlmTask.LLM_RANKER.id());
    }

    @Test
    void resetUserAll_clearsTaskOverridesMapKey() {
        Map<String, Object> current =
                Map.of(
                        PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                        Map.of(TaskLlmTask.FINAL_ANSWER.id(), Map.of("temperature", 0.5)));
        when(userProjectConfigurationService.getEffectiveUserConfig(userId)).thenReturn(current);
        when(userProjectConfigurationService.putUserConfig(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        service.resetUserAll(userId);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userProjectConfigurationService).putUserConfig(any(), captor.capture());
        assertThat(captor.getValue()).doesNotContainKey(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
    }

    private void stubAllRoles() {
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
        for (TaskLlmTask task : TaskLlmTask.catalogTasks()) {
            when(taskLlmConfigResolver.resolve(any(), any(), any(), any()))
                    .thenReturn(
                            new TaskLlmConfigResolver.EffectiveTaskLlmConfig(
                                    base,
                                    task,
                                    null,
                                    TaskModelSettingsService.displayDefault(task).modelId(),
                                    0.1,
                                    TaskLlmGenerationParameters.fromMap(
                                            TaskModelSettingsService.displayDefault(task).parameters()),
                                    base,
                                    false));
        }
    }
}
