package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.ProjectDocumentStatus;
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
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    private static KnowledgeIngestionService newSut(
            KnowledgePipelineOrchestrator orchestrator,
            KnowledgeDocumentRepository repo,
            ProjectDocumentIngestionService ingestion,
            ProjectAccessService access,
            ResolvedConfigSnapshotApplicationService resolved,
            ResolvedLlmConfigResolver llmConfigResolver,
            EntityManager entityManager) {
        BinaryStoragePort storage = mock(BinaryStoragePort.class);
        return new KnowledgeIngestionService(
                orchestrator, repo, ingestion, access, resolved, llmConfigResolver, entityManager, storage);
    }

    @Test
    void ingestFromTempFile_returnsWhenDocumentMissing() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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
    void ingestProjectSharedDocumentSynchronouslyFromBytes_flushesBeforePipeline_andClearsBeforeReload()
            throws IOException {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);
        EntityManager entityManager = mock(EntityManager.class);

        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(access.requireOwnedProject(userId, projectId)).thenReturn(project);

        KnowledgeDocumentEntity ingesting = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        when(ingesting.getId()).thenReturn(docId);
        when(ingesting.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(ingesting.getConversation()).thenReturn(null);

        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getId()).thenReturn(docId);
        when(ready.getFileName()).thenReturn("bootstrap-acta.txt");
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(ready.getChunkCount()).thenReturn(3);
        when(ready.getErrorMessage()).thenReturn(null);
        when(ready.getUploadedAt()).thenReturn(null);
        when(ready.getReindexedAt()).thenReturn(null);
        when(ready.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(ready.getConversation()).thenReturn(null);
        when(ready.getCurrentIndexSnapshot()).thenReturn(null);
        when(ready.getStorageUri()).thenReturn("projects/x/doc.bin");

        when(repo.save(any(KnowledgeDocumentEntity.class))).thenReturn(ingesting);
        when(repo.findById(docId)).thenReturn(Optional.of(ingesting), Optional.of(ready));

        ResolvedConfigSnapshotEntity snap = mock(ResolvedConfigSnapshotEntity.class);
        when(snap.getId()).thenReturn(UUID.randomUUID());
        when(snap.getConfigHash()).thenReturn("hash");
        when(resolved.persistIngestionDefaultSnapshot(eq(userId), eq(projectId), eq(Optional.empty())))
                .thenReturn(snap);

        ProjectDocumentDto dto =
                sut.ingestProjectSharedDocumentSynchronouslyFromBytes(
                        userId, projectId, "acta".getBytes(), "bootstrap-acta.txt", "text/plain");

        assertThat(dto.id()).isEqualTo(docId);
        assertThat(dto.status()).isEqualTo(ProjectDocumentStatus.READY);
        verify(entityManager, atLeastOnce()).flush();
        verify(entityManager).clear();
        verify(entityManager, never()).refresh(any());
        verify(orchestrator)
                .ingestFromTempFileInCurrentTransaction(
                        eq(projectId), eq(docId), any(Path.class), eq("bootstrap-acta.txt"), eq("text/plain"), any(), any());
        verify(orchestrator, never()).ingestFromTempFile(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteVectorChunksForDocument_delegatesToOrchestrator() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

        KnowledgeDocumentEntity ingesting = mock(KnowledgeDocumentEntity.class);
        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        UUID docId = UUID.randomUUID();
        when(ingesting.getId()).thenReturn(docId);
        when(ready.getId()).thenReturn(docId);
        when(ready.getFileName()).thenReturn("report.pdf");
        when(ready.getStorageUri()).thenReturn("projects/x/doc.bin");
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(ready.getChunkCount()).thenReturn(3);
        when(ready.getErrorMessage()).thenReturn(null);
        when(ready.getUploadedAt()).thenReturn(null);
        when(ready.getReindexedAt()).thenReturn(null);
        when(ready.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(ready.getConversation()).thenReturn(null);
        when(ready.getCurrentIndexSnapshot()).thenReturn(null);
        when(repo.save(any(KnowledgeDocumentEntity.class))).thenReturn(ingesting);
        when(repo.findById(docId)).thenReturn(Optional.of(ingesting), Optional.of(ready));

        ResolvedConfigSnapshotEntity snap = mock(ResolvedConfigSnapshotEntity.class);
        when(snap.getId()).thenReturn(UUID.randomUUID());
        when(snap.getConfigHash()).thenReturn("hash");
        when(resolved.persistIngestionDefaultSnapshot(eq(userId), eq(projectId), eq(Optional.empty())))
                .thenReturn(snap);

        ProjectDocumentDto dto = sut.uploadProjectDocument(userId, projectId, file);
        assertThat(dto.id()).isEqualTo(docId);
        assertThat(dto.status()).isEqualTo(ProjectDocumentStatus.READY);
        verify(entityManager, atLeastOnce()).flush();
        verify(entityManager).clear();
        verify(orchestrator)
                .ingestFromTempFileInCurrentTransaction(
                        eq(projectId), eq(docId), any(Path.class), eq("report.pdf"), eq("application/pdf"), any(), any());
        verify(ingestion, never()).ingestFromTempFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadConversationOverlay_rejectsProjectMismatch() throws IOException {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);

        EntityManager entityManager = mock(EntityManager.class);
        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

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

    @Test
    void loadTerminalProjectDocumentDto_marksStaleIngestingAsError() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);
        EntityManager entityManager = mock(EntityManager.class);

        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

        UUID docId = UUID.randomUUID();
        KnowledgeDocumentEntity stuck = mock(KnowledgeDocumentEntity.class);
        when(stuck.getId()).thenReturn(docId);
        when(stuck.getFileName()).thenReturn("stuck.txt");
        when(stuck.getStatus())
                .thenReturn(ProjectDocumentStatus.INGESTING, ProjectDocumentStatus.ERROR);
        when(stuck.getChunkCount()).thenReturn(0);
        when(stuck.getErrorMessage()).thenReturn(null);
        when(stuck.getUploadedAt()).thenReturn(null);
        when(stuck.getReindexedAt()).thenReturn(null);
        when(stuck.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(stuck.getConversation()).thenReturn(null);
        when(stuck.getCurrentIndexSnapshot()).thenReturn(null);
        when(stuck.getStorageUri()).thenReturn("uri");

        when(repo.findById(docId)).thenReturn(Optional.of(stuck));
        when(repo.save(stuck)).thenReturn(stuck);

        ProjectDocumentDto dto = sut.loadTerminalProjectDocumentDto(docId);

        assertThat(dto.status()).isEqualTo(ProjectDocumentStatus.ERROR);
        verify(stuck).setStatus(ProjectDocumentStatus.ERROR);
        verify(stuck)
                .setErrorMessage(
                        argThat(msg -> msg != null && msg.contains("FAILED_STALE_INGESTION")));
    }

    @Test
    void retryIngestFromStoredBinarySynchronously_flushesSnapshotBeforeNestedPipelineTransaction() {
        KnowledgePipelineOrchestrator orchestrator = mock(KnowledgePipelineOrchestrator.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        ProjectDocumentIngestionService ingestion = mock(ProjectDocumentIngestionService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        ResolvedConfigSnapshotApplicationService resolved = mock(ResolvedConfigSnapshotApplicationService.class);
        EntityManager entityManager = mock(EntityManager.class);

        KnowledgeIngestionService sut =
                newSut(orchestrator, repo, ingestion, access, resolved, mock(ResolvedLlmConfigResolver.class), entityManager);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        KnowledgeDocumentEntity ingesting = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        when(ingesting.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(ingesting.getConversation()).thenReturn(null);
        when(ingesting.getStorageUri()).thenReturn("projects/x/source.bin");
        when(ingesting.getProject().getId()).thenReturn(projectId);

        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getId()).thenReturn(docId);
        when(ready.getFileName()).thenReturn("acta.pdf");
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(ready.getChunkCount()).thenReturn(5);
        when(ready.getErrorMessage()).thenReturn(null);
        when(ready.getUploadedAt()).thenReturn(null);
        when(ready.getReindexedAt()).thenReturn(null);
        when(ready.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(ready.getConversation()).thenReturn(null);
        when(ready.getCurrentIndexSnapshot()).thenReturn(null);
        when(ready.getStorageUri()).thenReturn("projects/x/source.bin");

        when(repo.findById(docId)).thenReturn(Optional.of(ingesting), Optional.of(ready));
        when(repo.save(any(KnowledgeDocumentEntity.class))).thenReturn(ingesting);

        ResolvedConfigSnapshotEntity snap = mock(ResolvedConfigSnapshotEntity.class);
        UUID snapId = UUID.randomUUID();
        when(snap.getId()).thenReturn(snapId);
        when(snap.getConfigHash()).thenReturn("hash");
        when(resolved.persistIngestionDefaultSnapshot(eq(userId), eq(projectId), eq(Optional.empty())))
                .thenReturn(snap);

        sut.retryIngestFromStoredBinarySynchronously(userId, projectId, docId);

        // flush after INGESTING row save, after snapshot persist, and before reloadProjectDocumentAfterIngest clear
        verify(entityManager, times(3)).flush();
        verify(entityManager).clear();
        verify(orchestrator)
                .ingestFromStoredBinaryInCurrentTransaction(projectId, docId, snapId, "hash");
    }
}

