package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.TaskLlmGenerationParameters;
import com.uniovi.rag.domain.llm.TaskLlmOverride;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaultsSeeder;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Resolves system-level per-role LLM defaults from DB, with seed fallback and WARN logging. */
@Service
public class SystemTaskLlmDefaultsProvider {

    private static final Logger log = LoggerFactory.getLogger(SystemTaskLlmDefaultsProvider.class);

    private final ConfigurationSourcePort configurationSource;
    private volatile boolean warnedMissingDbDefaults;

    public SystemTaskLlmDefaultsProvider(ConfigurationSourcePort configurationSource) {
        this.configurationSource = configurationSource;
    }

    public TaskLlmRoleDefaults.RoleDefault baselineFor(TaskLlmTask task) {
        TaskLlmOverride override = overrideFromDb(task).orElse(null);
        if (override != null && override.isActive()) {
            String model =
                    override.model() != null && !override.model().isBlank()
                            ? override.model().trim()
                            : TaskLlmRoleDefaults.forTask(task).modelId();
            return new TaskLlmRoleDefaults.RoleDefault(model, effectiveParameters(task, override));
        }
        warnMissingDbDefaults(task);
        return TaskLlmRoleDefaults.forTask(task);
    }

    public TaskLlmOverride baselineOverrideFor(TaskLlmTask task) {
        return overrideFromDb(task)
                .orElseGet(
                        () -> {
                            warnMissingDbDefaults(task);
                            return TaskLlmOverride.fromMap(TaskLlmRoleDefaultsSeeder.seedOverrideMap(task));
                        });
    }

    private Optional<TaskLlmOverride> overrideFromDb(TaskLlmTask task) {
        Optional<Map<String, Object>> system = configurationSource.loadSystemDefaults();
        if (system.isEmpty()) {
            return Optional.empty();
        }
        Object nested = system.get().get(PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);
        if (!(nested instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object raw = map.get(task.id());
        if (!(raw instanceof Map<?, ?> roleMap)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        TaskLlmOverride parsed = TaskLlmOverride.fromMap((Map<String, Object>) roleMap);
        return Optional.ofNullable(parsed);
    }

    private static TaskLlmGenerationParameters effectiveParameters(
            TaskLlmTask task, TaskLlmOverride override) {
        if (override.effectiveInheritParameters()) {
            return TaskLlmRoleDefaults.forTask(task).parameters();
        }
        return TaskLlmRoleDefaults.forTask(task).parameters().mergeOverlay(override.parameterOverlay());
    }

    private void warnMissingDbDefaults(TaskLlmTask task) {
        if (warnedMissingDbDefaults) {
            return;
        }
        warnedMissingDbDefaults = true;
        log.warn(
                "System task LLM defaults missing or incomplete in default_system_configuration "
                        + "(role={}); using TaskLlmRoleDefaults seed fallback. "
                        + "Run bootstrap or admin seed to externalize defaults.",
                task.id());
    }
}
