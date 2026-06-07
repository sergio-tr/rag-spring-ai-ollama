package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoveConversationApplicationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private MoveConversationApplicationService service;

    @Test
    void moveConversationToProject_updatesProjectAndDocuments() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID sourcePid = UUID.randomUUID();
        UUID destPid = UUID.randomUUID();

        ProjectEntity sourceProject = Mockito.mock(ProjectEntity.class);
        when(sourceProject.getId()).thenReturn(sourcePid);
        ProjectEntity destProject = Mockito.mock(ProjectEntity.class);
        when(destProject.getId()).thenReturn(destPid);

        ConversationEntity conv = Mockito.mock(ConversationEntity.class);
        when(conv.getProject()).thenReturn(sourceProject);

        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(conv);
        when(projectAccessService.requireOwnedProject(userId, sourcePid)).thenReturn(sourceProject);
        when(projectAccessService.requireOwnedProject(userId, destPid)).thenReturn(destProject);

        service.moveConversationToProject(userId, sourcePid, convId, destPid);

        verify(conv).setProject(destProject);
        verify(conv).setDocumentFilter(eq(List.of()));
        verify(conversationRepository).save(conv);
        verify(knowledgeDocumentRepository).updateProjectForChatLocalDocuments(convId, destPid);
        verify(knowledgeIndexSnapshotRepository)
                .updateProjectIdForConversationSnapshots(eq(convId), eq(destPid), any(Instant.class));
    }

    @Test
    void moveConversationToProject_wrongSourceProject_throws() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID sourcePid = UUID.randomUUID();
        UUID otherPid = UUID.randomUUID();
        UUID destPid = UUID.randomUUID();

        ProjectEntity actualSource = Mockito.mock(ProjectEntity.class);
        when(actualSource.getId()).thenReturn(otherPid);

        ConversationEntity conv = Mockito.mock(ConversationEntity.class);
        when(conv.getProject()).thenReturn(actualSource);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(conv);

        assertThrows(
                NotFoundException.class,
                () -> service.moveConversationToProject(userId, sourcePid, convId, destPid));
    }
}
