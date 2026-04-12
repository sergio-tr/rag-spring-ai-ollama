package com.uniovi.rag.service.document;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectDocumentIngestionService}.
 */
class ProjectDocumentIngestionServiceTest {

    @Test
    void ingestFromTempFile_whenRowMissing_deletesTempQuietlyAndDoesNotDelegate() throws Exception {
        PgVectorStore vs = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);

        ProjectDocumentIngestionService sut =
                new ProjectDocumentIngestionService(vs, chatClient, jdbc, repo, ingestion);

        UUID docId = UUID.randomUUID();
        Path temp = Files.createTempFile("projdoc-", ".txt");
        Files.writeString(temp, "x");

        when(repo.findById(docId)).thenReturn(Optional.empty());

        sut.ingestFromTempFile(UUID.randomUUID(), UUID.randomUUID(), docId, temp, "f.txt", "text/plain");

        verify(ingestion, never()).ingestFromTempFile(any(), any(), any(), any(), any(), any());
        assertThat(Files.exists(temp)).isFalse();
    }

    @Test
    void ingestFromTempFile_whenRowPresent_delegatesToKnowledgeIngestionService() throws Exception {
        PgVectorStore vs = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);

        ProjectDocumentIngestionService sut =
                new ProjectDocumentIngestionService(vs, chatClient, jdbc, repo, ingestion);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Path temp = Files.createTempFile("projdoc-", ".txt");
        Files.writeString(temp, "x");

        KnowledgeDocumentEntity row = mock(KnowledgeDocumentEntity.class);
        when(repo.findById(docId)).thenReturn(Optional.of(row));

        sut.ingestFromTempFile(userId, projectId, docId, temp, "f.txt", "text/plain");

        verify(ingestion).ingestFromTempFile(eq(userId), eq(projectId), eq(docId), eq(temp), eq("f.txt"), eq("text/plain"));
    }

    @Test
    void deleteVectorChunksForProjectDocument_delegatesToKnowledgeIngestionService() {
        PgVectorStore vs = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);

        ProjectDocumentIngestionService sut =
                new ProjectDocumentIngestionService(vs, chatClient, jdbc, repo, ingestion);

        UUID docId = UUID.randomUUID();
        sut.deleteVectorChunksForProjectDocument(docId);

        verify(ingestion).deleteVectorChunksForDocument(docId);
    }

    @Test
    void processDocument_alwaysThrowsUnsupportedOperation() {
        PgVectorStore vs = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
        KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);

        ProjectDocumentIngestionService sut =
                new ProjectDocumentIngestionService(vs, chatClient, jdbc, repo, ingestion);

        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        assertThatThrownBy(() -> sut.processDocument(file)).isInstanceOf(UnsupportedOperationException.class);
    }
}
