package com.uniovi.rag.application.service.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
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
    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public RuntimeConfigValidationService(
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper,
            ConfigResolverService configResolverService,
            WorkflowSelector workflowSelector,
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector,
            KnowledgeSnapshotService knowledgeSnapshotService) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.configResolverService = configResolverService;
        this.workflowSelector = workflowSelector;
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
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
        return validateForProject(
                userId, projectId, req.conversationId(), presetId, req.overrides(), "runtime_config_validate");
    }

    /**
     * Validates runtime configuration for a conversation that does not exist yet (preset + overrides draft).
     *
     * @param presetId explicit preset for preview; when {@code null}, callers should pass deterministic default id for UX parity.
     */
    public RuntimeConfigValidateResponse validateDraft(
            UUID userId, UUID projectId, UUID presetId, Map<String, Object> overrides) {
        Optional<UUID> presetOpt = presetId != null ? Optional.of(presetId) : Optional.empty();
        return validateForProject(
                userId, projectId, null, presetOpt, overrides != null ? overrides : Map.of(), "runtime_config_validate_draft");
    }

    private RuntimeConfigValidateResponse validateForProject(
            UUID userId,
            UUID projectId,
            UUID conversationIdForSnapshot,
            Optional<UUID> presetId,
            Map<String, Object> overrides,
            String correlationId) {
        JsonNode overrideNode =
                overrides != null && !overrides.isEmpty()
                        ? objectMapper.convertValue(overrides, JsonNode.class)
                        : null;

        ResolvedRuntimeConfig resolved =
                configResolverService.preview(
                        new RuntimeConfigResolutionInput(
                                userId,
                                projectId,
                                conversationIdForSnapshot != null ? Optional.of(conversationIdForSnapshot) : Optional.empty(),
                                presetId,
                                Optional.empty(),
                                Optional.ofNullable(overrideNode),
                                Set.of(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.ofNullable(correlationId)));

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
        RuntimeIndexCompatibilityDto indexCompatibility =
                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false);
        boolean requiresReindex = false;
        if (resolved.toRagConfig() != null) {
            effectiveConfig = objectMapper.convertValue(resolved.toRagConfig(), Map.class);
        }

        var selection = knowledgeRuntimeSnapshotSelector.select(projectId, conversationIdForSnapshot);
        KnowledgeIndexSnapshotEntity projectSnap =
                knowledgeSnapshotService.findActiveProjectSnapshot(projectId).orElse(null);
        KnowledgeIndexSnapshotEntity chatSnap =
                conversationIdForSnapshot == null
                        ? null
                        : knowledgeSnapshotService
                                .findActiveConversationSnapshot(conversationIdForSnapshot)
                                .orElse(null);
        KnowledgeIndexSnapshotEntity active = chatSnap != null ? chatSnap : projectSnap;
        boolean hasActiveIndex = active != null && active.getId() != null;
        Map<String, Object> activeProfile = hasActiveIndex && active.getIndexProfileJsonb() != null ? active.getIndexProfileJsonb() : Map.of();
        String activeProfileHash = hasActiveIndex ? active.getIndexProfileHash() : null;
        indexCompatibility =
                new RuntimeIndexCompatibilityDto(
                        selection.projectSharedSnapshotId().orElse(null),
                        selection.chatLocalSnapshotId().orElse(null),
                        activeProfileHash,
                        activeProfile,
                        hasActiveIndex);

        RagConfig rag = resolved.toRagConfig();
        if (rag != null) {
            if (rag.toolsEnabled() && rag.functionCallingEnabled()) {
                warnings.add(
                        new RuntimeConfigValidationIssueDto(
                                "TOOLS_FUNCTION_CALLING_PRECEDENCE",
                                null,
                                "Tools and function calling are both enabled. Function calling takes precedence over deterministic tools.",
                                "WARNING"));
            }
            if (hasActiveIndex) {
                String idxStrategy = stringOrNull(activeProfile.get("materializationStrategy"));
                Boolean idxSupportsMetadata = boolOrNull(activeProfile.get("supportsMetadata"));
                boolean strategyMismatch =
                        idxStrategy != null && rag.materializationStrategy() != null
                                && !idxStrategy.equalsIgnoreCase(rag.materializationStrategy().name());
                boolean metadataMismatch =
                        idxSupportsMetadata != null && rag.metadataEnabled() && !idxSupportsMetadata;

                if (strategyMismatch || metadataMismatch) {
                    requiresReindex = true;
                    valid = false;
                    supported = false;
                    if (strategyMismatch) {
                        errors.add(
                                new RuntimeConfigValidationIssueDto(
                                        "INDEX_REQUIRES_REINDEX",
                                        "materializationStrategy",
                                        "Selected runtime materializationStrategy is incompatible with the active index snapshot. Reindex is required.",
                                        "ERROR"));
                    }
                    if (metadataMismatch) {
                        errors.add(
                                new RuntimeConfigValidationIssueDto(
                                        "INDEX_REQUIRES_REINDEX",
                                        "metadataEnabled",
                                        "Selected runtime metadata-aware behavior requires an index snapshot that supports metadata. Reindex is required.",
                                        "ERROR"));
                    }
                }
            } else {
                warnings.add(
                        new RuntimeConfigValidationIssueDto(
                                "NO_ACTIVE_INDEX",
                                null,
                                "No active index snapshot yet. Index/project capabilities will apply when documents are indexed.",
                                "WARNING"));
            }
        }

        return new RuntimeConfigValidateResponse(
                valid, supported, effectiveConfig, errors, warnings, selectedWorkflow, indexCompatibility, requiresReindex);
    }

    private static String stringOrNull(Object o) {
        return o instanceof String s ? s : null;
    }

    private static Boolean boolOrNull(Object o) {
        return o instanceof Boolean b ? b : null;
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

