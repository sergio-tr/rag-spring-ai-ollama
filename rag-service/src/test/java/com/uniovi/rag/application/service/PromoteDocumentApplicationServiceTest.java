package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoteDocumentApplicationServiceTest {

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectDocumentIngestionService ingestionService;

    @Mock
    private BinaryStoragePort binaryStoragePort;

    @InjectMocks
    private PromoteDocumentApplicationService service;

    @Test
    void promote_wrongProject_notFound() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID otherProject = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(otherProject);
        KnowledgeDocumentEntity src = Mockito.mock(KnowledgeDocumentEntity.class);
        when(src.getProject()).thenReturn(project);
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(src);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.promote(userId, projectId, docId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void promote_notChatLocal_conflict() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        KnowledgeDocumentEntity src = Mockito.mock(KnowledgeDocumentEntity.class);
        when(src.getProject()).thenReturn(project);
        when(src.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(src);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.promote(userId, projectId, docId));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void promote_noStoredBinary_unprocessable() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        KnowledgeDocumentEntity src = Mockito.mock(KnowledgeDocumentEntity.class);
        when(src.getProject()).thenReturn(project);
        when(src.getCorpusScope()).thenReturn(CorpusScope.CHAT_LOCAL);
        when(src.getStorageUri()).thenReturn("  ");
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(src);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.promote(userId, projectId, docId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void promote_success() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID promotedId = UUID.randomUUID();

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);

        KnowledgeDocumentEntity src = Mockito.mock(KnowledgeDocumentEntity.class);
        when(src.getProject()).thenReturn(project);
        when(src.getCorpusScope()).thenReturn(CorpusScope.CHAT_LOCAL);
        when(src.getStorageUri()).thenReturn("src/blob");
        when(src.getFileName()).thenReturn("f.txt");
        when(src.getMimeType()).thenReturn("text/plain");
        when(src.getByteSize()).thenReturn(10L);
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(src);

        when(knowledgeDocumentRepository.save(any(KnowledgeDocumentEntity.class)))
                .thenAnswer(
                        inv -> {
                            KnowledgeDocumentEntity e = inv.getArgument(0);
                            if (e.getId() == null) {
                                ReflectionTestUtils.setField(e, "id", promotedId);
                            }
                            return e;
                        });

        when(binaryStoragePort.linkOrCopy(eq("src/blob"), eq(projectId + "/" + promotedId + "/promoted.bin")))
                .thenReturn(new BinaryStoragePort.StoredObject("dst/blob", "deadbeef"));

        when(binaryStoragePort.openStream("dst/blob"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        service.promote(userId, projectId, docId);

        verify(ingestionService)
                .ingestFromTempFile(
                        eq(userId),
                        eq(projectId),
                        eq(promotedId),
                        any(),
                        eq("f.txt"),
                        eq("text/plain"));
    }
}
