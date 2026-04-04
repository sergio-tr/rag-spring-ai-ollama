package com.uniovi.rag.service.project;

import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntityTestFactory;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.service.AuditApplicationService;
import com.uniovi.rag.service.preset.PresetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        com.uniovi.rag.infrastructure.persistence.jpa.UserEntity owner = mock(com.uniovi.rag.infrastructure.persistence.jpa.UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        UUID newId = UUID.randomUUID();
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            p.setId(newId);
            return p;
        });
        when(knowledgeDocumentRepository.countByProject_Id(any())).thenReturn(0L);
        when(conversationRepository.countByProject_Id(any())).thenReturn(0L);

        ProjectSummaryDto dto =
                projectService.create(userId, new CreateProjectRequest("  My project  ", null, null));

        assertThat(dto.name()).isEqualTo("My project");
    }

    @Test
    void create_withInitialPresetId_appliesPreset() {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        com.uniovi.rag.infrastructure.persistence.jpa.UserEntity owner = mock(com.uniovi.rag.infrastructure.persistence.jpa.UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        UUID newId = UUID.randomUUID();
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            p.setId(newId);
            return p;
        });
        when(knowledgeDocumentRepository.countByProject_Id(any())).thenReturn(0L);
        when(conversationRepository.countByProject_Id(any())).thenReturn(0L);

        projectService.create(userId, new CreateProjectRequest("P", null, presetId.toString()));

        verify(presetService).applyInitialPresetToProject(userId, newId, presetId);
    }

    @Test
    void get_returnsSummary() {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = ProjectEntityTestFactory.project(pid, "x", "d");
        when(projectAccessService.requireOwnedProject(userId, pid)).thenReturn(p);
        when(knowledgeDocumentRepository.countByProject_Id(pid)).thenReturn(2L);
        when(conversationRepository.countByProject_Id(pid)).thenReturn(1L);

        ProjectSummaryDto dto = projectService.get(userId, pid);

        assertThat(dto.name()).isEqualTo("x");
        assertThat(dto.docCount()).isEqualTo(2L);
        assertThat(dto.convCount()).isEqualTo(1L);
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

        projectService.patch(userId, pid, new PatchProjectRequest("  new  ", "  desc  ", null));

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

        projectService.patch(userId, pid, new PatchProjectRequest(null, "   ", null));

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

        projectService.delete(userId, pid);

        verify(projectRepository).deleteById(pid);
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
