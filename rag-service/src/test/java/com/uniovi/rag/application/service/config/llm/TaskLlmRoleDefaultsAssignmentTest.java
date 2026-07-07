package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskLlmRoleDefaultsAssignmentTest {

    @Mock private ConfigurationSourcePort configurationSource;
    @Mock private ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    @Mock private RagRuntimeProperties ragRuntimeProperties;
    @Mock private SystemTaskLlmDefaultsProvider systemTaskLlmDefaultsProvider;

    private TaskLlmConfigResolver resolver;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        lenient()
                .when(systemTaskLlmDefaultsProvider.baselineFor(any(TaskLlmTask.class)))
                .thenAnswer(inv -> TaskLlmRoleDefaults.forTask(inv.getArgument(0)));
        resolver =
                new TaskLlmConfigResolver(
                        configurationSource,
                        resolvedLlmConfigResolver,
                        new ObjectMapper(),
                        ragRuntimeProperties,
                        systemTaskLlmDefaultsProvider);
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        lenient().when(ragRuntimeProperties.hasSecondaryModel()).thenReturn(false);
        lenient().when(resolvedLlmConfigResolver.resolve(any(), any(), any())).thenReturn(baseConfig());
        lenient().when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        lenient().when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.empty());
        lenient().when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.empty());
    }

    @Test
    void builtInDefaults_matchLlm5Assignment() {
        assertRole(
                TaskLlmTask.FINAL_ANSWER,
                "qwen3.5:9b",
                Map.of("temperature", 0.1, "topP", 0.9, "seed", 42L, "maxTokens", 1024, "responseFormat", "text"));
        assertRole(
                TaskLlmTask.QUERY_REWRITE,
                "qwen3.5:27b",
                Map.of("temperature", 0.1, "topP", 0.85, "seed", 42L, "maxTokens", 384, "responseFormat", "text"));
        assertRole(
                TaskLlmTask.QUERY_EXPANSION,
                "qwen3.5:27b",
                Map.of("temperature", 0.2, "topP", 0.85, "seed", 42L, "maxTokens", 512, "responseFormat", "text"));
        assertRole(
                TaskLlmTask.MEMORY_CONDENSE,
                "ministral-3:14b",
                Map.of("temperature", 0.1, "topP", 0.9, "seed", 42L, "maxTokens", 768, "responseFormat", "text"));
        assertRole(
                TaskLlmTask.RUNTIME_JUDGE,
                "qwen3.5:9b",
                Map.of("temperature", 0.0, "topP", 0.9, "seed", 42L, "maxTokens", 512, "responseFormat", "json_object"));
        assertRole(
                TaskLlmTask.EVALUATION_JUDGE,
                "gemma4:12b",
                Map.of("temperature", 0.0, "topP", 0.9, "seed", 42L, "maxTokens", 512, "responseFormat", "json_object"));
        assertRole(
                TaskLlmTask.NER_EXTRACTION,
                "qwen3.5:9b",
                Map.of("temperature", 0.0, "topP", 0.9, "seed", 42L, "maxTokens", 1024, "responseFormat", "json_object"));
        assertThat(TaskLlmTask.EVALUATION_JUDGE.inheritsMainModelByDefault()).isFalse();
    }

    @Test
    void resolver_appliesLlm5DefaultsForSecondaryRoles() {
        TaskLlmConfigResolver.SecondaryCallConfig rewrite =
                resolver.resolveSecondaryCall(userId, projectId, "query-rewrite", null, null);
        assertThat(rewrite.effectiveModel()).isEqualTo("qwen3.5:27b");
        assertThat(rewrite.effectiveTemperature()).isEqualTo(0.1);
        assertThat(rewrite.effectiveConfig().additionalParameters()).containsEntry("topP", 0.85);
        assertThat(rewrite.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 384);
        assertThat(rewrite.effectiveConfig().additionalParameters()).containsEntry("seed", 42L);

        TaskLlmConfigResolver.SecondaryCallConfig memory =
                resolver.resolveSecondaryCall(userId, projectId, "conversation-condense", null, null);
        assertThat(memory.effectiveModel()).isEqualTo("ministral-3:14b");
        assertThat(memory.effectiveTemperature()).isEqualTo(0.1);
        assertThat(memory.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 768);

        TaskLlmConfigResolver.SecondaryCallConfig judge =
                resolver.resolveSecondaryCall(userId, projectId, "evaluation-judge", null, null);
        assertThat(judge.effectiveModel()).isEqualTo("gemma4:12b");
        assertThat(judge.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 512);
        assertThat(judge.effectiveConfig().additionalParameters().get("responseFormat"))
                .isEqualTo(Map.of("type", "json_object"));
    }

    private static void assertRole(TaskLlmTask task, String model, Map<String, Object> params) {
        TaskLlmRoleDefaults.RoleDefault defaults = TaskLlmRoleDefaults.forTask(task);
        assertThat(defaults.modelId()).isEqualTo(model);
        assertThat(defaults.parameters().temperature()).isEqualTo(params.get("temperature"));
        assertThat(defaults.parameters().topP()).isEqualTo(params.get("topP"));
        assertThat(defaults.parameters().seed()).isEqualTo(params.get("seed"));
        assertThat(defaults.parameters().maxTokens()).isEqualTo(params.get("maxTokens"));
        assertThat(defaults.parameters().responseFormat()).isEqualTo(params.get("responseFormat"));
    }

    private static ResolvedLlmConfig baseConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "qwen3.5:9b",
                "embed-model",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
