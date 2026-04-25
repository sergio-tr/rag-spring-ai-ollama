package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectKnowledgeApplicationServiceTest {

    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotDocumentRepository snapshotDocumentRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private KnowledgeConfigurationIntegrationService knowledgeConfigurationIntegrationService;

    @Test
    void listSnapshots_chatLocal_requiresConversationId() {
        ProjectKnowledgeApplicationService svc = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.listSnapshots(userId, projectId, CorpusScope.CHAT_LOCAL, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void listSnapshots_projectShared_rejectsConversationId() {
        ProjectKnowledgeApplicationService svc = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.listSnapshots(userId, projectId, CorpusScope.PROJECT_SHARED, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getSnapshot_notFound_whenMissing() {
        ProjectKnowledgeApplicationService svc = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.getSnapshot(userId, projectId, snapshotId, CorpusScope.PROJECT_SHARED, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getSnapshot_notFound_whenProjectMismatch() {
        ProjectKnowledgeApplicationService svc = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        KnowledgeIndexSnapshotEntity e = mock(KnowledgeIndexSnapshotEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(e.getProject()).thenReturn(project);
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> svc.getSnapshot(userId, projectId, snapshotId, CorpusScope.PROJECT_SHARED, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getSnapshot_chatLocal_notFound_whenConversationMismatch() {
        ProjectKnowledgeApplicationService svc = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        KnowledgeIndexSnapshotEntity e = mock(KnowledgeIndexSnapshotEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        when(e.getProject()).thenReturn(project);
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(UUID.randomUUID());
        when(e.getConversation()).thenReturn(conv);
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> svc.getSnapshot(userId, projectId, snapshotId, CorpusScope.CHAT_LOCAL, conversationId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private ProjectKnowledgeApplicationService service() {
        return new ProjectKnowledgeApplicationService(
                snapshotRepository, snapshotDocumentRepository, projectAccessService, knowledgeConfigurationIntegrationService);
    }
}

