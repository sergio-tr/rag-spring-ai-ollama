package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.MessageDto;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.application.service.chat.RuntimeOverrideNormalizer;
import com.uniovi.rag.application.service.chat.ConversationRuntimeModelKeys;
import com.uniovi.rag.application.service.chat.ChatRuntimeCompatibilitySupport;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.runtime.ChatSourceMapper;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversation and message persistence for the product REST API (hexagonal application layer).
 */
@Service
public class ConversationApplicationService {

    private final ProjectAccessService projectAccessService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final PresetService presetService;
    private final ChatPresetDefaults chatPresetDefaults;
    private final LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    private final RuntimeConfigValidationService runtimeConfigValidationService;

    public ConversationApplicationService(
            ProjectAccessService projectAccessService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            PresetService presetService,
            ChatPresetDefaults chatPresetDefaults,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService,
            RuntimeConfigValidationService runtimeConfigValidationService) {
        this.projectAccessService = projectAccessService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.presetService = presetService;
        this.chatPresetDefaults = chatPresetDefaults;
        this.experimentalPresetCatalogService = experimentalPresetCatalogService;
        this.runtimeConfigValidationService = runtimeConfigValidationService;
    }

    public List<ConversationDto> listConversations(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return conversationRepository
                .findByProject_IdAndUser_IdOrderByUpdatedAtDesc(projectId, userId)
                .stream()
                .map(this::toConversationDtoBaseline)
                .toList();
    }

