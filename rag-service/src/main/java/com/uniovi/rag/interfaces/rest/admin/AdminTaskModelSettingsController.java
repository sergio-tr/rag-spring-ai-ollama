package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.interfaces.rest.dto.TaskModelRoleSettingsDto;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin system-default task model settings. */
@RestController
@RequestMapping("${rag.api.product-base-path}/admin/task-model-settings")
public class AdminTaskModelSettingsController {

    private final TaskModelSettingsService taskModelSettingsService;

    public AdminTaskModelSettingsController(TaskModelSettingsService taskModelSettingsService) {
        this.taskModelSettingsService = taskModelSettingsService;
    }

    @GetMapping
    @Operation(summary = "System task model settings with effective defaults")
    public Map<String, Object> getSettings() {
        return taskModelSettingsService.getSystemDefaults();
    }

    @PutMapping
    @Operation(summary = "Save system task model role overrides")
    public Map<String, Object> putSettings(@RequestBody Map<String, Object> body) {
        return taskModelSettingsService.putAdminSystemSettings(
                parseRoles(body).stream().map(TaskModelRoleSettingsDto::toDomain).toList());
    }

    @PostMapping("/{role}/reset")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset system task model overrides for one role")
    public Map<String, Object> resetRole(@PathVariable String role) {
        return taskModelSettingsService.resetAdminRole(parseRole(role));
    }

    @PostMapping("/reset-all")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset all system task model overrides")
    public Map<String, Object> resetAll() {
        return taskModelSettingsService.resetAdminAll();
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
