package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmGenerationParameters;
import com.uniovi.rag.domain.llm.TaskLlmOverride;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** Merges per-task LLM overrides with {@link ResolvedLlmConfig}. */
@Service
public class TaskLlmConfigResolver {

    private final ConfigurationSourcePort configurationSource;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final ObjectMapper objectMapper;
    private final RagRuntimeProperties ragRuntimeProperties;
    private final SystemTaskLlmDefaultsProvider systemTaskLlmDefaultsProvider;

    public TaskLlmConfigResolver(
            ConfigurationSourcePort configurationSource,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            ObjectMapper objectMapper,
            RagRuntimeProperties ragRuntimeProperties,
            SystemTaskLlmDefaultsProvider systemTaskLlmDefaultsProvider) {
        this.configurationSource = configurationSource;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.objectMapper = objectMapper;
        this.ragRuntimeProperties = ragRuntimeProperties;
        this.systemTaskLlmDefaultsProvider = systemTaskLlmDefaultsProvider;
    }

    public record EffectiveTaskLlmConfig(
            ResolvedLlmConfig base,
            TaskLlmTask task,
            TaskLlmOverride override,
            String effectiveModel,
            Double effectiveTemperature,
            TaskLlmGenerationParameters effectiveParameters,
            ResolvedLlmConfig mergedConfig,
            boolean taskOverrideApplied) {}

    public record SecondaryCallConfig(
            ResolvedLlmConfig effectiveConfig,
            String effectiveModel,
            Double effectiveTemperature,
            boolean taskOverrideApplied,
            boolean secondaryModelApplied) {

        public SecondaryCallConfig(
                ResolvedLlmConfig effectiveConfig,
                String effectiveModel,
                Double effectiveTemperature,
                boolean taskOverrideApplied) {
            this(effectiveConfig, effectiveModel, effectiveTemperature, taskOverrideApplied, false);
        }
    }

    public record FinalAnswerCallConfig(
            ResolvedLlmConfig effectiveConfig,
            String effectiveModel,
            Double effectiveTemperature,
            boolean inheritModel,
            boolean inheritParameters,
            boolean taskOverrideApplied,
            String modelSource,
            String paramSource) {}

    public EffectiveTaskLlmConfig resolve(
            TaskLlmTask task, UUID userId, UUID projectId, JsonNode runtimeOverride) {
        ResolvedLlmConfig base = resolvedLlmConfigResolver.resolve(userId, projectId, runtimeOverride);
        return resolveWithBase(task, base, userId, projectId, null, runtimeOverride);
    }

    /**
     * Resolves a task role against an already-orchestrated base config (e.g. {@link
     * com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope}).
     */
    public EffectiveTaskLlmConfig resolveWithBase(
            TaskLlmTask task,
            ResolvedLlmConfig base,
            UUID userId,
            UUID projectId,
            @Nullable UUID presetId,
            @Nullable JsonNode requestRuntimeOverride) {
        TaskLlmOverride override =
                mergedOverride(task, userId, projectId, presetId, null, requestRuntimeOverride);
        String model = resolveEffectiveModel(base, task, override);
        TaskLlmGenerationParameters parameters = resolveEffectiveParameters(base, task, override);
        Double temperature = parameters.temperature();
        ResolvedLlmConfig merged = mergeTaskOverride(base, override, model, parameters);
        boolean applied = override != null && override.isActive() && override.hasActiveFields();
        return new EffectiveTaskLlmConfig(base, task, override, model, temperature, parameters, merged, applied);
    }