    public ConversationDto createConversation(
            UUID userId, UUID projectId, CreateConversationRequest body) {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        String title = body != null && body.title() != null && !body.title().isBlank()
                ? body.title().trim()
                : "New chat";
        List<String> filter =
                resolveAndValidateDocumentFilter(
                        project.getId(),
                        null,
                        body != null ? body.documentFilter() : null);
        ConversationEntity c = ConversationEntity.create(project.getOwner(), project, title, filter);

        if (body != null && body.initialPresetId() != null && !body.initialPresetId().isBlank()) {
            UUID presetId;
            try {
                presetId = UUID.fromString(body.initialPresetId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid initialPresetId");
            }
            RagPresetEntity preset = presetService.requireVisiblePreset(userId, presetId);
            validateExperimentalPresetSupport(preset);
            c.setPreset(preset);
        } else {
            chatPresetDefaults.loadDeterministicDefaultPreset().ifPresent(c::setPreset);
        }

        Map<String, Object> initialOverrides =
                body != null && body.initialRuntimeOverride() != null && !body.initialRuntimeOverride().isEmpty()
                        ? new LinkedHashMap<>(body.initialRuntimeOverride())
                        : Map.of();
        ChatRuntimeCompatibilitySupport.throwIfIndexBoundOverride(initialOverrides);
        initialOverrides = ChatRuntimeCompatibilitySupport.copyWithoutNonRuntimeOverrideKeys(initialOverrides);

        UUID presetUuidForPreview =
                c.getPreset() != null
                        ? c.getPreset().getId()
                        : ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;

        long readyDocs =
                knowledgeDocumentRepository.countByProject_IdAndStatus(projectId, ProjectDocumentStatus.READY);

        RuntimeConfigValidateResponse baseVr =
                runtimeConfigValidationService.validateDraft(userId, projectId, presetUuidForPreview, Map.of());
        ChatRuntimeCompatibilitySupport.throwIfInvalid(baseVr);
        RuntimeOverrideNormalizer.NormalizedOverride normalized =
                RuntimeOverrideNormalizer.normalize(
                        initialOverrides,
                        baseVr.effectiveConfig() != null ? baseVr.effectiveConfig() : Map.of());

        RuntimeConfigValidateResponse vr =
                runtimeConfigValidationService.validateDraft(userId, projectId, presetUuidForPreview, normalized.runtimeOverride());

        ChatRuntimeCompatibilitySupport.throwIfInvalid(vr);
        c.setRuntimeOverride(normalized.runtimeOverride());

        List<RuntimeConfigValidationIssueDto> mergedWarnings = new ArrayList<>(vr.warnings());
        if (vr.requiresReindex() && readyDocs == 0) {
            for (RuntimeConfigValidationIssueDto err : vr.errors()) {
                mergedWarnings.add(
                        new RuntimeConfigValidationIssueDto(
                                err.code(),
                                err.field(),
                                err.message(),
                                "WARNING"));
            }
        }

        c = conversationRepository.save(c);
        Map<String, Object> preview =
                vr.effectiveConfig() != null && !vr.effectiveConfig().isEmpty()
                        ? Map.copyOf(vr.effectiveConfig())
                        : Map.of();
        RuntimeIndexCompatibilityDto idxCompat = vr.indexCompatibility();
        return toConversationDtoWithHints(c, preview, mergedWarnings, idxCompat);
    }

    public ConversationDto patchConversation(
            UUID userId, UUID conversationId, PatchConversationRequest body) {
        ConversationEntity c = projectAccessService.requireConversationForUser(userId, conversationId);
        if (body == null) {
            return toConversationDtoBaseline(c);
        }

        boolean touchesRuntimeConfig =
                Boolean.TRUE.equals(body.clearPreset())
                        || (body.presetId() != null && !body.presetId().isBlank())
                        || body.documentFilter() != null
                        || Boolean.TRUE.equals(body.clearRuntimeOverride())
                        || body.runtimeOverride() != null;

        boolean touchesModels =
                Boolean.TRUE.equals(body.clearLlmModel())
                        || body.llmModel() != null
                        || Boolean.TRUE.equals(body.clearClassifierModelId())
                        || body.classifierModelId() != null;

        // Stage all candidate mutations in-memory before persisting (no partial persistence on validation failure).
        String candidateTitle =
                body.title() != null && !body.title().isBlank() ? body.title().trim() : c.getTitle();
        boolean clearPending = Boolean.TRUE.equals(body.clearPendingClarification());

        String candidateLlmModel = c.getLlmModel();
        if (Boolean.TRUE.equals(body.clearLlmModel())) {
            candidateLlmModel = null;
        } else if (body.llmModel() != null) {
            String t = body.llmModel().trim();
            candidateLlmModel = t.isEmpty() ? null : t;
        }

        String candidateClassifierModelId = c.getClassifierModelId();
        if (Boolean.TRUE.equals(body.clearClassifierModelId())) {
            candidateClassifierModelId = null;
        } else if (body.classifierModelId() != null) {
            String t = body.classifierModelId().trim();
            candidateClassifierModelId = t.isEmpty() ? null : t;
        }

        RagPresetEntity candidatePreset = c.getPreset();
        if (Boolean.TRUE.equals(body.clearPreset())) {
            candidatePreset = null;
        } else if (body.presetId() != null && !body.presetId().isBlank()) {
            UUID presetId;
            try {
                presetId = UUID.fromString(body.presetId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid presetId");
            }
            RagPresetEntity preset = presetService.requireVisiblePreset(userId, presetId);
            validateExperimentalPresetSupport(preset);
            candidatePreset = preset;
        }

        List<String> candidateDocumentFilter = candidateDocumentFilter(c, conversationId, body.documentFilter());

        Map<String, Object> candidateOverrideRaw = candidateRuntimeOverride(c, body);
        if (body.runtimeOverride() != null) {
            ChatRuntimeCompatibilitySupport.throwIfIndexBoundOverride(candidateOverrideRaw);
            candidateOverrideRaw = ChatRuntimeCompatibilitySupport.copyWithoutNonRuntimeOverrideKeys(candidateOverrideRaw);
        }

        if (touchesRuntimeConfig || touchesModels) {
            UUID projectId = c.getProject() != null ? c.getProject().getId() : null;
            if (projectId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversation missing project");
            }

            UUID selectedPresetId = candidatePreset != null ? candidatePreset.getId() : null;
            UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(selectedPresetId);

            // Base effective config without candidate overrides.
            RuntimeConfigValidateResponse baseVr =
                    runtimeConfigValidationService.validate(
                            userId,
                            new RuntimeConfigValidateRequest(
                                    conversationId,
                                    effectivePresetId != null ? effectivePresetId.toString() : null,
                                    null,
                                    Map.of()));
            Map<String, Object> baseEffectiveConfig =
                    baseVr.effectiveConfig() != null ? baseVr.effectiveConfig() : Map.of();

            RuntimeOverrideNormalizer.NormalizedOverride normalized =
                    RuntimeOverrideNormalizer.normalize(candidateOverrideRaw, baseEffectiveConfig);

            RuntimeConfigValidateResponse vr =
                    runtimeConfigValidationService.validate(
                            userId,
                            new RuntimeConfigValidateRequest(
                                    conversationId,
                                    effectivePresetId != null ? effectivePresetId.toString() : null,
                                    null,
                                    validationOverrideWithModels(
                                            normalized.runtimeOverride(),
                                            candidateLlmModel,
                                            candidateClassifierModelId)));

            ChatRuntimeCompatibilitySupport.throwIfInvalid(vr);

            if (touchesRuntimeConfig) {
                // Persist only after validation has passed; runtimeOverride must be diff-only.
                candidateOverrideRaw = normalized.runtimeOverride();
            }
        }

        boolean overrideChanged =
                touchesRuntimeConfig
                        && !Objects.equals(
                                candidateOverrideRaw,
                                c.getRuntimeOverride() != null ? c.getRuntimeOverride() : Map.of());
        boolean modelsChanged =
                touchesModels
                        && (!Objects.equals(candidateLlmModel, c.getLlmModel())
                                || !Objects.equals(candidateClassifierModelId, c.getClassifierModelId()));
        boolean changed =
                !Objects.equals(candidateTitle, c.getTitle())
                        || !Objects.equals(candidatePreset, c.getPreset())
                        || (body.documentFilter() != null
                                && !Objects.equals(candidateDocumentFilter, c.getDocumentFilter()))
                        || overrideChanged
                        || clearPending
                        || modelsChanged;

        if (changed) {
            c.setTitle(candidateTitle);
            c.setPreset(candidatePreset);
            if (body.documentFilter() != null) {
                c.setDocumentFilter(candidateDocumentFilter);
            }
            if (touchesRuntimeConfig) {
                c.setRuntimeOverride(candidateOverrideRaw);
            }
            if (modelsChanged) {
                c.setLlmModel(candidateLlmModel);
                c.setClassifierModelId(candidateClassifierModelId);
            }
            if (clearPending) {
                c.setPendingClarification(null);
            }
            c.touchUpdated();
            conversationRepository.save(c);
        }
        return toConversationDtoBaseline(c);
    }

    private List<String> candidateDocumentFilter(
            ConversationEntity c,
            UUID conversationId,
            List<String> requestedDocumentFilter) {
        if (requestedDocumentFilter != null) {
            return resolveAndValidateDocumentFilter(c.getProject().getId(), conversationId, requestedDocumentFilter);
        }
        return c.getDocumentFilter() != null ? List.copyOf(c.getDocumentFilter()) : List.of();
    }

    private static Map<String, Object> candidateRuntimeOverride(
            ConversationEntity c,
            PatchConversationRequest body) {
        if (Boolean.TRUE.equals(body.clearRuntimeOverride())) {
            return Map.of();
        }
        if (body.runtimeOverride() != null) {
            return new LinkedHashMap<>(body.runtimeOverride());
        }
        Map<String, Object> persisted =
                c.getRuntimeOverride() != null ? new LinkedHashMap<>(c.getRuntimeOverride()) : new LinkedHashMap<>();
        return ChatRuntimeCompatibilitySupport.copyWithoutNonRuntimeOverrideKeys(persisted);
    }

    private static Map<String, Object> validationOverrideWithModels(
            Map<String, Object> runtimeOverride,
            String llmModel,
            String classifierModelId) {
        Map<String, Object> out =
                runtimeOverride != null ? new LinkedHashMap<>(runtimeOverride) : new LinkedHashMap<>();
        if (llmModel != null && !llmModel.isBlank()) {
            out.put(ConversationRuntimeModelKeys.LLM_MODEL, llmModel);
        }
        if (classifierModelId != null && !classifierModelId.isBlank()) {
            out.put(ConversationRuntimeModelKeys.CLASSIFIER_MODEL_ID, classifierModelId);
        }
        return out;
    }

    private void validateExperimentalPresetSupport(RagPresetEntity preset) {
        if (preset == null || preset.getTags() == null) {
            return;
        }
        boolean experimental =
                preset.getTags().stream().anyMatch(t -> t != null && t.trim().equalsIgnoreCase("experimental"));
        if (!experimental) {
            return;
        }
        // Single source of truth: LabExperimentalPresetCatalogService determines supported/chatSelectable/reasons.
        var item =
                experimentalPresetCatalogService.list().stream()
                        .filter(p -> p != null && preset.getId() != null && preset.getId().toString().equals(p.productPresetId()))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Experimental preset not found in catalog"));

        if (!item.chatSelectable()) {
            String reason =
                    item.supportStatus() != null ? item.supportStatus() : "NOT_CHAT_SELECTABLE";
            String msg =
                    item.reasonIfUnsupported() != null && !item.reasonIfUnsupported().isBlank()
                            ? item.reasonIfUnsupported()
                            : reason;
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This experimental preset is not selectable in Chat: " + msg);
        }
    }

    public void deleteConversation(UUID userId, UUID conversationId) {
        if (conversationRepository.findByIdAndUser_Id(conversationId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        conversationRepository.deleteById(conversationId);
    }

    public List<MessageDto> listMessages(UUID userId, UUID conversationId) {
        projectAccessService.requireConversationForUser(userId, conversationId);
        return messageRepository.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId).stream()
                .map(ConversationApplicationService::toMessageDto)
                .toList();
    }

    private ConversationDto toConversationDtoBaseline(ConversationEntity c) {
        return toConversationDtoWithHints(c, Map.of(), List.of(), null);
    }

    private ConversationDto toConversationDtoWithHints(
            ConversationEntity c,
            Map<String, Object> effectiveRuntimePreview,
            List<RuntimeConfigValidationIssueDto> runtimeWarnings,
            RuntimeIndexCompatibilityDto indexCompatibility) {
        UUID presetId = c.getPreset() != null ? c.getPreset().getId() : null;
        List<String> docs = c.getDocumentFilter() != null ? List.copyOf(c.getDocumentFilter()) : List.of();
        Map<String, Object> runtimeOverride =
                ConversationRuntimeModelKeys.copyWithoutModelKeys(
                        c.getRuntimeOverride() != null ? new LinkedHashMap<>(c.getRuntimeOverride()) : new LinkedHashMap<>());
        UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(presetId);
        Map<String, Object> preview =
                effectiveRuntimePreview != null && !effectiveRuntimePreview.isEmpty()
                        ? Map.copyOf(effectiveRuntimePreview)
                        : Map.of();
        List<RuntimeConfigValidationIssueDto> warns =
                runtimeWarnings != null ? List.copyOf(runtimeWarnings) : List.of();
        Map<String, Object> pendingRaw = c.getPendingClarification();
        Map<String, Object> pending =
                pendingRaw != null && !pendingRaw.isEmpty() ? Map.copyOf(pendingRaw) : null;
        return new ConversationDto(
                c.getId(),
                c.getTitle(),
                c.getUpdatedAt(),
                presetId,
                docs,
                runtimeOverride,
                effectivePresetId,
                preview,
                warns,
                indexCompatibility,
                pending,
                c.getLlmModel(),
                c.getClassifierModelId());
    }

    /**
     * Normalizes document ids and ensures each belongs to the project. Empty input yields an empty list (no filter).
     */
    private List<String> resolveAndValidateDocumentFilter(UUID projectId, UUID conversationId, List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            UUID docId;
            try {
                docId = UUID.fromString(s.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document id in documentFilter");
            }
            var hit = knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId);
            if (hit.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "documentFilter contains id not in project: " + docId);
            }
            var doc = hit.get();
            if (doc.getStatus() == null || doc.getStatus() != ProjectDocumentStatus.READY) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "documentFilter contains non-READY document: " + docId);
            }
            if (doc.getCorpusScope() == CorpusScope.CHAT_LOCAL) {
                if (doc.getConversation() == null
                        || doc.getConversation().getId() == null
                        || conversationId == null
                        || !conversationId.equals(doc.getConversation().getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "documentFilter contains CHAT_LOCAL document from another conversation: " + docId);
                }
            }
            normalized.add(docId.toString());
        }
        return List.copyOf(normalized);
    }

    private static MessageDto toMessageDto(MessageEntity m) {
        return new MessageDto(
                m.getId(),
                m.getRole(),
                m.getContent(),
                m.getCreatedAt(),
                ChatSourceMapper.toDtos(ChatSourceMapper.fromLegacyMaps(m.getSources())),
                m.getQueryType(),
                m.getPipelineSteps(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getExecutionMetadata());
    }
}
