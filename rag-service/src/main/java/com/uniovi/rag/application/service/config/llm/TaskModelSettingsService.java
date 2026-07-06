package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.application.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmOverride;
import com.uniovi.rag.domain.llm.TaskLlmRoleSettings;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes effective per-role task model settings. */
@Service
public class TaskModelSettingsService {

    private final TaskLlmConfigResolver taskLlmConfigResolver;
    private final UserProjectConfigurationService userProjectConfigurationService;
    private final AdminSystemDefaultsService adminSystemDefaultsService;

    public TaskModelSettingsService(
            TaskLlmConfigResolver taskLlmConfigResolver,
            UserProjectConfigurationService userProjectConfigurationService,
            AdminSystemDefaultsService adminSystemDefaultsService) {
        this.taskLlmConfigResolver = taskLlmConfigResolver;
        this.userProjectConfigurationService = userProjectConfigurationService;
        this.adminSystemDefaultsService = adminSystemDefaultsService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEffectiveForUser(UUID userId, UUID projectId) {
        return buildResponse(userId, projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemDefaults() {
        return buildResponse(null, null);
    }

    @Transactional
    public Map<String, Object> putUserSettings(UUID userId, List<TaskLlmRoleSettings> roles) {
        Map<String, Object> current = userProjectConfigurationService.getEffectiveUserConfig(userId);
        Map<String, Object> next = withTaskOverrides(current, roles);
        userProjectConfigurationService.putUserConfig(userId, next);
        return buildResponse(userId, null);
    }

    @Transactional
    public Map<String, Object> putProjectSettings(UUID userId, UUID projectId, List<TaskLlmRoleSettings> roles) {
        Map<String, Object> current = userProjectConfigurationService.getEffectiveProjectConfig(userId, projectId);
        Map<String, Object> next = withTaskOverrides(current, roles);
        userProjectConfigurationService.putProjectConfig(userId, projectId, next);
        return buildResponse(userId, projectId);
    }

    @Transactional
    public Map<String, Object> putAdminSystemSettings(List<TaskLlmRoleSettings> roles) {
        Map<String, Object> current = new LinkedHashMap<>(adminSystemDefaultsService.getDefaults());
        Map<String, Object> next = withTaskOverrides(current, roles);
        adminSystemDefaultsService.putDefaults(next);
        return buildResponse(null, null);
    }

    @Transactional
    public Map<String, Object> resetUserRole(UUID userId, TaskLlmTask task) {
        return resetRoleInConfig(userProjectConfigurationService.getEffectiveUserConfig(userId), userId, null, task);
    }

    @Transactional
    public Map<String, Object> resetProjectRole(UUID userId, UUID projectId, TaskLlmTask task) {
        return resetRoleInConfig(
                userProjectConfigurationService.getEffectiveProjectConfig(userId, projectId),
                userId,
                projectId,
                task);
    }

    @Transactional
    public Map<String, Object> resetUserAll(UUID userId) {
        Map<String, Object> current = new LinkedHashMap<>(userProjectConfigurationService.getEffectiveUserConfig(userId));
        current.remove(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        userProjectConfigurationService.putUserConfig(userId, current);
        return buildResponse(userId, null);
    }

    @Transactional
    public Map<String, Object> resetProjectAll(UUID userId, UUID projectId) {
        Map<String, Object> current =
                new LinkedHashMap<>(userProjectConfigurationService.getEffectiveProjectConfig(userId, projectId));
        current.remove(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        userProjectConfigurationService.putProjectConfig(userId, projectId, current);
        return buildResponse(userId, projectId);
    }

    @Transactional
    public Map<String, Object> resetAdminRole(TaskLlmTask task) {
        Map<String, Object> current = new LinkedHashMap<>(adminSystemDefaultsService.getDefaults());
        removeRoleOverride(current, task);
        adminSystemDefaultsService.putDefaults(current);
        return buildResponse(null, null);
    }

    @Transactional
    public Map<String, Object> resetAdminAll() {
        Map<String, Object> current = new LinkedHashMap<>(adminSystemDefaultsService.getDefaults());
        current.remove(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        adminSystemDefaultsService.putDefaults(current);
        return buildResponse(null, null);
    }

    private Map<String, Object> resetRoleInConfig(
            Map<String, Object> current, UUID userId, UUID projectId, TaskLlmTask task) {
        Map<String, Object> next = new LinkedHashMap<>(current);
        removeRoleOverride(next, task);
        if (projectId == null) {
            userProjectConfigurationService.putUserConfig(userId, next);
        } else {
            userProjectConfigurationService.putProjectConfig(userId, projectId, next);
        }
        return buildResponse(userId, projectId);
    }

    private static void removeRoleOverride(Map<String, Object> config, TaskLlmTask task) {
        Object nested = config.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (!(nested instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() instanceof String key && e.getValue() != null) {
                overrides.put(key, e.getValue());
            }
        }
        overrides.remove(task.id());
        if (overrides.isEmpty()) {
            config.remove(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        } else {
            config.put(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY, overrides);
        }
    }

    private static Map<String, Object> withTaskOverrides(
            Map<String, Object> current, List<TaskLlmRoleSettings> roles) {
        Map<String, Object> next = new LinkedHashMap<>(current);
        Map<String, Object> overrides = new LinkedHashMap<>();
        Object existing = current.get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (existing instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() instanceof String key && e.getValue() != null) {
                    overrides.put(key, e.getValue());
                }
            }
        }
        for (TaskLlmRoleSettings role : roles) {
            TaskLlmTask task = TaskLlmTask.valueOf(role.role());
            TaskLlmOverride override = role.toOverride();
            if (override != null && override.hasActiveFields()) {
                overrides.put(task.id(), override.toMap());
            } else {
                overrides.remove(task.id());
            }
        }
        if (overrides.isEmpty()) {
            next.remove(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        } else {
            next.put(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY, overrides);
        }
        return next;
    }

    private Map<String, Object> buildResponse(UUID userId, UUID projectId) {
        List<Map<String, Object>> roles = new ArrayList<>();
        for (TaskLlmTask task : TaskLlmTask.settingsCatalogTasks()) {
            roles.add(toEffectiveSettings(task, userId, projectId).toResponseMap());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("version", 2);
        response.put("roles", List.copyOf(roles));
        response.put("overridesMapKey", PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        return Map.copyOf(response);
    }

    private TaskLlmRoleSettings toEffectiveSettings(TaskLlmTask task, UUID userId, UUID projectId) {
        var effective = taskLlmConfigResolver.resolve(task, userId, projectId, null);
        TaskLlmOverride override = effective.override();
        boolean inheritModel = override != null ? override.effectiveInheritModel(task) : task.inheritsMainModelByDefault();
        boolean inheritParameters = override != null && override.effectiveInheritParameters();
        return TaskLlmRoleSettings.effective(
                task,
                effective.effectiveModel(),
                effective.effectiveParameters(),
                inheritModel,
                inheritParameters,
                override);
    }

    public static TaskLlmRoleSettings displayDefault(TaskLlmTask task) {
        return TaskLlmRoleSettings.displayDefault(task);
    }
}
