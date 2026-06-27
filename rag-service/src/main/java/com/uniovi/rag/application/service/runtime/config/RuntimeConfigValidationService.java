package com.uniovi.rag.application.service.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimePresetIndexRequirementsDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.application.service.runtime.config.MaterializationAwareSnapshotResolver;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
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

    private static final String ERROR = "ERROR";
    private static final String WARNING = "WARNING";
    private static final String NO_ACTIVE_INDEX = "NO_ACTIVE_INDEX";
    private static final String INDEX_COMPATIBILITY_FIELD = "indexCompatibility";

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final ConfigResolverService configResolverService;
    private final WorkflowSelector workflowSelector;
    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final MaterializationAwareSnapshotResolver materializationAwareSnapshotResolver;

    public RuntimeConfigValidationService(
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper,
            ConfigResolverService configResolverService,
            WorkflowSelector workflowSelector,
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector,
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            MaterializationAwareSnapshotResolver materializationAwareSnapshotResolver) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.configResolverService = configResolverService;
        this.workflowSelector = workflowSelector;
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.materializationAwareSnapshotResolver = materializationAwareSnapshotResolver;
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
                            ERROR)));
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
                            ERROR));
        } catch (RuntimeException ex) {
            supported = false;
            valid = false;
            errors.add(
                    new RuntimeConfigValidationIssueDto(
                            "RUNTIME_CONFIG_VALIDATION_ERROR",
                            null,
                            ex.getMessage(),
                            ERROR));
        }

        Map<String, Object> effectiveConfig = new LinkedHashMap<>();
        RuntimeIndexCompatibilityDto indexCompatibility =
                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN");
        boolean requiresReindex = false;
        if (resolved.toRagConfig() != null) {
            effectiveConfig = objectMapper.convertValue(resolved.toRagConfig(), Map.class);
        }

        RagConfig rag = resolved.toRagConfig();
        ExperimentalPresetCanonicalCatalog.IndexRequirements presetReq =
                resolveIndexRequirements(presetId, rag);

        var selection =
                knowledgeRuntimeSnapshotSelector.select(
                        projectId, conversationIdForSnapshot, presetReq);
        KnowledgeIndexSnapshotEntity projectSnap =
                projectId == null
                        ? null
                        : materializationAwareSnapshotResolver
                                .resolveProjectSnapshot(projectId, presetReq)
                                .filter(MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot::compatibleWithRequirements)
                                .flatMap(
                                        resolvedSnap ->
                                                knowledgeSnapshotService
                                                        .findProjectSnapshots(projectId)
                                                        .stream()
                                                        .filter(s -> resolvedSnap.snapshotId().equals(s.getId()))
                                                        .findFirst())
                                .orElseGet(
                                        () ->
                                                knowledgeSnapshotService
                                                        .findActiveProjectSnapshot(projectId)
                                                        .orElse(null));
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

        if (rag != null) {
            if (rag.toolsEnabled() && rag.functionCallingEnabled()) {
                warnings.add(
                        new RuntimeConfigValidationIssueDto(
                                "TOOLS_FUNCTION_CALLING_PRECEDENCE",
                                null,
                                "Tools and function calling are both enabled. Function calling takes precedence over deterministic tools.",
                                WARNING));
            }
            IndexSnapshotCapabilities snapCaps = IndexSnapshotCapabilities.fromIndexProfile(activeProfile);

            IndexCompatibilityResult idx =
                    IndexCompatibilityResult.check(presetReq, hasActiveIndex, snapCaps);
            long readyDocs =
                    projectId != null
                            ? knowledgeDocumentRepository.countByProject_IdAndStatus(
                                    projectId,
                                    ProjectDocumentStatus.READY)
                            : 0;

            indexCompatibility =
                    new RuntimeIndexCompatibilityDto(
                            selection.projectSharedSnapshotId().orElse(null),
                            selection.chatLocalSnapshotId().orElse(null),
                            activeProfileHash,
                            activeProfile,
                            hasActiveIndex,
                            new RuntimeSnapshotCapabilitiesDto(
                                    snapCaps.materializationStrategy(),
                                    snapCaps.supportsMetadata(),
                                    snapCaps.embeddingModelId(),
                                    snapCaps.chunkMaxChars(),
                                    snapCaps.chunkOverlap()),
                            new RuntimePresetIndexRequirementsDto(
                                    presetReq.requiredMaterialization() != null ? presetReq.requiredMaterialization().name() : null,
                                    presetReq.requiresMetadataSupport()),
                            idx.compatible(),
                            idx.status());

            if (!idx.compatible()) {
                boolean emptyProjectWithoutSnapshot =
                        !hasActiveIndex
                                && readyDocs == 0
                                && NO_ACTIVE_INDEX.equals(idx.reasonCode());
                if (emptyProjectWithoutSnapshot) {
                    warnings.add(
                            new RuntimeConfigValidationIssueDto(
                                    NO_ACTIVE_INDEX,
                                    INDEX_COMPATIBILITY_FIELD,
                                    "No active index snapshot yet. Upload documents and index/reindex the project before running corpus-grounded presets.",
                                    WARNING));
                } else {
                    requiresReindex = idx.requiresReindex();
                    valid = false;
                    supported = false;
                    errors.add(
                            new RuntimeConfigValidationIssueDto(
                                    idx.reasonCode() != null ? idx.reasonCode() : "INDEX_CAPABILITY_MISMATCH",
                                    INDEX_COMPATIBILITY_FIELD,
                                    idx.message(),
                                    ERROR));
                }
            } else {
                // Snapshot has metadata but the selected preset/runtime does not require it.
                if (hasActiveIndex && Boolean.TRUE.equals(snapCaps.supportsMetadata()) && !presetReq.requiresMetadataSupport()) {
                    warnings.add(
                            new RuntimeConfigValidationIssueDto(
                                    "METADATA_AVAILABLE_NOT_USED",
                                    "metadataEnabled",
                                    "Active index supports metadata, but this preset does not require metadata-aware behavior.",
                                    WARNING));
                }
            }
            if (!hasActiveIndex && presetReq.requiredMaterialization() == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
                warnings.add(
                        new RuntimeConfigValidationIssueDto(
                                NO_ACTIVE_INDEX,
                                null,
                                "No active index snapshot yet. Index/project capabilities will apply when documents are indexed.",
                                WARNING));
            }
        }

        return new RuntimeConfigValidateResponse(
                valid, supported, effectiveConfig, errors, warnings, selectedWorkflow, indexCompatibility, requiresReindex);
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

    private static ExperimentalPresetCanonicalCatalog.IndexRequirements resolveIndexRequirements(
            Optional<UUID> presetIdOpt, RagConfig rag) {
        return MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(presetIdOpt, rag);
    }
}

