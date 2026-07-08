package com.uniovi.rag.application.service.config.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
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
class FinalAnswerRuntimeResolutionTest {

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
        lenient().when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        lenient().when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.empty());
        lenient().when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.empty());
    }

    @Test
    void resolveFinalAnswer_explicitModelOverrideUsedForPrimaryChat() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.FINAL_ANSWER.id(),
                        Map.of("enabled", true, "inheritModel", false, "model", "explicit-final-model", "temperature", 0.4)));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.FinalAnswerCallConfig call =
                resolver.resolveFinalAnswer(executionContext(Optional.empty()), baseConfig("base-chat"));

        assertThat(call.effectiveModel()).isEqualTo("explicit-final-model");
        assertThat(call.effectiveTemperature()).isEqualTo(0.4);
        assertThat(call.inheritModel()).isFalse();
        assertThat(call.modelSource()).isEqualTo("project");
        assertThat(call.taskOverrideApplied()).isTrue();
    }

    @Test
    void resolveFinalAnswer_inheritModelUsesPrimaryAndRoleParams() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.FINAL_ANSWER.id(),
                        Map.of("enabled", true, "inheritModel", true, "inheritParameters", false, "temperature", 0.15, "maxTokens", 900)));
        when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.of(values));

        TaskLlmConfigResolver.FinalAnswerCallConfig call =
                resolver.resolveFinalAnswer(executionContext(Optional.empty()), baseConfig("primary-chat"));

        assertThat(call.inheritModel()).isTrue();
        assertThat(call.effectiveModel()).isEqualTo("primary-chat");
        assertThat(call.effectiveTemperature()).isEqualTo(0.15);
        assertThat(call.effectiveConfig().additionalParameters()).containsEntry("maxTokens", 900);
        assertThat(call.modelSource()).isEqualTo("primary_inherited");
        assertThat(call.paramSource()).isEqualTo("final_answer_role");
    }

    @Test
    void resolveFinalAnswer_projectOverrideWinsOverUserOverride() {
        Map<String, Object> userValues = new LinkedHashMap<>();
        userValues.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.FINAL_ANSWER.id(),
                        Map.of("enabled", true, "inheritModel", false, "model", "user-final")));
        Map<String, Object> projectValues = new LinkedHashMap<>();
        projectValues.put(
                PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY,
                Map.of(
                        TaskLlmTask.FINAL_ANSWER.id(),
                        Map.of("enabled", true, "inheritModel", false, "model", "project-final")));
        when(configurationSource.loadUserDefault(userId)).thenReturn(Optional.of(userValues));
        when(configurationSource.loadProject(userId, projectId)).thenReturn(Optional.of(projectValues));

        TaskLlmConfigResolver.FinalAnswerCallConfig call =
                resolver.resolveFinalAnswer(executionContext(Optional.empty()), baseConfig("base-chat"));

        assertThat(call.effectiveModel()).isEqualTo("project-final");
        assertThat(call.modelSource()).isEqualTo("project");
    }

    @Test
    void resolveFinalAnswer_conversationPrimaryModelStillWorksWhenInheriting() {
        TaskLlmConfigResolver.FinalAnswerCallConfig call =
                resolver.resolveFinalAnswer(
                        executionContext(Optional.of("conversation-selected-model")),
                        baseConfig("conversation-selected-model"));

        assertThat(call.inheritModel()).isTrue();
        assertThat(call.effectiveModel()).isEqualTo("conversation-selected-model");
        assertThat(call.modelSource()).isEqualTo("conversation_primary");
    }

    @Test
    void fromOperation_mapsFinalAnswerOperations() {
        assertThat(TaskLlmTask.fromOperation("final-answer")).contains(TaskLlmTask.FINAL_ANSWER);
        assertThat(TaskLlmTask.fromOperation("primary-answer")).contains(TaskLlmTask.FINAL_ANSWER);
    }

    @Test
    void settingsCatalogTasks_hidesLlmBaselineEvaluation() {
        assertThat(TaskLlmTask.settingsCatalogTasks()).doesNotContain(TaskLlmTask.LLM_BASELINE_EVALUATION);
        assertThat(TaskLlmTask.settingsCatalogTasks()).contains(TaskLlmTask.FINAL_ANSWER);
    }

    private static ResolvedLlmConfig baseConfig(String chatModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                chatModel,
                "embed-model",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private ExecutionContext executionContext(Optional<String> chatOverride) {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.2, "legacy-model", "emb", "cls", "SIMPLE");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                userId,
                projectId,
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                chatOverride,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
