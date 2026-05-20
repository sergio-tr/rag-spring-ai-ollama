package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Test
    void ingestFromTempFile_returnsWhenDocumentMissing() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID docId = UUID.randomUUID();
        when(repo.findById(docId)).thenReturn(Optional.empty());

        sut.ingestFromTempFile(UUID.randomUUID(), UUID.randomUUID(), docId, Path.of("x"), "f.txt", "text/plain");

        verify(orchestrator, never()).ingestFromTempFile(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestFromTempFile_persistsDefaultSnapshot_andDelegatesToOrchestrator() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Path temp = Path.of("tmp");

        KnowledgeDocumentEntity row = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        when(row.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(repo.findById(docId)).thenReturn(Optional.of(row));

        ResolvedConfigSnapshotEntity snap = mock(ResolvedConfigSnapshotEntity.class);
        UUID snapId = UUID.randomUUID();
        when(snap.getId()).thenReturn(snapId);
        when(snap.getConfigHash()).thenReturn("hash");
        when(resolved.persistIngestionDefaultSnapshot(eq(userId), eq(projectId), eq(Optional.empty()))).thenReturn(snap);

        sut.ingestFromTempFile(userId, projectId, docId, temp, "f.txt", "text/plain");

        verify(orchestrator)
                .ingestFromTempFile(projectId, docId, temp, "f.txt", "text/plain", snapId, "hash");
    }

    @Test
    void ingestFromTempFile_chatLocal_includesConversationIdInSnapshotCall() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        KnowledgeDocumentEntity row = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        when(row.getCorpusScope()).thenReturn(CorpusScope.CHAT_LOCAL);
        when(row.getConversation().getId()).thenReturn(convId);
        when(repo.findById(docId)).thenReturn(Optional.of(row));

        ResolvedConfigSnapshotEntity snap = mock(ResolvedConfigSnapshotEntity.class);
        when(snap.getId()).thenReturn(UUID.randomUUID());
        when(snap.getConfigHash()).thenReturn("hash");
        when(resolved.persistIngestionDefaultSnapshot(eq(userId), eq(projectId), eq(Optional.of(convId)))).thenReturn(snap);

        sut.ingestFromTempFile(userId, projectId, docId, Path.of("tmp"), "f.txt", "text/plain");

        verify(resolved).persistIngestionDefaultSnapshot(userId, projectId, Optional.of(convId));
    }

    @Test
    void deleteVectorChunksForDocument_delegatesToOrchestrator() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID docId = UUID.randomUUID();
        sut.deleteVectorChunksForDocument(docId);

        verify(orchestrator).deleteVectorChunksForDocument(docId);
    }

    @Test
    void uploadProjectDocument_rejectsEmptyFile() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> sut.uploadProjectDocument(UUID.randomUUID(), UUID.randomUUID(), file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadProjectDocument_persistsRow_createsTempFile_andTriggersIngestion() throws IOException {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(access.requireOwnedProject(userId, projectId)).thenReturn(project);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        Mockito.doAnswer(
                        inv -> {
                            Path target = ((File) inv.getArgument(0)).toPath();
                            Files.writeString(target, "x");
                            return null;
                        })
                .when(file)
                .transferTo(any(File.class));

        KnowledgeDocumentEntity saved = mock(KnowledgeDocumentEntity.class);
        UUID docId = UUID.randomUUID();
        when(saved.getId()).thenReturn(docId);
        when(saved.getFileName()).thenReturn("report.pdf");
        when(saved.getStorageUri()).thenReturn(null);
        when(saved.getStatus()).thenReturn(null);
        when(saved.getChunkCount()).thenReturn(0);
        when(saved.getErrorMessage()).thenReturn(null);
        when(saved.getUploadedAt()).thenReturn(null);
        when(saved.getReindexedAt()).thenReturn(null);
        when(saved.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(saved.getConversation()).thenReturn(null);
        when(saved.getCurrentIndexSnapshot()).thenReturn(null);
        when(repo.save(any(KnowledgeDocumentEntity.class))).thenReturn(saved);

        ProjectDocumentDto dto = sut.uploadProjectDocument(userId, projectId, file);
        assertThat(dto.id()).isEqualTo(docId);

        ArgumentCaptor<Path> tempPath = ArgumentCaptor.forClass(Path.class);
        verify(ingestion)
                .ingestFromTempFile(eq(userId), eq(projectId), eq(docId), tempPath.capture(), eq("report.pdf"), eq("application/pdf"));
        assertThat(Files.exists(tempPath.getValue())).isTrue();
    }

    @Test
    void uploadConversationOverlay_rejectsProjectMismatch() throws IOException {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        KnowledgeIngestionService sut = new KnowledgeIngestionService(orchestrator, repo, ingestion, access, resolved);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(access.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));

        ConversationEntity conv = mock(ConversationEntity.class, Mockito.RETURNS_DEEP_STUBS);
        when(conv.getProject().getId()).thenReturn(UUID.randomUUID());
        when(access.requireConversationForUser(userId, conversationId)).thenReturn(conv);

        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> sut.uploadConversationOverlay(userId, projectId, conversationId, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

