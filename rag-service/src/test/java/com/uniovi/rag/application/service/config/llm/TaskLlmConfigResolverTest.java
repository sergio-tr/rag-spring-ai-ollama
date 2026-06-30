package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskLlmConfigResolverTest {

    @Mock private ConfigurationSourcePort configurationSource;
    @Mock private ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    private TaskLlmConfigResolver resolver;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        resolver = new TaskLlmConfigResolver(configurationSource, resolvedLlmConfigResolver, new ObjectMapper());
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
    }

    private void stubDefaultLayers() {
        when(resolvedLlmConfigResolver.resolve(any(), any(), any())).thenReturn(baseConfig());
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.empty());
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.empty());
    }

    @Test
    void fromOperation_mapsQueryRewrite() {
        assertThat(TaskLlmTask.fromOperation("query-rewrite")).contains(TaskLlmTask.QUERY_REWRITE);
        assertThat(TaskLlmTask.fromOperation("conversation-condense")).contains(TaskLlmTask.MEMORY_CONDENSE);
        assertThat(TaskLlmTask.fromOperation("runtime-judge")).contains(TaskLlmTask.RUNTIME_JUDGE);
        assertThat(TaskLlmTask.fromOperation("evaluation-judge")).contains(TaskLlmTask.EVALUATION_JUDGE);
        assertThat(TaskLlmTask.fromOperation("metadata-yes-no-filter")).contains(TaskLlmTask.METADATA_REASONING);
        assertThat(TaskLlmTask.fromOperation("metadata-topic-filter")).contains(TaskLlmTask.METADATA_REASONING);
    }

    @Test
    void resolveSecondaryCall_appliesTaskModelOverride() {
        stubDefaultLayers();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.QUERY_REWRITE.id(),
                        Map.of("enabled", true, "model", "override-chat-model", "temperature", 0.2)));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "query-rewrite", null, null);

        assertThat(call.taskOverrideApplied()).isTrue();
        assertThat(call.effectiveModel()).isEqualTo("override-chat-model");
        assertThat(call.effectiveTemperature()).isEqualTo(0.2);
        assertThat(call.effectiveConfig().additionalParameters()).isEmpty();
    }

    @Test
    void resolveSecondaryCall_inheritsBaseWhenNoOverride() {
        stubDefaultLayers();
        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "query-rewrite", null, null);

        assertThat(call.taskOverrideApplied()).isFalse();
        assertThat(call.effectiveModel()).isEqualTo("base-chat");
        assertThat(call.effectiveTemperature()).isEqualTo(0.1);
    }

    @Test
    void resolveSecondaryCall_appliesTopPAndMaxTokensFromOverride() {
        stubDefaultLayers();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.MEMORY_CONDENSE.id(),
                        Map.of(
                                "enabled",
                                true,
                                "temperature",
                                0.05,
                                "topP",
                                0.9,
                                "maxTokens",
                                512)));
        when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "conversation-condense", null, null);

        assertThat(call.taskOverrideApplied()).isTrue();
        assertThat(call.effectiveTemperature()).isEqualTo(0.05);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("topP", 0.9);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 512);
    }

    @Test
    void resolveSecondaryCall_metadataOperationMapsToMetadataReasoning() {
        when(resolvedLlmConfigResolver.resolve(isNull(), isNull(), isNull())).thenReturn(baseConfig());
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(null, null, "metadata-filter-and-list", null, null);
        assertThat(call.effectiveModel()).isEqualTo("base-chat");
    }

    @Test
    void resolveSecondaryCall_appliesEvaluationJudgeOverride() {
        stubDefaultLayers();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.EVALUATION_JUDGE.id(),
                        Map.of("enabled", true, "model", "judge-override", "temperature", 0.0)));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "evaluation-judge", null, null);

        assertThat(call.taskOverrideApplied()).isTrue();
        assertThat(call.effectiveModel()).isEqualTo("judge-override");
        assertThat(call.effectiveTemperature()).isEqualTo(0.0);
    }

    private static ResolvedLlmConfig baseConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "base-chat",
                "embed-model",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
