package com.uniovi.rag.service.document;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.ProjectDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Async ingestion for {@code project_documents} + scoped vector chunks (metadata {@code projectId}, {@code projectDocumentId}).
 */
@Service
public class ProjectDocumentIngestionService extends AbstractDocumentService {

    private final ProjectDocumentRepository projectDocumentRepository;
    private final int chunkMaxChars;

    public ProjectDocumentIngestionService(PgVectorStore vectorStore,
                                           org.springframework.ai.chat.client.ChatClient chatClient,
                                           JdbcTemplate jdbcTemplate,
                                           ProjectDocumentRepository projectDocumentRepository,
                                           @Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
        super(vectorStore, chatClient, jdbcTemplate);
        this.projectDocumentRepository = projectDocumentRepository;
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
    }

    @Override
    public void processDocument(org.springframework.web.multipart.MultipartFile file) {
        throw new UnsupportedOperationException("Use ingestFromTempFile for project-scoped documents");
    }

    /**
     * Deletes existing vector rows for this project document, then ingests from a temp file (deleted after read).
     */
    @Async
    public void ingestFromTempFile(UUID projectId, UUID projectDocumentId, Path tempFile, String originalFilename,
                                   String contentType) {
        ProjectDocumentEntity row = projectDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            log().warn("Project document {} not found, skipping ingest", projectDocumentId);
            deleteTempQuietlyInstance(tempFile);
            return;
        }
        try {
            deleteVectorChunksForProjectDocument(projectDocumentId);
            byte[] bytes = Files.readAllBytes(tempFile);
            ByteArrayMultipartFile mf = new ByteArrayMultipartFile(
                    "file", originalFilename, contentType, bytes);
            String content = extractContent(mf);
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("empty content");
            }
            List<String> chunks = splitContentIntoChunks(content, chunkMaxChars);
            String documentId = generateDocumentId(originalFilename, content, projectDocumentId);
            if (hasDocumentWithId(documentId)) {
                deleteDocumentByDocumentId(documentId);
            }
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", documentId);
                metadata.put("projectId", projectId.toString());
                metadata.put("projectDocumentId", projectDocumentId.toString());
                metadata.put("filename", originalFilename != null ? originalFilename : "unknown");
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());
                documents.add(new Document(chunks.get(i), metadata));
            }
            vectorStore.add(documents);
            row.setStatus(ProjectDocumentStatus.READY);
            row.setChunkCount(chunks.size());
            row.setErrorMessage(null);
            row.setReindexedAt(java.time.Instant.now());
            projectDocumentRepository.save(row);
            log().info("Ingested project document {} ({} chunks)", projectDocumentId, chunks.size());
        } catch (Exception e) {
            log().error("Ingest failed for project document {}: {}", projectDocumentId, e.getMessage());
            row.setStatus(ProjectDocumentStatus.ERROR);
            row.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            projectDocumentRepository.save(row);
        } finally {
            deleteTempQuietlyInstance(tempFile);
        }
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
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'projectDocumentId' = ?",
                projectDocumentId.toString());
    }

    private static String generateDocumentId(String filename, String content, UUID projectDocumentId) {
        String base = (filename != null ? filename : "unknown") + "_" + projectDocumentId + "_"
                + (content != null ? String.valueOf(content.hashCode()) : "0");
        return String.valueOf(Math.abs(base.hashCode()));
    }
}
