package com.uniovi.rag.application.service.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RuntimeConfigValidationService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final ConfigResolverService configResolverService;
    private final WorkflowSelector workflowSelector;

    public RuntimeConfigValidationService(
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper,
            ConfigResolverService configResolverService,
            WorkflowSelector workflowSelector) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.configResolverService = configResolverService;
        this.workflowSelector = workflowSelector;
    }

    public RuntimeConfigValidateResponse validate(UUID userId, RuntimeConfigValidateRequest req) {
        if (req == null || req.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }
        ConversationEntity conv =
                conversationRepository
                        .findByIdWithConfigAndPreset(req.conversationId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found"));
        if (conv.getUser() == null || conv.getUser().getId() == null || !conv.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
        }
        UUID projectId = conv.getProject() != null ? conv.getProject().getId() : null;
        if (projectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversation missing project");
        }

        Optional<UUID> presetId = parseUuidOptional(req.presetId());
        JsonNode overrideNode = req.overrides() != null && !req.overrides().isEmpty()
                ? objectMapper.convertValue(req.overrides(), JsonNode.class)
                : null;

        ResolvedRuntimeConfig resolved =
                configResolverService.preview(
                        new RuntimeConfigResolutionInput(
                                userId,
                                projectId,
                                Optional.of(req.conversationId()),
                                presetId,
                                Optional.empty(),
                                Optional.ofNullable(overrideNode),
                                Set.of(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of("runtime_config_validate")));

        List<RuntimeConfigValidationIssueDto> errors = new ArrayList<>();
        List<RuntimeConfigValidationIssueDto> warnings = new ArrayList<>();

        boolean valid = resolved.compatibility() == null || resolved.compatibility().valid();
        if (!valid && resolved.compatibility() != null && resolved.compatibility().errors() != null) {
            resolved.compatibility().errors().forEach(e -> errors.add(
                    new RuntimeConfigValidationIssueDto(
                            e.code() != null ? e.code() : "INCOMPATIBLE_CONFIGURATION",
                            e.message(),
                            null,
                            "ERROR")));
        }

        String selectedWorkflow = null;
        boolean supported = true;
        try {
            selectedWorkflow = workflowSelector.selectFromResolved(resolved).workflowName();
        } catch (RagServiceException ex) {
            supported = false;
            valid = false;
            errors.add(
                    new RuntimeConfigValidationIssueDto(
                            "UNSUPPORTED_RUNTIME_CONFIGURATION",
                            null,
                            ex.getMessage(),
                            "ERROR"));
        } catch (RuntimeException ex) {
            supported = false;
            valid = false;
            errors.add(
                    new RuntimeConfigValidationIssueDto(
                            "RUNTIME_CONFIG_VALIDATION_ERROR",
                            null,
                            ex.getMessage(),
                            "ERROR"));
        }

        Map<String, Object> effectiveConfig = new LinkedHashMap<>();
        if (resolved.toRagConfig() != null) {
            // UX-oriented: expose the resolved config as a JSON map (stable keys match rag_preset.values).
            effectiveConfig = objectMapper.convertValue(resolved.toRagConfig(), Map.class);
        }

        return new RuntimeConfigValidateResponse(valid, supported, effectiveConfig, errors, warnings, selectedWorkflow);
    }

    private static Optional<UUID> parseUuidOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid presetId");
        }
    }
}

