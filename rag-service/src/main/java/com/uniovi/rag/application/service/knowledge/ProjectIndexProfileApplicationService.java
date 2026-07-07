package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.interfaces.rest.dto.ProjectIndexProfileDto;
import com.uniovi.rag.interfaces.rest.dto.UpsertProjectIndexProfileRequest;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectIndexProfileApplicationService {

    private final ProjectAccessService projectAccessService;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public ProjectIndexProfileApplicationService(
            ProjectAccessService projectAccessService,
            ProjectIndexProfileService projectIndexProfileService,
            KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.projectAccessService = projectAccessService;
        this.projectIndexProfileService = projectIndexProfileService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    public ProjectIndexProfileDto get(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return toDto(projectIndexProfileService.ensureDefault(projectId));
    }

    public ProjectIndexProfileDto put(UUID userId, UUID projectId, UpsertProjectIndexProfileRequest body) {
        projectAccessService.requireOwnedProject(userId, projectId);

        List<KnowledgeDocumentEntity> docs =
                knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED);
        boolean hasReady = docs.stream().anyMatch(d -> d.getStatus() == ProjectDocumentStatus.READY);
        if (hasReady) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Project index profile change requires reindexing. Reindex the project to apply new index capabilities.");
        }

        MaterializationStrategy strategy = parseStrategy(body != null ? body.materializationStrategy() : null);
        boolean metadataEnabled = body != null && Boolean.TRUE.equals(body.metadataEnabled());
        validateStrategyMetadataCombination(strategy, metadataEnabled);
        String embedding = body != null ? body.embeddingModelId() : null;
        int chunkMaxChars = body != null && body.chunkMaxChars() != null ? body.chunkMaxChars() : 0;
        Integer chunkOverlap = body != null ? body.chunkOverlap() : null;
        String metadataProfile = body != null ? body.metadataProfile() : null;

        return toDto(
                projectIndexProfileService.upsert(
                        userId,
                        projectId, strategy, metadataEnabled, metadataProfile, embedding, chunkMaxChars, chunkOverlap));
    }

    private static MaterializationStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) return MaterializationStrategy.CHUNK_LEVEL;
        try {
            return MaterializationStrategy.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid materializationStrategy");
        }
    }

    private static void validateStrategyMetadataCombination(
            MaterializationStrategy strategy, boolean metadataEnabled) {
        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH && !metadataEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "STRUCTURED_SEARCH requires metadata-aware indexing. Enable metadata-aware index capability or choose another indexing strategy.");
        }
    }

    private static ProjectIndexProfileDto toDto(ProjectIndexProfile p) {
        return new ProjectIndexProfileDto(
                p.projectId(),
                p.materializationStrategy() != null ? p.materializationStrategy().name() : null,
                p.metadataEnabled(),
                p.metadataProfile(),
                p.embeddingModelId(),
                p.chunkMaxChars(),
                p.chunkOverlap(),
                p.profileHash(),
                p.createdAt(),
                p.updatedAt());
    }
}