    /** Primary chat final-answer resolution using orchestration-bound base LLM config. */
    public FinalAnswerCallConfig resolveFinalAnswer(ExecutionContext ctx, ResolvedLlmConfig orchestrationBase) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(orchestrationBase, "orchestrationBase");
        UUID userId = ctx.userId();
        UUID projectId = ctx.projectId();
        UUID presetId = presetId(ctx);
        EffectiveTaskLlmConfig effective =
                resolveWithBase(TaskLlmTask.FINAL_ANSWER, orchestrationBase, userId, projectId, presetId, null);
        TaskLlmOverride override = effective.override();
        boolean inheritModel =
                override != null
                        ? override.effectiveInheritModel(TaskLlmTask.FINAL_ANSWER)
                        : TaskLlmTask.FINAL_ANSWER.inheritsMainModelByDefault();
        boolean inheritParameters = override != null && override.effectiveInheritParameters();
        String modelSource = resolveFinalAnswerModelSource(ctx, orchestrationBase, override, inheritModel);
        String paramSource = inheritParameters ? "primary_inherited" : "final_answer_role";
        return new FinalAnswerCallConfig(
                effective.mergedConfig(),
                effective.effectiveModel(),
                effective.effectiveTemperature(),
                inheritModel,
                inheritParameters,
                effective.taskOverrideApplied(),
                modelSource,
                paramSource);
    }

    public SecondaryCallConfig resolveSecondaryCall(
            ExecutionContext ctx,
            String operation,
            @Nullable Double temperatureOverride,
            @Nullable String chatModelFromSelector) {
        UUID userId = ctx != null ? ctx.userId() : null;
        UUID projectId = ctx != null ? ctx.projectId() : null;
        JsonNode runtimeOverride = buildRuntimeOverride(ctx, chatModelFromSelector);
        return resolveSecondaryCall(userId, projectId, operation, temperatureOverride, runtimeOverride);
    }

    public SecondaryCallConfig resolveSecondaryCall(
            UUID userId,
            UUID projectId,
            String operation,
            @Nullable Double temperatureOverride,
            @Nullable JsonNode runtimeOverride) {
        ResolvedLlmConfig base = resolvedLlmConfigResolver.resolve(userId, projectId, runtimeOverride);
        Optional<TaskLlmTask> taskOpt = TaskLlmTask.fromOperation(operation);
        if (taskOpt.isEmpty()) {
            Double temp = coalesceTemperature(base.temperature(), temperatureOverride);
            String model = resolveRuntimeSecondaryModel(base);
            boolean secondaryApplied = ragRuntimeProperties.hasSecondaryModel();
            if (secondaryApplied) {
                model = ragRuntimeProperties.effectiveSecondaryModel();
            }
            ResolvedLlmConfig merged = secondaryApplied ? mergeTaskOverride(base, null, model, TaskLlmGenerationParameters.fromMap(Map.of("temperature", temp))) : base;
            return new SecondaryCallConfig(merged, model, temp, false, secondaryApplied);
        }
        EffectiveTaskLlmConfig effective = resolve(taskOpt.get(), userId, projectId, runtimeOverride);
        Double temp = effective.effectiveTemperature();
        if (temperatureOverride != null
                && (effective.override() == null || effective.override().temperature() == null)) {
            temp = temperatureOverride;
        }
        String model = effective.effectiveModel();
        boolean secondaryApplied = false;
        if (!effective.taskOverrideApplied()
                && !effective.task().inheritsMainModelByDefault()
                && ragRuntimeProperties.hasSecondaryModel()) {
            model = ragRuntimeProperties.effectiveSecondaryModel();
            secondaryApplied = true;
        }
        ResolvedLlmConfig mergedConfig =
                secondaryApplied
                        ? mergeTaskOverride(
                                effective.mergedConfig(),
                                null,
                                model,
                                effective.effectiveParameters().mergeOverlay(
                                        new TaskLlmGenerationParameters(temp, null, null, null, null, null, null, List.of(), null, null)))
                        : effective.mergedConfig();
        if (temperatureOverride != null
                && (effective.override() == null || effective.override().temperature() == null)) {
            mergedConfig =
                    mergeTaskOverride(
                            mergedConfig,
                            null,
                            model,
                            effective.effectiveParameters().mergeOverlay(
                                    new TaskLlmGenerationParameters(temp, null, null, null, null, null, null, List.of(), null, null)));
        }
        return new SecondaryCallConfig(
                mergedConfig, model, temp, effective.taskOverrideApplied(), secondaryApplied);
    }

    private String resolveRuntimeSecondaryModel(ResolvedLlmConfig base) {
        return ragRuntimeProperties.hasSecondaryModel()
                ? ragRuntimeProperties.effectiveSecondaryModel()
                : base.chatModel();
    }

    private JsonNode buildRuntimeOverride(ExecutionContext ctx, @Nullable String chatModelFromSelector) {
        if (chatModelFromSelector == null || chatModelFromSelector.isBlank()) {
            return null;
        }
        return objectMapper.createObjectNode().put("llmModel", chatModelFromSelector.trim());
    }

    private static UUID presetId(ExecutionContext ctx) {
        if (ctx.resolved() == null
                || ctx.resolved().provenance() == null
                || ctx.resolved().provenance().presetId() == null) {
            return null;
        }
        return ctx.resolved().provenance().presetId();
    }

    private String resolveFinalAnswerModelSource(
            ExecutionContext ctx,
            ResolvedLlmConfig orchestrationBase,
            @Nullable TaskLlmOverride mergedOverride,
            boolean inheritModel) {
        if (!inheritModel && mergedOverride != null && mergedOverride.model() != null && !mergedOverride.model().isBlank()) {
            return resolveOverrideLayerSource(ctx.userId(), ctx.projectId(), TaskLlmTask.FINAL_ANSWER);
        }
        Optional<String> chatOverride = ctx.chatModelOverride();
        if (chatOverride.isPresent() && !chatOverride.get().isBlank()) {
            return "conversation_primary";
        }
        if (orchestrationBase.chatModel() != null && !orchestrationBase.chatModel().isBlank()) {
            return "primary_inherited";
        }
        return "user_system";
    }

    private String resolveOverrideLayerSource(UUID userId, UUID projectId, TaskLlmTask task) {
        if (userId != null && projectId != null) {
            TaskLlmOverride project =
                    readLayerOverride(configurationSource.loadProject(userId, projectId), task);
            if (project != null && project.isActive() && hasExplicitModel(project, task)) {
                return "project";
            }
        }
        if (userId != null) {
            TaskLlmOverride user = readLayerOverride(configurationSource.loadUserDefault(userId), task);
            if (user != null && user.isActive() && hasExplicitModel(user, task)) {
                return "user";
            }
        }
        TaskLlmOverride system = readLayerOverride(configurationSource.loadSystemDefaults(), task);
        if (system != null && system.isActive() && hasExplicitModel(system, task)) {
            return "user_system";
        }
        return "final_answer_override";
    }

    @Nullable
    private static TaskLlmOverride readLayerOverride(Optional<Map<String, Object>> layer, TaskLlmTask task) {
        if (layer.isEmpty()) {
            return null;
        }
        Object nested = layer.get().get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (!(nested instanceof Map<?, ?> map)) {
            return null;
        }
        Object raw = map.get(task.id());
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = (Map<String, Object>) rawMap;
        return TaskLlmOverride.fromMap(asMap);
    }

    private static boolean hasExplicitModel(TaskLlmOverride override, TaskLlmTask task) {
        return !override.effectiveInheritModel(task)
                && override.model() != null
                && !override.model().isBlank();
    }

    public TaskLlmOverride mergedOverride(
            TaskLlmTask task,
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        Map<String, TaskLlmOverride> merged = new LinkedHashMap<>();
        mergeLayer(merged, configurationSource.loadSystemDefaults());
        if (userId != null) {
            mergeLayer(merged, configurationSource.loadUserDefault(userId));
        }
        if (userId != null && projectId != null) {
            mergeLayer(merged, configurationSource.loadProject(userId, projectId));
        }
        if (userId != null && presetId != null) {
            configurationSource
                    .loadPresetProfileCompositionSources(userId, presetId)
                    .ifPresent(
                            src -> {
                                List<Map<String, Object>> payloads = new ArrayList<>(src.orderedProfilePayloads());
                                mergeLayer(
                                        merged,
                                        Optional.of(PresetProfilePayloadMerge.merge(src.presetValues(), payloads)));
                            });
        }
        mergeRuntimeLayer(merged, conversationRuntimeOverride);
        mergeRuntimeLayer(merged, requestRuntimeOverride);
        return merged.get(task.id());
    }

    private String resolveEffectiveModel(ResolvedLlmConfig base, TaskLlmTask task, @Nullable TaskLlmOverride override) {
        TaskLlmRoleDefaults.RoleDefault systemBaseline = systemTaskLlmDefaultsProvider.baselineFor(task);
        if (override != null && override.isActive()) {
            if (override.effectiveInheritModel(task)) {
                return base.chatModel();
            }
            if (override.model() != null && !override.model().isBlank()) {
                return override.model().trim();
            }
        }
        if (task.inheritsMainModelByDefault()) {
            return base.chatModel();
        }
        return systemBaseline.modelId();
    }

    private TaskLlmGenerationParameters resolveEffectiveParameters(
            ResolvedLlmConfig base, TaskLlmTask task, @Nullable TaskLlmOverride override) {
        TaskLlmRoleDefaults.RoleDefault systemBaseline = systemTaskLlmDefaultsProvider.baselineFor(task);
        if (override != null && override.isActive() && override.effectiveInheritParameters()) {
            return parametersFromBaseConfig(base);
        }
        TaskLlmGenerationParameters effective = systemBaseline.parameters();
        if (override != null && override.isActive()) {
            effective = effective.mergeOverlay(override.parameterOverlay());
        }
        return effective;
    }

    private static TaskLlmGenerationParameters parametersFromBaseConfig(ResolvedLlmConfig base) {
        TaskLlmGenerationParameters fromAdditional = TaskLlmGenerationParameters.fromMap(base.additionalParameters());
        return new TaskLlmGenerationParameters(
                        base.temperature(),
                        fromAdditional.topP(),
                        fromAdditional.seed(),
                        fromAdditional.maxTokens(),
                        fromAdditional.presencePenalty(),
                        fromAdditional.frequencyPenalty(),
                        fromAdditional.responseFormat(),
                        fromAdditional.stopSequences(),
                        fromAdditional.think(),
                        base.timeoutMs() != null ? base.timeoutMs() / 1000 : fromAdditional.timeoutSeconds())
                .mergeOverlay(fromAdditional);
    }

    private static ResolvedLlmConfig mergeTaskOverride(
            ResolvedLlmConfig base,
            @Nullable TaskLlmOverride override,
            String model,
            TaskLlmGenerationParameters parameters) {
        if (parameters == null) {
            return base;
        }
        Map<String, Object> additional = new LinkedHashMap<>(base.additionalParameters());
        additional.putAll(parameters.toAdditionalParameters());
        Integer timeoutMs =
                parameters.timeoutSeconds() != null ? parameters.timeoutSeconds() * 1000 : base.timeoutMs();
        return new ResolvedLlmConfig(
                base.chatProvider(),
                base.embeddingProvider(),
                base.baseUrl(),
                model,
                base.embeddingModel(),
                base.apiKeyEnv(),
                base.secretName(),
                parameters.temperature() != null ? parameters.temperature() : base.temperature(),
                timeoutMs,
                base.systemPrompt(),
                additional);
    }

    private static Double coalesceTemperature(@Nullable Double base, @Nullable Double override) {
        if (override != null) {
            return override;
        }
        if (base != null) {
            return base;
        }
        return ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE;
    }

    @SuppressWarnings("unchecked")
    private static void mergeLayer(Map<String, TaskLlmOverride> target, Optional<Map<String, Object>> layer) {
        if (layer.isEmpty()) {
            return;
        }
        Object nested = layer.get().get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (!(nested instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getKey() instanceof String id)) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> rawMap) {
                TaskLlmOverride parsed = TaskLlmOverride.fromMap((Map<String, Object>) rawMap);
                if (parsed != null) {
                    target.put(id, parsed);
                }
            }
        }
    }

    private void mergeRuntimeLayer(Map<String, TaskLlmOverride> target, JsonNode runtimeOverride) {
        if (runtimeOverride == null || runtimeOverride.isNull()) {
            return;
        }
        mergeLayer(target, Optional.of(objectMapper.convertValue(runtimeOverride, Map.class)));
    }
}
