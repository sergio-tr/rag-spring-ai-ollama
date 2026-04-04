package com.uniovi.rag.service.document;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.KnowledgeIndexSnapshotService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.IndexSignature;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeSnapshotDocumentEntity;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Knowledge pipeline: filesystem binary, {@link IndexSignature}, enriched {@code vector_store} metadata, snapshot link.
 */
@Service
public class KnowledgeIngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionOrchestrator.class);

    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeIndexSnapshotService knowledgeIndexSnapshotService;
    private final KnowledgeSnapshotDocumentRepository knowledgeSnapshotDocumentRepository;
    private final BinaryStoragePort binaryStoragePort;
    private final int chunkMaxChars;
    private final String embeddingModelId;
    private final MeterRegistry meterRegistry;

    public KnowledgeIngestionOrchestrator(
            PgVectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIndexSnapshotService knowledgeIndexSnapshotService,
            KnowledgeSnapshotDocumentRepository knowledgeSnapshotDocumentRepository,
            BinaryStoragePort binaryStoragePort,
            @Value("${rag.chunk.max-chars:400}") int chunkMaxChars,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large}") String embeddingModelId,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIndexSnapshotService = knowledgeIndexSnapshotService;
        this.knowledgeSnapshotDocumentRepository = knowledgeSnapshotDocumentRepository;
        this.binaryStoragePort = binaryStoragePort;
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
        this.embeddingModelId = embeddingModelId;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void ingestFromTempFile(
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            log.warn("Project document {} not found, skipping v2 ingest", projectDocumentId);
            deleteTempQuietly(tempFile);
            return;
        }
        try {
            deleteVectorChunksForDocument(projectDocumentId);
            try (InputStream in = Files.newInputStream(tempFile)) {
                long size = Files.size(tempFile);
                BinaryStoragePort.StoredObject stored = binaryStoragePort.store(
                        in, size, projectId + "/" + projectDocumentId + "/source.bin");
                row.setStorageUri(stored.relativeUri());
                row.setContentChecksum(stored.sha256Hex());
                row.setByteSize(size);
                row.setMimeType(contentType);
            }

            byte[] bytes = Files.readAllBytes(tempFile);
            ByteArrayMultipartFile mf = new ByteArrayMultipartFile(
                    "file", originalFilename, contentType, bytes);
            String content = projectDocumentIngestionService.extractContent(mf);
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("empty content");
            }
            List<String> chunks = projectDocumentIngestionService.splitContentIntoChunks(content, chunkMaxChars);
            KnowledgeIndexSnapshotEntity snap = knowledgeIndexSnapshotService.ensureLegacySnapshotForProject(row.getProject());
            IndexSignature sig = IndexSignature.chunkDefaults(embeddingModelId, chunkMaxChars);
            String sigHex = sig.toHashHex();
            String legacyHash = KnowledgeChunkMetadataFactory.legacyContentHashId(originalFilename, content, projectDocumentId);

            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                var meta = KnowledgeChunkMetadataFactory.buildV2(
                        row.getCorpusScope(),
                        projectDocumentId,
                        projectId,
                        row.getConversation() != null ? row.getConversation().getId() : null,
                        snap.getId(),
                        sigHex,
                        originalFilename != null ? originalFilename : "unknown",
                        i,
                        chunks.size(),
                        legacyHash);
                documents.add(new Document(chunks.get(i), meta));
            }
            vectorStore.add(documents);

            row.setStatus(ProjectDocumentStatus.READY);
            row.setChunkCount(chunks.size());
            row.setErrorMessage(null);
            row.setReindexedAt(Instant.now());
            row.setCurrentIndexSnapshot(snap);
            knowledgeDocumentRepository.save(row);

            knowledgeSnapshotDocumentRepository.deleteByDocument_Id(projectDocumentId);
            KnowledgeSnapshotDocumentEntity link = new KnowledgeSnapshotDocumentEntity();
            link.setSnapshot(snap);
            link.setDocument(row);
            knowledgeSnapshotDocumentRepository.save(link);

            log.info("V2 ingested project document {} ({} chunks)", projectDocumentId, chunks.size());
            if (meterRegistry != null) {
                meterRegistry.counter("rag.knowledge.ingest.completed").increment();
            }
        } catch (Exception e) {
            log.error("V2 ingest failed for project document {}: {}", projectDocumentId, e.getMessage());
            row.setStatus(ProjectDocumentStatus.ERROR);
            row.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            knowledgeDocumentRepository.save(row);
        } finally {
            deleteTempQuietly(tempFile);
        }
    }

    /**
     * Removes chunks for this document (legacy {@code projectDocumentId} and new {@code documentId} keys).
     */
    public void deleteVectorChunksForDocument(UUID projectDocumentId) {
        jdbcTemplate.update(
                """
                        DELETE FROM vector_store
                        WHERE metadata->>'projectDocumentId' = ?
                           OR metadata->>'documentId' = ?
                        """,
                projectDocumentId.toString(),
                projectDocumentId.toString());
    }

    private static void deleteTempQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            // intentionally quiet
        }
    }
}
