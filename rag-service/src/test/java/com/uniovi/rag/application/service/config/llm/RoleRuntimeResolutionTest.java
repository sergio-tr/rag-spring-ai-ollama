package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleRuntimeResolutionTest {

    @Mock private ConfigurationSourcePort configurationSource;
    @Mock private ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    @Mock private RagRuntimeProperties ragRuntimeProperties;

    private TaskLlmConfigResolver resolver;
    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        resolver =
                new TaskLlmConfigResolver(
                        configurationSource, resolvedLlmConfigResolver, new ObjectMapper(), ragRuntimeProperties);
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        lenient().when(ragRuntimeProperties.hasSecondaryModel()).thenReturn(false);
        when(resolvedLlmConfigResolver.resolve(any(), any(), any())).thenReturn(baseConfig());
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.empty());
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.empty());
    }

    @Test
    void resolveSecondaryCall_appliesResponseFormatStopAndPenalties() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.RUNTIME_JUDGE.id(),
                        Map.of(
                                "enabled",
                                true,
                                "temperature",
                                0.0,
                                "topP",
                                0.9,
                                "maxTokens",
                                600,
                                "presencePenalty",
                                0.1,
                                "frequencyPenalty",
                                -0.2,
                                "responseFormat",
                                "json_object",
                                "stopSequences",
                                List.of("END"),
                                "think",
                                false)));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "runtime-judge", null, null);

        assertThat(call.taskOverrideApplied()).isTrue();
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("topP", 0.9);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 600);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("presencePenalty", 0.1);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("frequencyPenalty", -0.2);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("think", false);
        assertThat(call.effectiveConfig().additionalParameters().get("responseFormat"))
                .isEqualTo(Map.of("type", "json_object"));
        assertThat(call.effectiveConfig().additionalParameters().get("stop"))
                .isEqualTo(List.of("END"));
    }

    @Test
    void resolveSecondaryCall_backwardCompatibleModelAndTemperatureOnly() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.QUERY_REWRITE.id(),
                        Map.of("enabled", true, "model", "override-chat-model", "temperature", 0.2)));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "query-rewrite", null, null);

        assertThat(call.effectiveModel()).isEqualTo("override-chat-model");
        assertThat(call.effectiveTemperature()).isEqualTo(0.2);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("topP", 1.0);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 256);
    }

    @Test
    void resolveSecondaryCall_finalAnswerInheritsMainModelByDefault() {
        TaskLlmConfigResolver.SecondaryCallConfig call =
                resolver.resolveSecondaryCall(userId, projectId, "function-calling", null, null);

        assertThat(call.effectiveModel()).isEqualTo("base-chat");
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
