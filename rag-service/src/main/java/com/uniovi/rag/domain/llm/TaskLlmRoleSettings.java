package com.uniovi.rag.domain.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Effective task model settings for one role. */
public record TaskLlmRoleSettings(
        String role,
        String roleId,
        String label,
        boolean inheritModel,
        String modelId,
        boolean inheritParameters,
        Map<String, Object> parameters,
        boolean hasOverride,
        boolean usesSystemDefaults) {

    public static TaskLlmRoleSettings effective(
            TaskLlmTask task,
            String effectiveModel,
            TaskLlmGenerationParameters effectiveParameters,
            boolean inheritModel,
            boolean inheritParameters,
            TaskLlmOverride override) {
        boolean hasOverride = override != null && override.hasActiveFields();
        return new TaskLlmRoleSettings(
                task.name(),
                task.id(),
                task.label(),
                inheritModel,
                effectiveModel,
                inheritParameters,
                effectiveParameters.toMap(),
                hasOverride,
                !hasOverride);
    }

    public static TaskLlmRoleSettings systemDefault(TaskLlmTask task) {
        TaskLlmRoleDefaults.RoleDefault defaults = TaskLlmRoleDefaults.forTask(task);
        boolean inheritModel = task.inheritsMainModelByDefault();
        return new TaskLlmRoleSettings(
                task.name(),
                task.id(),
                task.label(),
                inheritModel,
                defaults.modelId(),
                false,
                defaults.parameters().toMap(),
                false,
                true);
    }

    public static TaskLlmRoleSettings displayDefault(TaskLlmTask task) {
        TaskLlmRoleDefaults.RoleDefault defaults = TaskLlmRoleDefaults.forTask(task);
        return effective(
                task,
                defaults.modelId(),
                defaults.parameters(),
                task.inheritsMainModelByDefault(),
                false,
                null);
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("roleId", roleId);
        m.put("label", label);
        m.put("inheritModel", inheritModel);
        m.put("modelId", modelId);
        m.put("inheritParameters", inheritParameters);
        m.put("parameters", parameters);
        m.put("hasOverride", hasOverride);
        m.put("usesSystemDefaults", usesSystemDefaults);
        return Map.copyOf(m);
    }

    public static TaskLlmRoleSettings fromRequestMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("role settings required");
        }
        String roleName = readString(raw.get("role"));
        String roleId = readString(firstPresent(raw, "roleId", "id"));
        TaskLlmTask task =
                roleName != null
                        ? TaskLlmTask.valueOf(roleName.trim())
                        : TaskLlmTask.fromId(roleId != null ? roleId : "");
        Map<String, Object> paramsRaw =
                raw.get("parameters") instanceof Map<?, ?> pm ? (Map<String, Object>) pm : Map.of();
        Map<String, Object> overrideRaw = new LinkedHashMap<>();
        overrideRaw.put("enabled", true);
        overrideRaw.put("inheritModel", raw.get("inheritModel"));
        overrideRaw.put("model", firstPresent(raw, "modelId", "model"));
        overrideRaw.put("inheritParameters", raw.get("inheritParameters"));
        overrideRaw.put("temperature", paramsRaw.get("temperature"));
        overrideRaw.put("topP", paramsRaw.get("topP"));
        overrideRaw.put("seed", paramsRaw.get("seed"));
        overrideRaw.put("maxTokens", paramsRaw.get("maxTokens"));
        overrideRaw.put("presencePenalty", paramsRaw.get("presencePenalty"));
        overrideRaw.put("frequencyPenalty", paramsRaw.get("frequencyPenalty"));
        overrideRaw.put("responseFormat", paramsRaw.get("responseFormat"));
        overrideRaw.put("stopSequences", paramsRaw.get("stopSequences"));
        overrideRaw.put("think", paramsRaw.get("think"));
        overrideRaw.put("timeoutSeconds", paramsRaw.get("timeoutSeconds"));
        TaskLlmOverride override = TaskLlmOverride.fromMap(overrideRaw);
        TaskLlmRoleDefaults.RoleDefault defaults = TaskLlmRoleDefaults.forTask(task);
        boolean inheritModel =
                override != null ? override.effectiveInheritModel(task) : task.inheritsMainModelByDefault();
        boolean inheritParameters = override != null && override.effectiveInheritParameters();
        String model =
                inheritModel
                        ? defaults.modelId()
                        : (override != null && override.model() != null ? override.model() : defaults.modelId());
        TaskLlmGenerationParameters params =
                inheritParameters
                        ? defaults.parameters()
                        : defaults.parameters().mergeOverlay(override != null ? override.parameterOverlay() : TaskLlmGenerationParameters.empty());
        return effective(task, model, params, inheritModel, inheritParameters, override);
    }

    @SuppressWarnings("unchecked")
    public TaskLlmOverride toOverride() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("enabled", true);
        raw.put("inheritModel", inheritModel);
        if (!inheritModel && modelId != null && !modelId.isBlank()) {
            raw.put("model", modelId.trim());
        }
        raw.put("inheritParameters", inheritParameters);
        if (!inheritParameters && parameters != null) {
            raw.putAll(parameters);
            if (parameters.get("stopSequences") instanceof List<?> stops) {
                raw.put("stopSequences", stops);
            }
        }
        return TaskLlmOverride.fromMap(raw);
    }

    private static Object firstPresent(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return null;
    }

    private static String readString(Object raw) {
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
