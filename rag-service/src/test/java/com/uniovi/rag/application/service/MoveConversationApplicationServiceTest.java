package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private MoveConversationApplicationService service;

    @Test
    void moveConversationToProject_updatesProjectAndDocuments() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID sourcePid = UUID.randomUUID();
        UUID destPid = UUID.randomUUID();

        ProjectEntity sourceProject = org.mockito.Mockito.mock(ProjectEntity.class);
        when(sourceProject.getId()).thenReturn(sourcePid);
        ProjectEntity destProject = org.mockito.Mockito.mock(ProjectEntity.class);
        when(destProject.getId()).thenReturn(destPid);

        ConversationEntity conv = org.mockito.Mockito.mock(ConversationEntity.class);
        when(conv.getProject()).thenReturn(sourceProject);

        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(conv);
        when(projectAccessService.requireOwnedProject(userId, sourcePid)).thenReturn(sourceProject);
        when(projectAccessService.requireOwnedProject(userId, destPid)).thenReturn(destProject);

        service.moveConversationToProject(userId, sourcePid, convId, destPid);

        verify(conv).setProject(destProject);
        verify(conversationRepository).save(conv);
        verify(knowledgeDocumentRepository).updateProjectForChatLocalDocuments(convId, destPid);
    }

    @Test
    void moveConversationToProject_wrongSourceProject_throws() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID sourcePid = UUID.randomUUID();
        UUID otherPid = UUID.randomUUID();
        UUID destPid = UUID.randomUUID();

        ProjectEntity actualSource = org.mockito.Mockito.mock(ProjectEntity.class);
        when(actualSource.getId()).thenReturn(otherPid);

        ConversationEntity conv = org.mockito.Mockito.mock(ConversationEntity.class);
        when(conv.getProject()).thenReturn(actualSource);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(conv);

        assertThrows(
                com.uniovi.rag.interfaces.rest.NotFoundException.class,
                () -> service.moveConversationToProject(userId, sourcePid, convId, destPid));
    }
}
