package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.interfaces.rest.dto.TaskModelRoleSettingsDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Effective per-role task model settings for user and project configuration. */
@RestController
@RequestMapping("${rag.api.product-base-path}/config/task-model-settings")
public class TaskModelSettingsController {

    private final TaskModelSettingsService taskModelSettingsService;

    public TaskModelSettingsController(TaskModelSettingsService taskModelSettingsService) {
        this.taskModelSettingsService = taskModelSettingsService;
    }

    @GetMapping
    @Operation(summary = "Effective task model settings for all roles")
    public Map<String, Object> getEffective(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(required = false) UUID projectId) {
        return taskModelSettingsService.getEffectiveForUser(principal.userId(), projectId);
    }

    @PutMapping
    @Operation(summary = "Save task model role overrides")
    public Map<String, Object> putSettings(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(required = false) UUID projectId,
            @RequestBody Map<String, Object> body) {
        List<TaskModelRoleSettingsDto> roles = parseRoles(body);
        if (projectId == null) {
            return taskModelSettingsService.putUserSettings(
                    principal.userId(), roles.stream().map(TaskModelRoleSettingsDto::toDomain).toList());
        }
        return taskModelSettingsService.putProjectSettings(
                principal.userId(), projectId, roles.stream().map(TaskModelRoleSettingsDto::toDomain).toList());
    }

    @PostMapping("/{role}/reset")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset task model overrides for one role")
    public Map<String, Object> resetRole(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable String role,
            @RequestParam(required = false) UUID projectId) {
        TaskLlmTask task = parseRole(role);
        if (projectId == null) {
            return taskModelSettingsService.resetUserRole(principal.userId(), task);
        }
        return taskModelSettingsService.resetProjectRole(principal.userId(), projectId, task);
    }

    @PostMapping("/reset-all")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset all task model overrides")
    public Map<String, Object> resetAll(
            @AuthenticationPrincipal RagPrincipal principal, @RequestParam(required = false) UUID projectId) {
        if (projectId == null) {
            return taskModelSettingsService.resetUserAll(principal.userId());
        }
        return taskModelSettingsService.resetProjectAll(principal.userId(), projectId);
    }

    @SuppressWarnings("unchecked")
    private static List<TaskModelRoleSettingsDto> parseRoles(Map<String, Object> body) {
        if (body == null || !(body.get("roles") instanceof List<?> rawList)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles array required");
        }
        return rawList.stream()
                .filter(Map.class::isInstance)
                .map(m -> TaskModelRoleSettingsDto.fromRequestMap((Map<String, Object>) m))
                .toList();
    }

    private static TaskLlmTask parseRole(String role) {
        try {
            return TaskLlmTask.valueOf(role.trim());
        } catch (IllegalArgumentException e) {
            return TaskLlmTask.fromId(role);
        }
    }
}
