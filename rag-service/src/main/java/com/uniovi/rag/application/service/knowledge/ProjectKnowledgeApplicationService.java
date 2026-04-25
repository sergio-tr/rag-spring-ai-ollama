package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeOperationKind;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.mapper.KnowledgeIndexSnapshotMapper;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeRebuildExecuteRequest;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeRebuildExecuteResponse;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeRebuildPreviewRequest;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeRebuildPreviewResponse;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotDetailResponse;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotSummaryResponse;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectKnowledgeApplicationService {

    private final KnowledgeIndexSnapshotRepository snapshotRepository;
    private final KnowledgeSnapshotDocumentRepository snapshotDocumentRepository;
    private final ProjectAccessService projectAccessService;
    private final KnowledgeConfigurationIntegrationService knowledgeConfigurationIntegrationService;

    public ProjectKnowledgeApplicationService(
            KnowledgeIndexSnapshotRepository snapshotRepository,
            KnowledgeSnapshotDocumentRepository snapshotDocumentRepository,
            ProjectAccessService projectAccessService,
            KnowledgeConfigurationIntegrationService knowledgeConfigurationIntegrationService) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotDocumentRepository = snapshotDocumentRepository;
        this.projectAccessService = projectAccessService;
        this.knowledgeConfigurationIntegrationService = knowledgeConfigurationIntegrationService;
    }

    public KnowledgeRebuildPreviewResponse previewRebuild(
            UUID userId, UUID projectId, KnowledgeRebuildPreviewRequest req) {
        projectAccessService.requireOwnedProject(userId, projectId);
        validateCorpusForList(userId, req.corpusScope(), req.conversationId());
        KnowledgeConfigurationOperationInput input =
                new KnowledgeConfigurationOperationInput(
                        projectId,
                        req.corpusScope(),
                        req.conversationId(),
                        KnowledgeOperationKind.PREVIEW,
                        null,
                        req.presetId(),
                        req.runtimeOverride(),
                        parseTouchedProfileTypes(req.touchedProfileTypes()),
                        userId,
                        req.correlationId());
        KnowledgeRebuildPreviewResult result = knowledgeConfigurationIntegrationService.previewRebuild(input);
        return KnowledgeRebuildPreviewResponse.from(result, req.corpusScope(), req.conversationId());
    }

    public KnowledgeRebuildExecuteResponse executeRebuild(UUID userId, UUID projectId, KnowledgeRebuildExecuteRequest req) {
        projectAccessService.requireOwnedProject(userId, projectId);
        validateCorpusForList(userId, req.corpusScope(), req.conversationId());
        KnowledgeConfigurationOperationInput input =
                new KnowledgeConfigurationOperationInput(
                        projectId,
                        req.corpusScope(),
                        req.conversationId(),
                        KnowledgeOperationKind.EXECUTE,
                        req.explicitResolvedConfigSnapshotId(),
                        req.presetId(),
                        req.runtimeOverride(),
                        parseTouchedProfileTypes(req.touchedProfileTypes()),
                        userId,
                        req.correlationId());
        KnowledgeRebuildExecuteResult result = knowledgeConfigurationIntegrationService.executeRebuild(input);
        return KnowledgeRebuildExecuteResponse.from(result);
    }

    /**
     * Legacy {@code POST /reindex}: same as execute with defaults (plan §10).
     */
    public void triggerReindex(UUID userId, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        validateCorpusForList(userId, corpusScope, conversationId);
        KnowledgeConfigurationOperationInput input =
                new KnowledgeConfigurationOperationInput(
                        projectId,
                        corpusScope,
                        conversationId,
                        KnowledgeOperationKind.EXECUTE,
                        null,
                        null,
                        null,
                        Set.of(),
                        userId,
                        null);
        knowledgeConfigurationIntegrationService.executeRebuild(input);
    }

    public List<KnowledgeSnapshotSummaryResponse> listSnapshots(
            UUID userId, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        validateCorpusForList(userId, corpusScope, conversationId);
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            return snapshotRepository
                    .findByProjectAndScopeProjectOrderByCreatedAtDesc(projectId, KnowledgeSnapshotScopeType.PROJECT)
                    .stream()
                    .map(KnowledgeIndexSnapshotMapper::toDomain)
                    .map(KnowledgeSnapshotSummaryResponse::fromDomain)
                    .toList();
        }
        return snapshotRepository
                .findByConversationAndScopeOrderByCreatedAtDesc(conversationId, KnowledgeSnapshotScopeType.CONVERSATION)
                .stream()
                .map(KnowledgeIndexSnapshotMapper::toDomain)
                .map(KnowledgeSnapshotSummaryResponse::fromDomain)
                .toList();
    }

    public KnowledgeSnapshotDetailResponse getSnapshot(
            UUID userId, UUID projectId, UUID snapshotId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        validateCorpusForList(userId, corpusScope, conversationId);
        KnowledgeIndexSnapshotEntity e =
                snapshotRepository.findById(snapshotId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!e.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            if (e.getConversation() != null || e.getScopeType() != KnowledgeSnapshotScopeType.PROJECT) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } else {
            if (conversationId == null
                    || e.getConversation() == null
                    || !e.getConversation().getId().equals(conversationId)
                    || e.getScopeType() != KnowledgeSnapshotScopeType.CONVERSATION) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        long docCount = snapshotDocumentRepository.countBySnapshot_Id(snapshotId);
        return KnowledgeSnapshotDetailResponse.fromDomain(KnowledgeIndexSnapshotMapper.toDomain(e), docCount);
    }

    private void validateCorpusForList(UUID userId, CorpusScope corpusScope, UUID conversationId) {
        if (corpusScope == CorpusScope.CHAT_LOCAL) {
            if (conversationId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId required for CHAT_LOCAL");
            }
            projectAccessService.requireConversationForUser(userId, conversationId);
        } else if (conversationId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be absent for PROJECT_SHARED");
        }
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
}
