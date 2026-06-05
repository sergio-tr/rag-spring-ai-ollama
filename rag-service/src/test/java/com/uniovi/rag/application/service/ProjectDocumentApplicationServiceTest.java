package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentApplicationServiceTest {

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private BinaryStoragePort binaryStoragePort;

    @InjectMocks
    private ProjectDocumentApplicationService service;

    @Test
    void listDocuments_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        KnowledgeDocumentEntity e = mock(KnowledgeDocumentEntity.class);
        UUID eid = UUID.randomUUID();
        when(e.getId()).thenReturn(eid);
        when(e.getFileName()).thenReturn("f.txt");
        when(e.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(e.getChunkCount()).thenReturn(1);
        when(e.getErrorMessage()).thenReturn(null);
        when(e.getUploadedAt()).thenReturn(null);
        when(e.getReindexedAt()).thenReturn(null);
        when(e.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(e.getConversation()).thenReturn(null);
        when(e.getCurrentIndexSnapshot()).thenReturn(null);
        when(e.getStorageUri()).thenReturn(null);
        when(knowledgeDocumentRepository.findByProject_IdOrderByUploadedAtDesc(projectId)).thenReturn(List.of(e));

        List<ProjectDocumentDto> out = service.listDocuments(userId, projectId);
        assertEquals(1, out.size());
        assertEquals(eid, out.getFirst().id());
    }

    @Test
    void deleteDocument_notFound() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        when(knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId)).thenReturn(Optional.empty());

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.deleteDocument(userId, projectId, docId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteDocument_ok() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        KnowledgeDocumentEntity e = mock(KnowledgeDocumentEntity.class);
        when(knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId)).thenReturn(Optional.of(e));

        service.deleteDocument(userId, projectId, docId);
        verify(knowledgeIngestionService).deleteVectorChunksForDocument(docId);
        verify(knowledgeDocumentRepository).delete(e);
    }

    @Test
    void documentStatus_returnsDto() {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentEntity e = mock(KnowledgeDocumentEntity.class);
        when(e.getId()).thenReturn(docId);
        when(e.getFileName()).thenReturn("a");
        when(e.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(e.getChunkCount()).thenReturn(0);
        when(e.getErrorMessage()).thenReturn(null);
        when(e.getUploadedAt()).thenReturn(null);
        when(e.getReindexedAt()).thenReturn(null);
        when(e.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(e.getConversation()).thenReturn(null);
        when(e.getCurrentIndexSnapshot()).thenReturn(null);
        when(e.getStorageUri()).thenReturn(null);
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(e);

        ProjectDocumentDto dto = service.documentStatus(userId, docId);
        assertEquals(docId, dto.id());
    }

    @Test
    void uploadConversationOverlay_delegates() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        ProjectDocumentDto expected = mock(ProjectDocumentDto.class);
        when(knowledgeIngestionService.uploadConversationOverlay(userId, pid, cid, file)).thenReturn(expected);

        assertEquals(expected, service.uploadConversationOverlay(userId, pid, cid, file));
    }

    @Test
    void uploadDocument_delegates() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        ProjectDocumentDto expected = mock(ProjectDocumentDto.class);
        when(knowledgeIngestionService.uploadProjectDocument(userId, pid, file)).thenReturn(expected);

        assertEquals(expected, service.uploadDocument(userId, pid, file));
    }

    @Test
    void reindexDocument_emptyFile_badRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.txt", "text/plain", new byte[0]);
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        assertThrows(ResponseStatusException.class, () -> service.reindexDocument(userId, docId, empty));
    }

    @Test
    void reindexDocument_success() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        KnowledgeDocumentEntity row = mock(KnowledgeDocumentEntity.class);
        when(row.getProject()).thenReturn(project);
        when(projectAccessService.requireDocumentForUser(userId, docId)).thenReturn(row);

        ProjectDocumentDto terminal =
                new ProjectDocumentDto(
                        docId,
                        "f.txt",
                        ProjectDocumentStatus.READY,
                        0,
                        null,
                        null,
                        null,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        null,
                        null,
                        false);
        when(knowledgeIngestionService.loadTerminalProjectDocumentDto(docId)).thenReturn(terminal);

        MockMultipartFile file =
                new MockMultipartFile("file", "f.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));

        ProjectDocumentDto dto = service.reindexDocument(userId, docId, file);
        verify(knowledgeDocumentRepository).save(row);
        verify(knowledgeIngestionService)
                .ingestFromTempFileJoiningCallerTransaction(
                        eq(userId), eq(projectId), eq(docId), any(), eq("f.txt"), eq("text/plain"));
        assertEquals(docId, dto.id());
    }
}
