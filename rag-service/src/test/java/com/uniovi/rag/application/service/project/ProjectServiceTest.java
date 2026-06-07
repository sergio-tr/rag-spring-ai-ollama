package com.uniovi.rag.application.service.project;

import com.uniovi.rag.application.service.AuditApplicationService;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectIndexProfileDto;
import com.uniovi.rag.interfaces.rest.dto.UpsertProjectIndexProfileRequest;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntityTestFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import com.uniovi.rag.application.service.preset.PresetService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private PresetService presetService;

    @Mock
    private AuditApplicationService auditApplicationService;

    @Mock
    private ProjectIndexProfileApplicationService projectIndexProfileApplicationService;

    @Mock
    private EvaluationCorpusRepository evaluationCorpusRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void list_returnsEmptyPage() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.findByOwner_IdOrderByUpdatedAtDesc(eq(userId), eq(PageRequest.of(0, 24))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 24), 0));

        ProjectListResponseDto dto = projectService.list(userId, 0, 24);

        assertThat(dto.total()).isZero();
        assertThat(dto.items()).isEmpty();
    }

    @Test
    void list_clampsSizeToMax100() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.findByOwner_IdOrderByUpdatedAtDesc(eq(userId), eq(PageRequest.of(0, 100))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        projectService.list(userId, 0, 500);
    }

    @Test
    void create_savesAndReturnsSummary() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        UUID newId = UUID.randomUUID();
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            p.setId(newId);
            return p;
        });
        when(knowledgeDocumentRepository.countByProject_Id(any())).thenReturn(0L);
        when(conversationRepository.countByProject_Id(any())).thenReturn(0L);
        when(projectIndexProfileApplicationService.get(eq(userId), eq(newId)))
                .thenReturn(
                        new ProjectIndexProfileDto(
                                newId,
                                "CHUNK_LEVEL",
                                false,
                                null,
                                null,
                                400,
                                null,
                                "ab",
                                Instant.EPOCH,
                                Instant.EPOCH));

        ProjectSummaryDto dto =
                projectService.create(userId, new CreateProjectRequest("  My project  ", null, null));

        assertThat(dto.name()).isEqualTo("My project");
        assertThat(dto.indexProfile()).isNotNull();
        assertThat(dto.indexProfile().materializationStrategy()).isEqualTo("CHUNK_LEVEL");
    }

    @Test
    void create_withInitialPresetId_appliesPreset() {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        UUID newId = UUID.randomUUID();
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            p.setId(newId);
            return p;
        });
        when(knowledgeDocumentRepository.countByProject_Id(any())).thenReturn(0L);
        when(conversationRepository.countByProject_Id(any())).thenReturn(0L);
        when(projectIndexProfileApplicationService.get(eq(userId), eq(newId)))
                .thenReturn(
                        new ProjectIndexProfileDto(
                                newId,
                                "CHUNK_LEVEL",
                                false,
                                null,
                                null,
                                400,
                                null,
                                "ab",
                                Instant.EPOCH,
                                Instant.EPOCH));

        projectService.create(userId, new CreateProjectRequest("P", null, presetId.toString()));

        verify(presetService).applyInitialPresetToProject(userId, newId, presetId);
    }

    @Test
    void create_withInitialIndexProfile_callsPut() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        UUID newId = UUID.randomUUID();
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            p.setId(newId);
            return p;
        });
        when(knowledgeDocumentRepository.countByProject_Id(any())).thenReturn(0L);
        when(conversationRepository.countByProject_Id(any())).thenReturn(0L);

        UpsertProjectIndexProfileRequest initial =
                new UpsertProjectIndexProfileRequest("DOCUMENT_LEVEL", true, null, "mxbai-embed-large", 800, 40);
        ProjectIndexProfileDto returned =
                new ProjectIndexProfileDto(
                        newId,
                        "DOCUMENT_LEVEL",
                        true,
                        null,
                        "mxbai-embed-large",
                        800,
                        40,
                        "hash",
                        Instant.EPOCH,
                        Instant.EPOCH);
        when(projectIndexProfileApplicationService.put(eq(userId), eq(newId), eq(initial))).thenReturn(returned);

        ProjectSummaryDto dto =
                projectService.create(
                        userId, new CreateProjectRequest("P", null, null, initial));

        verify(projectIndexProfileApplicationService).put(userId, newId, initial);
        assertThat(dto.indexProfile()).isEqualTo(returned);
    }

    @Test
    void get_returnsSummary() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "x", "d");
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(knowledgeDocumentRepository.countByProject_Id(pid)).thenReturn(2L);
        when(conversationRepository.countByProject_Id(pid)).thenReturn(1L);
        when(projectIndexProfileApplicationService.get(userId, pid))
                .thenReturn(
                        new ProjectIndexProfileDto(
                                pid,
                                "CHUNK_LEVEL",
                                false,
                                null,
                                null,
                                400,
                                null,
                                "x",
                                Instant.EPOCH,
                                Instant.EPOCH));

        ProjectSummaryDto dto = projectService.get(userId, pid);

        assertThat(dto.name()).isEqualTo("x");
        assertThat(dto.docCount()).isEqualTo(2L);
        assertThat(dto.convCount()).isEqualTo(1L);
        assertThat(dto.indexProfile()).isNotNull();
    }

    @Test
    void patch_updatesFields() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "old", null);
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeDocumentRepository.countByProject_Id(pid)).thenReturn(0L);
        when(conversationRepository.countByProject_Id(pid)).thenReturn(0L);

        projectService.patch(userId, pid, new PatchProjectRequest("  new  ", "  desc  ", null, null, null));

        ArgumentCaptor<ProjectEntity> cap = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projectRepository).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("new");
        assertThat(cap.getValue().getDescription()).isEqualTo("desc");
    }

    @Test
    void patch_blankDescription_setsNull() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "n", null);
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeDocumentRepository.countByProject_Id(pid)).thenReturn(0L);
        when(conversationRepository.countByProject_Id(pid)).thenReturn(0L);

        projectService.patch(userId, pid, new PatchProjectRequest(null, "   ", null, null, null));

        ArgumentCaptor<ProjectEntity> cap = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projectRepository).save(cap.capture());
        assertThat(cap.getValue().getDescription()).isNull();
    }

    @Test
    void delete_removesProject() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "x", null);
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(evaluationCorpusRepository.existsByIndexProject_Id(pid)).thenReturn(false);

        projectService.delete(userId, pid);

        verify(projectRepository).deleteById(pid);
    }

    @Test
    void delete_rejectsLabCorpusIndexProject() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "Lab knowledge base · demo", null);
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(evaluationCorpusRepository.existsByIndexProject_Id(pid)).thenReturn(true);

        assertThatThrownBy(() -> projectService.delete(userId, pid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(ProjectService.LAB_CORPUS_INDEX_PROJECT_PROTECTED);

        verify(projectRepository, never()).deleteById(pid);
    }

    @Test
    void activate_returnsProjectId() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, pid))
                .thenReturn(ProjectEntityTestFactory.project(pid, "x", null));

        assertThat(projectService.activate(userId, pid).activeProjectId()).isEqualTo(pid);
    }
}
