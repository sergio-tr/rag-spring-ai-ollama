package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.config.ConfigurationSchemaProvider;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config schema and user/project effective configuration (PLAN_EXCELENCIA §4).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/config")
public class ConfigController {

    private final ConfigurationSchemaProvider configurationSchemaProvider;
    private final UserProjectConfigurationService userProjectConfigurationService;

    public ConfigController(
            ConfigurationSchemaProvider configurationSchemaProvider,
            UserProjectConfigurationService userProjectConfigurationService) {
        this.configurationSchemaProvider = configurationSchemaProvider;
        this.userProjectConfigurationService = userProjectConfigurationService;
    }

    @GetMapping("/schema")
    public Map<String, Object> schema() {
        return configurationSchemaProvider.buildSchema();
    }

    @GetMapping("/user")
    public Map<String, Object> getUserConfig(@AuthenticationPrincipal RagPrincipal principal) {
        return userProjectConfigurationService.getEffectiveUserConfig(principal.userId());
    }

    @PutMapping("/user")
    public Map<String, Object> putUserConfig(
            @AuthenticationPrincipal RagPrincipal principal, @RequestBody Map<String, Object> body) {
        return userProjectConfigurationService.putUserConfig(principal.userId(), body);
    }

    @GetMapping("/project/{projectId}")
    public Map<String, Object> getProjectConfig(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return userProjectConfigurationService.getEffectiveProjectConfig(principal.userId(), projectId);
    }

    @PutMapping("/project/{projectId}")
    public Map<String, Object> putProjectConfig(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return userProjectConfigurationService.putProjectConfig(principal.userId(), projectId, body);
    }

    @DeleteMapping("/project/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProjectConfig(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        userProjectConfigurationService.deleteProjectConfig(principal.userId(), projectId);
    }
}
