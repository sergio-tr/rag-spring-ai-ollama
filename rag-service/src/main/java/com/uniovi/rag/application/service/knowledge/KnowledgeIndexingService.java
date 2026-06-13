package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.infrastructure.persistence.DocumentArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import com.uniovi.rag.application.service.knowledge.document.ByteArrayMultipartFile;
import com.uniovi.rag.application.service.knowledge.document.KnowledgeChunkMetadataFactory;
import com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline stages for one workspace document; invoked only from {@link KnowledgePipelineOrchestrator}.
 */
@Service
public class KnowledgeIndexingService {

    private static final String JSON_KEY_SCHEMA_VERSION = "schemaVersion";
    private static final int ARTIFACT_SCHEMA_VERSION = 1;
    private static final String UNKNOWN_FILENAME_LABEL = "unknown";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

    private final PgVectorStoreRegistry vectorStoreRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final BinaryStoragePort binaryStoragePort;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final IndexingEmbeddingGuard indexingEmbeddingGuard;

    public KnowledgeIndexingService(
            PgVectorStoreRegistry vectorStoreRegistry,
            JdbcTemplate jdbcTemplate,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            BinaryStoragePort binaryStoragePort,
            DocumentArtifactRepository documentArtifactRepository,
            IndexingEmbeddingGuard indexingEmbeddingGuard) {
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.binaryStoragePort = binaryStoragePort;
        this.documentArtifactRepository = documentArtifactRepository;
        this.indexingEmbeddingGuard = indexingEmbeddingGuard;
    }

    /**
     * Runs parse → metadata → optional chunk → embed → index artifact + vectors per §8 matrix.
     */
    public void processDocument(KnowledgeDocumentIndexingRequest req) throws IOException {
        KnowledgeDocumentEntity doc = req.doc();
        Path tempFileOverride = req.tempFileOverride();
        KnowledgeIndexSnapshotEntity snapshot = req.snapshot();
        MaterializationStrategy strategy = req.strategy();
        int effectiveChunkMaxChars = req.effectiveChunkMaxChars();
        String indexSigHex = req.indexSigHex();
        byte[] bytes = loadBytes(doc, tempFileOverride);
        String name = coalesce(doc.getFileName(), req.originalFilename());
        String ct = coalesce(doc.getMimeType(), req.contentType());
        String content = extractRequiredContent(bytes, name, ct);

        Instant now = Instant.now();
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
        parsed.put("normalizedText", content);
        parsed.put("mimeType", ct != null ? ct : "");
        saveArtifact(doc, DocumentArtifactType.PARSED, parsed, now);

        Map<String, Object> meta = buildMetadataPayload(strategy, content, name);
        saveArtifact(doc, DocumentArtifactType.METADATA, meta, now);

        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            Map<String, Object> indexPayload = structuredSearchIndexPayload();
            saveArtifact(doc, DocumentArtifactType.INDEX, indexPayload, now);
            return;
        }

        int embedMaxChars = indexingEmbeddingGuard.effectiveEmbedMaxChars(effectiveChunkMaxChars);

