package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.llm.TaskLlmGenerationParameters;
import com.uniovi.rag.domain.llm.TaskLlmOverride;
import com.uniovi.rag.domain.llm.TaskLlmRoleSettings;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.Map;

/** REST view of effective task model settings for one role. */
public record TaskModelRoleSettingsDto(
        String role,
        String roleId,
        String label,
        boolean inheritModel,
        String modelId,
        boolean inheritParameters,
        Map<String, Object> parameters,
        boolean hasOverride,
        boolean usesSystemDefaults) {

    public static TaskModelRoleSettingsDto fromDomain(TaskLlmRoleSettings settings) {
        return new TaskModelRoleSettingsDto(
                settings.role(),
                settings.roleId(),
                settings.label(),
                settings.inheritModel(),
                settings.modelId(),
                settings.inheritParameters(),
                settings.parameters(),
                settings.hasOverride(),
                settings.usesSystemDefaults());
    }

    public TaskLlmRoleSettings toDomain() {
        return new TaskLlmRoleSettings(
                role,
                roleId,
                label,
                inheritModel,
                modelId,
                inheritParameters,
                parameters,
                hasOverride,
                usesSystemDefaults);
    }

    public static TaskModelRoleSettingsDto effective(
            TaskLlmTask task,
            String effectiveModel,
            TaskLlmGenerationParameters effectiveParameters,
            boolean inheritModel,
            boolean inheritParameters,
            TaskLlmOverride override) {
        return fromDomain(
                TaskLlmRoleSettings.effective(
                        task, effectiveModel, effectiveParameters, inheritModel, inheritParameters, override));
    }

    public static TaskModelRoleSettingsDto systemDefault(TaskLlmTask task) {
        return fromDomain(TaskLlmRoleSettings.systemDefault(task));
    }

    public Map<String, Object> toResponseMap() {
        return toDomain().toResponseMap();
    }

    public static TaskModelRoleSettingsDto fromRequestMap(Map<String, Object> raw) {
        return fromDomain(TaskLlmRoleSettings.fromRequestMap(raw));
    }

    public TaskLlmOverride toOverride() {
        return toDomain().toOverride();
    }
}
