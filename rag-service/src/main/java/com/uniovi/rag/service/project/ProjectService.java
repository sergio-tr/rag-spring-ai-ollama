package com.uniovi.rag.service.project;

import com.uniovi.rag.interfaces.rest.dto.ActivateProjectResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.service.preset.PresetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ConversationRepository conversationRepository;
    private final ProjectAccessService projectAccessService;
    private final PresetService presetService;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectDocumentRepository projectDocumentRepository,
            ConversationRepository conversationRepository,
            ProjectAccessService projectAccessService,
            PresetService presetService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.conversationRepository = conversationRepository;
        this.projectAccessService = projectAccessService;
        this.presetService = presetService;
    }

    @Transactional(readOnly = true)
    public ProjectListResponseDto list(UUID userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        Page<ProjectEntity> slice =
                projectRepository.findByOwner_IdOrderByUpdatedAtDesc(userId, PageRequest.of(p, s));
        return new ProjectListResponseDto(
                slice.getContent().stream().map(this::toSummary).toList(),
                slice.getTotalElements());
    }

    @Transactional
    public ProjectSummaryDto create(UUID userId, CreateProjectRequest req) {
        UserEntity owner = userRepository.findById(userId).orElseThrow();
        String desc = req.description() != null ? req.description() : null;
        ProjectEntity p = ProjectEntityFactory.newOwnedProject(owner, req.name().trim(), desc);
        p = projectRepository.save(p);
        if (req.initialPresetId() != null && !req.initialPresetId().isBlank()) {
            UUID presetId;
            try {
                presetId = UUID.fromString(req.initialPresetId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid initialPresetId");
            }
            presetService.applyInitialPresetToProject(userId, p.getId(), presetId);
        }
        return toSummary(p);
    }

    @Transactional(readOnly = true)
    public ProjectSummaryDto get(UUID userId, UUID projectId) {
        ProjectEntity p = projectAccessService.requireOwnedProject(userId, projectId);
        return toSummary(p);
    }

    @Transactional
    public ProjectSummaryDto patch(UUID userId, UUID projectId, PatchProjectRequest req) {
        ProjectEntity p = projectAccessService.requireOwnedProject(userId, projectId);
        if (req.name() != null && !req.name().isBlank()) {
            p.setName(req.name().trim());
        }
        if (req.description() != null) {
            p.setDescription(req.description().isBlank() ? null : req.description().trim());
        }
        p.setUpdatedAt(Instant.now());
        p = projectRepository.save(p);
        return toSummary(p);
    }

    @Transactional
    public void delete(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        projectRepository.deleteById(projectId);
    }

    @Transactional(readOnly = true)
    public ActivateProjectResponseDto activate(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return new ActivateProjectResponseDto(projectId);
    }

    private ProjectSummaryDto toSummary(ProjectEntity p) {
        long docs = projectDocumentRepository.countByProject_Id(p.getId());
        long convs = conversationRepository.countByProject_Id(p.getId());
        return new ProjectSummaryDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                docs,
                convs,
                p.getUpdatedAt());
    }
}
