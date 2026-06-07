package com.uniovi.rag.application.service.project;

import com.uniovi.rag.interfaces.rest.dto.ActivateProjectResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.service.AuditApplicationService;
import com.uniovi.rag.application.service.account.ProjectVisualStyleValidator;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectIndexProfileDto;
import com.uniovi.rag.application.service.preset.PresetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

    /** Deleting this internal index project cascades to the whole Lab knowledge base. */
    public static final String LAB_CORPUS_INDEX_PROJECT_PROTECTED =
            "LAB_CORPUS_INDEX_PROJECT_PROTECTED";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ConversationRepository conversationRepository;
    private final ProjectAccessService projectAccessService;
    private final PresetService presetService;
    private final AuditApplicationService auditApplicationService;
    private final ProjectIndexProfileApplicationService projectIndexProfileApplicationService;
    private final EvaluationCorpusRepository evaluationCorpusRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ConversationRepository conversationRepository,
            ProjectAccessService projectAccessService,
            PresetService presetService,
            AuditApplicationService auditApplicationService,
            ProjectIndexProfileApplicationService projectIndexProfileApplicationService,
            EvaluationCorpusRepository evaluationCorpusRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.conversationRepository = conversationRepository;
        this.projectAccessService = projectAccessService;
        this.presetService = presetService;
        this.auditApplicationService = auditApplicationService;
        this.projectIndexProfileApplicationService = projectIndexProfileApplicationService;
        this.evaluationCorpusRepository = evaluationCorpusRepository;
    }

    @Transactional(readOnly = true)
    public ProjectListResponseDto list(UUID userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        Page<ProjectEntity> slice =
                projectRepository.findByOwner_IdOrderByUpdatedAtDesc(userId, PageRequest.of(p, s));
        return new ProjectListResponseDto(
                slice.getContent().stream().map(proj -> toSummary(proj, null)).toList(),
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

        ProjectIndexProfileDto indexProfile;
        if (req.initialIndexProfile() != null) {
            indexProfile = projectIndexProfileApplicationService.put(userId, p.getId(), req.initialIndexProfile());
        } else {
            indexProfile = projectIndexProfileApplicationService.get(userId, p.getId());
        }
        return toSummary(p, indexProfile);
    }

    @Transactional(readOnly = true)
    public ProjectSummaryDto get(UUID userId, UUID projectId) {
        ProjectEntity p = projectAccessService.requireOwnedProject(userId, projectId);
        ProjectIndexProfileDto indexProfile = projectIndexProfileApplicationService.get(userId, projectId);
        return toSummary(p, indexProfile);
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
        if (req.projectPrompt() != null) {
            p.setProjectPrompt(req.projectPrompt().isBlank() ? null : req.projectPrompt());
            auditApplicationService.persistAuditEntry(
                    userId,
                    "PROJECT_PROMPT_UPDATE",
                    "project",
                    projectId,
                    Map.of("hasPrompt", p.getProjectPrompt() != null));
        }
        if (req.colorHex() != null) {
            ProjectVisualStyleValidator.validateColorHexOrNull(
                    req.colorHex().isBlank() ? null : req.colorHex().trim());
            p.setColorHex(req.colorHex().isBlank() ? null : req.colorHex().trim());
        }
        if (req.iconKey() != null) {
            ProjectVisualStyleValidator.validateIconKeyOrNull(
                    req.iconKey().isBlank() ? null : req.iconKey().trim());
            p.setIconKey(req.iconKey().isBlank() ? null : req.iconKey().trim());
        }
        p.setUpdatedAt(Instant.now());
        p = projectRepository.save(p);
        return toSummary(p, null);
    }

    @Transactional
    public void delete(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        if (evaluationCorpusRepository.existsByIndexProject_Id(projectId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    LAB_CORPUS_INDEX_PROJECT_PROTECTED
                            + ": this project backs a Lab knowledge base; remove documents from the Lab"
                            + " evaluation page instead of deleting the project.");
        }
        projectRepository.deleteById(projectId);
    }

    @Transactional(readOnly = true)
    public ActivateProjectResponseDto activate(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return new ActivateProjectResponseDto(projectId);
    }

    private ProjectSummaryDto toSummary(ProjectEntity p, ProjectIndexProfileDto indexProfile) {
        long docs = knowledgeDocumentRepository.countByProject_Id(p.getId());
        long convs = conversationRepository.countByProject_Id(p.getId());
        return new ProjectSummaryDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                docs,
                convs,
                p.getUpdatedAt(),
                p.getProjectPrompt(),
                p.getColorHex(),
                p.getIconKey(),
                indexProfile);
    }
}
