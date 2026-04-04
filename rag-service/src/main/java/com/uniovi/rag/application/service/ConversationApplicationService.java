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
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
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

    public ConversationApplicationService(
            ProjectAccessService projectAccessService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            PresetService presetService) {
        this.projectAccessService = projectAccessService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.presetService = presetService;
    }

    public List<ConversationDto> listConversations(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return conversationRepository
                .findByProject_IdAndUser_IdOrderByUpdatedAtDesc(projectId, userId)
                .stream()
                .map(ConversationApplicationService::toConversationDto)
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
            c.setPreset(preset);
            changed = true;
        }
        if (body != null && body.documentFilter() != null) {
            c.setDocumentFilter(resolveAndValidateDocumentFilter(c.getProject().getId(), body.documentFilter()));
            changed = true;
        }
        if (changed) {
            c.touchUpdated();
            conversationRepository.save(c);
        }
        return toConversationDto(c);
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

    private static ConversationDto toConversationDto(ConversationEntity c) {
        UUID presetId = c.getPreset() != null ? c.getPreset().getId() : null;
        List<String> docs = c.getDocumentFilter() != null ? List.copyOf(c.getDocumentFilter()) : List.of();
        return new ConversationDto(c.getId(), c.getTitle(), c.getUpdatedAt(), presetId, docs);
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
