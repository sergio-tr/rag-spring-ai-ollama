package com.uniovi.rag.application.service;

import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.MessageDto;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public ConversationApplicationService(
            ProjectAccessService projectAccessService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            PresetService presetService,
            ChatPresetDefaults chatPresetDefaults,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService) {
        this.projectAccessService = projectAccessService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.presetService = presetService;
        this.chatPresetDefaults = chatPresetDefaults;
        this.experimentalPresetCatalogService = experimentalPresetCatalogService;
    }

    public List<ConversationDto> listConversations(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return conversationRepository
                .findByProject_IdAndUser_IdOrderByUpdatedAtDesc(projectId, userId)
                .stream()
                .map(this::toConversationDto)
                .toList();
    }

    public ConversationDto createConversation(
            UUID userId, UUID projectId, CreateConversationRequest body) {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        String title = body != null && body.title() != null && !body.title().isBlank()
                ? body.title().trim()
                : "New chat";
        List<String> filter =
                resolveAndValidateDocumentFilter(project.getId(), body != null ? body.documentFilter() : null);
        ConversationEntity c = ConversationEntity.create(project.getOwner(), project, title, filter);
        chatPresetDefaults.loadDeterministicDefaultPreset().ifPresent(c::setPreset);
        return toConversationDto(conversationRepository.save(c));
    }

    public ConversationDto patchConversation(
            UUID userId, UUID conversationId, PatchConversationRequest body) {
        ConversationEntity c = projectAccessService.requireConversationForUser(userId, conversationId);
        boolean changed = false;
        if (body != null && body.title() != null && !body.title().isBlank()) {
            c.setTitle(body.title().trim());
            changed = true;
        }
        if (body != null && Boolean.TRUE.equals(body.clearPreset())) {
            c.setPreset(null);
            changed = true;
        } else if (body != null && body.presetId() != null && !body.presetId().isBlank()) {
            UUID presetId;
            try {
                presetId = UUID.fromString(body.presetId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid presetId");
            }
            RagPresetEntity preset = presetService.requireVisiblePreset(userId, presetId);
            validateExperimentalPresetSupport(preset);
            c.setPreset(preset);
            changed = true;
        }
        if (body != null && body.documentFilter() != null) {
            c.setDocumentFilter(resolveAndValidateDocumentFilter(c.getProject().getId(), body.documentFilter()));
            changed = true;
        }
        if (body != null && Boolean.TRUE.equals(body.clearRuntimeOverride())) {
            c.setRuntimeOverride(Map.of());
            changed = true;
        } else if (body != null && body.runtimeOverride() != null) {
            c.setRuntimeOverride(body.runtimeOverride());
            changed = true;
        }
        if (changed) {
            c.touchUpdated();
            conversationRepository.save(c);
        }
        return toConversationDto(c);
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

    private ConversationDto toConversationDto(ConversationEntity c) {
        UUID presetId = c.getPreset() != null ? c.getPreset().getId() : null;
        List<String> docs = c.getDocumentFilter() != null ? List.copyOf(c.getDocumentFilter()) : List.of();
        Map<String, Object> runtimeOverride = c.getRuntimeOverride() != null ? Map.copyOf(c.getRuntimeOverride()) : Map.of();
        UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(presetId);
        return new ConversationDto(c.getId(), c.getTitle(), c.getUpdatedAt(), presetId, docs, runtimeOverride, effectivePresetId);
    }

    /**
     * Normalizes document ids and ensures each belongs to the project. Empty input yields an empty list (no filter).
     */
    private List<String> resolveAndValidateDocumentFilter(UUID projectId, List<String> raw) {
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
            if (knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "documentFilter contains id not in project: " + docId);
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
                m.getSources(),
                m.getQueryType(),
                m.getPipelineSteps(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getExecutionMetadata());
    }
}
