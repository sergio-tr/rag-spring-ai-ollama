package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurationSchemaProvider;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.interfaces.rest.dto.ResolvedRuntimeConfigResponseDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigPreviewRequest;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Config schema and user/project effective configuration (PLAN_EXCELENCIA §4).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/config")
public class ConfigController {

    private final ConfigurationSchemaProvider configurationSchemaProvider;
    private final UserProjectConfigurationService userProjectConfigurationService;
    private final RuntimeConfigResolutionService runtimeConfigResolutionService;
    private final ObjectMapper objectMapper;

    public ConfigController(
            ConfigurationSchemaProvider configurationSchemaProvider,
            UserProjectConfigurationService userProjectConfigurationService,
            RuntimeConfigResolutionService runtimeConfigResolutionService,
            ObjectMapper objectMapper) {
        this.configurationSchemaProvider = configurationSchemaProvider;
        this.userProjectConfigurationService = userProjectConfigurationService;
        this.runtimeConfigResolutionService = runtimeConfigResolutionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/schema")
    public Map<String, Object> schema() {
        return configurationSchemaProvider.buildSchema();
    }

    /**
     * Dry-run resolved runtime configuration and optional reindex signals (no persistence).
     */
    @PostMapping("/preview")
    public ResolvedRuntimeConfigResponseDto preview(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody RuntimeConfigPreviewRequest body) {
        JsonNode override =
                body.runtimeOverride() == null || body.runtimeOverride().isEmpty()
                        ? null
                        : objectMapper.valueToTree(body.runtimeOverride());
        Set<ConfigProfileType> touched = parseTouchedProfileTypes(body.touchedProfileTypes());
        CapabilitySet baseline =
                body.baselineCapabilitySnapshot() != null
                        ? body.baselineCapabilitySnapshot().toCapabilitySet()
                        : null;
        return ResolvedRuntimeConfigResponseDto.fromDomain(
                runtimeConfigResolutionService.preview(
                        principal.userId(), body.projectId(), override, touched, baseline));
    }

    private static Set<ConfigProfileType> parseTouchedProfileTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        EnumSet<ConfigProfileType> set = EnumSet.noneOf(ConfigProfileType.class);
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            set.add(ConfigProfileType.valueOf(s.trim()));
        }
        return set;
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
