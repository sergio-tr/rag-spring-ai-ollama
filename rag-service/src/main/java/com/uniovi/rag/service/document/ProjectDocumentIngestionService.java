package com.uniovi.rag.service.document;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Async ingestion for {@code project_documents}; delegates indexing to {@link KnowledgeIngestionService}.
 */
@Service
public class ProjectDocumentIngestionService extends AbstractDocumentService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeIngestionService knowledgeIngestionService;

    public ProjectDocumentIngestionService(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            JdbcTemplate jdbcTemplate,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIngestionService knowledgeIngestionService) {
        super(vectorStore, chatClient, jdbcTemplate);
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Override
    public void processDocument(MultipartFile file) {
        throw new UnsupportedOperationException("Use ingestFromTempFile for project-scoped documents");
    }

    /**
     * Deletes existing vector rows for this project document, then ingests from a temp file (deleted after read).
     */
    @Async("documentIngestionExecutor")
    public void ingestFromTempFile(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            log().warn("Project document {} not found, skipping ingest", projectDocumentId);
            deleteTempQuietlyInstance(tempFile);
            return;
        }
        knowledgeIngestionService.ingestFromTempFile(
                userId, projectId, projectDocumentId, tempFile, originalFilename, contentType);
    }

    private void deleteTempQuietlyInstance(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log().warn("Could not delete temp file: {}", e.getMessage());
        }
    }

    public void deleteVectorChunksForProjectDocument(UUID projectDocumentId) {
        knowledgeIngestionService.deleteVectorChunksForDocument(projectDocumentId);
    }
}
