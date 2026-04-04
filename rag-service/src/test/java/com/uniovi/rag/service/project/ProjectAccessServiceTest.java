package com.uniovi.rag.service.project;

import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @InjectMocks
    private ProjectAccessService projectAccessService;

    @Test
    void requireOwnedProject_found() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity p = mock(ProjectEntity.class);
        when(projectRepository.findByIdAndOwner_Id(pid, uid)).thenReturn(Optional.of(p));

        assertThat(projectAccessService.requireOwnedProject(uid, pid)).isSameAs(p);
    }

    @Test
    void requireOwnedProject_missing_throws() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(projectRepository.findByIdAndOwner_Id(pid, uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectAccessService.requireOwnedProject(uid, pid))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireConversationForUser_found() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(conversationRepository.findByIdAndUser_Id(cid, uid)).thenReturn(Optional.of(c));

        assertThat(projectAccessService.requireConversationForUser(uid, cid)).isSameAs(c);
    }

    @Test
    void requireDocumentForUser_found() {
        UUID uid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ProjectEntity proj = mock(ProjectEntity.class);
        when(proj.getId()).thenReturn(pid);
        ProjectDocumentEntity doc = mock(ProjectDocumentEntity.class);
        when(doc.getProject()).thenReturn(proj);
        when(projectDocumentRepository.findById(did)).thenReturn(Optional.of(doc));
        when(projectRepository.findByIdAndOwner_Id(pid, uid)).thenReturn(Optional.of(mock(ProjectEntity.class)));

        assertThat(projectAccessService.requireDocumentForUser(uid, did)).isSameAs(doc);
    }
}