        List<String> chunks;
        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            chunks = List.of(content);
        } else {
            chunks = projectDocumentIngestionService.splitContentIntoChunks(content, effectiveChunkMaxChars);
        }

        if (strategy != MaterializationStrategy.DOCUMENT_LEVEL) {
            Map<String, Object> chunkPayload = new LinkedHashMap<>();
            chunkPayload.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
            chunkPayload.put("chunkCount", chunks.size());
            chunkPayload.put("chunks", chunks);
            saveArtifact(doc, DocumentArtifactType.CHUNK, chunkPayload, now);
        }

        List<Document> vectorDocs = new ArrayList<>();
        String displayName = name != null ? name : UNKNOWN_FILENAME_LABEL;
        String contentHash = KnowledgeChunkMetadataFactory.contentHashId(name, content, doc.getId());
        int totalVectors = computeTotalVectorCount(strategy, chunks);

        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            IndexingEmbeddingGuard.SafeEmbedText safe =
                    indexingEmbeddingGuard.prepareForEmbedding(content, embedMaxChars);
            Map<String, Object> vm =
                    KnowledgeChunkMetadataFactory.buildV2(
                            doc.getCorpusScope(),
                            doc.getId(),
                            doc.getProject().getId(),
                            doc.getConversation() != null ? doc.getConversation().getId() : null,
                            snapshot.getId(),
                            indexSigHex,
                            displayName,
                            0,
                            1,
                            contentHash,
                            safe.truncated());
            vectorDocs.add(new Document(safe.text(), vm));
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                IndexingEmbeddingGuard.SafeEmbedText safe =
                        indexingEmbeddingGuard.prepareForEmbedding(chunks.get(i), embedMaxChars);
                Map<String, Object> vm =
                        KnowledgeChunkMetadataFactory.buildV2(
                                doc.getCorpusScope(),
                                doc.getId(),
                                doc.getProject().getId(),
                                doc.getConversation() != null ? doc.getConversation().getId() : null,
                                snapshot.getId(),
                                indexSigHex,
                                displayName,
                                i,
                                totalVectors,
                                contentHash,
                                safe.truncated());
                vectorDocs.add(new Document(safe.text(), vm));
            }
        }

        if (strategy == MaterializationStrategy.HYBRID) {
            // Dense leg uses embed-safe text; full corpus text remains in CHUNK artifact for audit/lexical context.
            IndexingEmbeddingGuard.SafeEmbedText safeDoc =
                    indexingEmbeddingGuard.prepareForEmbedding(content, embedMaxChars);
            Map<String, Object> vmDoc =
                    KnowledgeChunkMetadataFactory.buildV2(
                            doc.getCorpusScope(),
                            doc.getId(),
                            doc.getProject().getId(),
                            doc.getConversation() != null ? doc.getConversation().getId() : null,
                            snapshot.getId(),
                            indexSigHex,
                            displayName,
                            chunks.size(),
                            totalVectors,
                            contentHash,
                            safeDoc.truncated());
            vectorDocs.add(new Document(safeDoc.text(), vmDoc));
        }

        if (!vectorDocs.isEmpty()) {
            String embeddingModelId =
                    IndexProfileJsonSupport.readEmbeddingModelId(snapshot.getIndexProfileJsonb())
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "embeddingModelId missing from knowledge_index_snapshot.index_profile_jsonb; cannot embed"));
            PgVectorStore vectorStore = vectorStoreRegistry.forEmbeddingModelId(embeddingModelId);
            addVectorsWithEmbedSafety(vectorStore, vectorDocs, effectiveChunkMaxChars);
            // Spring AI PgVectorStore does not automatically populate our optional `vector_store.project_id` column.
            // We keep `metadata.projectId` as-is and also set the column for correct project-scoped retrieval.
            UUID projectId = doc.getProject() != null ? doc.getProject().getId() : null;
            UUID snapshotId = snapshot != null ? snapshot.getId() : null;
            int updated =
                    backfillVectorStoreProjectIdForDocument(projectId, doc.getId(), snapshotId, indexSigHex);
            if (updated == 0) {
                // Retry without indexSignatureHash — Spring AI metadata shape can omit or alter the hash key.
                updated = backfillVectorStoreProjectIdForDocument(projectId, doc.getId(), snapshotId, null);
            }
            if (updated == 0) {
                throw new IllegalStateException(
                        "vector_store_project_id_backfill_failed projectId="
                                + projectId
                                + " projectDocumentId="
                                + doc.getId()
                                + " indexSnapshotId="
                                + snapshotId
                                + " vectorDocs="
                                + vectorDocs.size());
            }
            backfillVectorStoreChunkIndexForDocument(projectId, doc.getId(), snapshotId);
            log.info(
                    "vector_store_project_id_backfill projectId={} projectDocumentId={} indexSnapshotId={} vectorDocs={} updatedRows={}",
                    projectId,
                    doc.getId(),
                    snapshotId,
                    vectorDocs.size(),
                    updated);
        }

        Map<String, Object> indexPayload = new LinkedHashMap<>();
        indexPayload.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
        indexPayload.put("vectorChunkCount", vectorDocs.size());
        indexPayload.put("indexSignatureHash", indexSigHex);
        saveArtifact(doc, DocumentArtifactType.INDEX, indexPayload, now);
    }

    /**
     * Repairs `vector_store.project_id` (column) for rows created by this indexing run.
     * <p>
     * We intentionally do not modify metadata; the system already relies on `metadata.projectId`.
     * The column is required because several retrieval paths filter by `vector_store.project_id`.
     */
    private int backfillVectorStoreProjectIdForDocument(
            UUID projectId, UUID projectDocumentId, UUID indexSnapshotId, String indexSignatureHashHex) {
        if (projectId == null || projectDocumentId == null) {
            return 0;
        }
        // Match only rows from this document (and snapshot when available) to avoid accidentally touching
        // other projects that might share metadata keys.
        String sql =
                """
                UPDATE vector_store
                SET project_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE project_id IS NULL
                  AND metadata->>'projectId' = ?
                  AND metadata->>'projectDocumentId' = ?
                  AND (? IS NULL OR metadata->>'indexSnapshotId' = ?)
                  AND (? IS NULL OR metadata->>'indexSignatureHash' = ?)
                """;
        String pid = projectId.toString();
        String did = projectDocumentId.toString();
        String sid = indexSnapshotId != null ? indexSnapshotId.toString() : null;
        String sig = (indexSignatureHashHex != null && !indexSignatureHashHex.isBlank()) ? indexSignatureHashHex : null;
        return jdbcTemplate.update(sql, projectId, pid, did, sid, sid, sig, sig);
    }

    /**
     * Aligns {@code vector_store.chunk_index} (column) with {@code metadata.chunkIndex} for snapshot-bound ordering.
     */
    private void backfillVectorStoreChunkIndexForDocument(
            UUID projectId, UUID projectDocumentId, UUID indexSnapshotId) {
        if (projectId == null || projectDocumentId == null) {
            return;
        }
        String pid = projectId.toString();
        String did = projectDocumentId.toString();
        String sid = indexSnapshotId != null ? indexSnapshotId.toString() : null;
        jdbcTemplate.update(
                """
                UPDATE vector_store
                SET chunk_index = COALESCE((metadata->>'chunkIndex')::int, chunk_index, 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_id = ?
                  AND metadata->>'projectId' = ?
                  AND metadata->>'projectDocumentId' = ?
                  AND (? IS NULL OR metadata->>'indexSnapshotId' = ?)
                  AND metadata->>'chunkIndex' IS NOT NULL
                """,
                projectId,
                pid,
                did,
                sid,
                sid);
    }

    private void addVectorsWithEmbedSafety(
            PgVectorStore vectorStore, List<Document> vectorDocs, int profileChunkMaxChars) {
        int embedMaxChars = indexingEmbeddingGuard.effectiveEmbedMaxChars(profileChunkMaxChars);
        try {
            vectorStore.add(vectorDocs);
        } catch (RuntimeException e) {
            if (!indexingEmbeddingGuard.retryOnContextLength()
                    || !indexingEmbeddingGuard.isContextLengthFailure(e)) {
                throw new IllegalStateException(
                        "Vector embedding failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                        e);
            }
            int reducedMax = Math.max(64, embedMaxChars / 2);
            log.warn(
                    "Embedding context length exceeded (maxChars={}); retrying with max {} chars per fragment",
                    embedMaxChars,
                    reducedMax);
            List<Document> retried = rebuildDocumentsForReducedEmbedMax(vectorDocs, reducedMax);
            try {
                vectorStore.add(retried);
            } catch (RuntimeException retryEx) {
                throw new IllegalStateException(
                        "Vector embedding failed after context-length retry: "
                                + (retryEx.getMessage() != null ? retryEx.getMessage() : retryEx.getClass().getSimpleName()),
                        retryEx);
            }
        }
    }

    private List<Document> rebuildDocumentsForReducedEmbedMax(List<Document> vectorDocs, int reducedMax) {
        List<Document> retried = new ArrayList<>();
        for (Document doc : vectorDocs) {
            String raw = doc.getText() != null ? doc.getText() : "";
            List<String> parts =
                    raw.length() <= reducedMax
                            ? List.of(raw)
                            : projectDocumentIngestionService.splitContentIntoChunks(raw, reducedMax);
            int partIndex = 0;
            for (String part : parts) {
                IndexingEmbeddingGuard.SafeEmbedText safe =
                        indexingEmbeddingGuard.prepareForEmbedding(part, reducedMax);
                Map<String, Object> meta = new LinkedHashMap<>(doc.getMetadata());
                if (safe.truncated() || parts.size() > 1) {
                    meta.put("truncated", true);
                }
                if (parts.size() > 1) {
                    meta.put("chunkIndex", partIndex);
                    meta.put("totalChunks", parts.size());
                }
                retried.add(new Document(safe.text(), meta));
                partIndex++;
            }
        }
        return retried;
    }

    private static int computeTotalVectorCount(MaterializationStrategy strategy, List<String> chunks) {
        if (strategy == MaterializationStrategy.HYBRID) {
            return chunks.size() + 1;
        }
        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            return 1;
        }
        return chunks.size();
    }

    private byte[] loadBytes(KnowledgeDocumentEntity doc, Path tempFileOverride) throws IOException {
        if (tempFileOverride != null) {
            return Files.readAllBytes(tempFileOverride);
        }
        return readAllBytesFromStorage(doc);
    }

    private static String coalesce(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private String extractRequiredContent(byte[] bytes, String name, String contentType) {
        MultipartFile mf = new ByteArrayMultipartFile("file", name, contentType, bytes);
        String content = projectDocumentIngestionService.extractContent(mf);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("empty content");
        }
        return content;
    }

    public int computeChunkCountForDoc(UUID documentId) {
        return documentArtifactRepository.findByDocument_IdOrderByCreatedAtAsc(documentId).stream()
                .filter(a -> a.getArtifactType() == DocumentArtifactType.CHUNK)
                .mapToInt(
                        a -> {
                            Object n = a.getPayloadJsonb().get("chunkCount");
                            return n instanceof Number ? ((Number) n).intValue() : 0;
                        })
                .findFirst()
                .orElse(0);
    }

    private byte[] readAllBytesFromStorage(KnowledgeDocumentEntity doc) throws IOException {
        String uri = doc.getStorageUri();
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("missing storage URI");
        }
        try (InputStream in = binaryStoragePort.openStream(uri)) {
            return in.readAllBytes();
        }
    }

    private Map<String, Object> structuredSearchIndexPayload() {
        Map<String, Object> indexPayload = new LinkedHashMap<>();
        indexPayload.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
        indexPayload.put("vectorChunkCount", 0);
        indexPayload.put("vectorRefs", List.of());
        return indexPayload;
    }

    private Map<String, Object> buildMetadataPayload(MaterializationStrategy strategy, String content, String filename) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
        meta.put("fileName", filename != null ? filename : "");
        meta.put("textLength", content.length());
        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            Map<String, Object> proj = new LinkedHashMap<>();
            proj.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
            proj.put("sourceFile", filename != null ? filename : "");
            proj.put("charLength", content.length());
            meta.put("structuredSearchProjection", proj);
        }
        return meta;
    }

    private void saveArtifact(
            KnowledgeDocumentEntity doc, DocumentArtifactType type, Map<String, Object> payload, Instant createdAt) {
        String hash = ArtifactPayloadHasher.sha256Hex(payload);
        DocumentArtifactEntity e = DocumentArtifactEntity.newRow();
        e.setDocument(doc);
        e.setArtifactType(type);
        e.setPayloadJsonb(payload);
        e.setContentHash(hash);
        e.setCreatedAt(createdAt);
        documentArtifactRepository.save(e);
    }
}
