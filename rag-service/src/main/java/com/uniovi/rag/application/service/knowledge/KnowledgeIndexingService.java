package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.infrastructure.persistence.DocumentArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.service.document.ByteArrayMultipartFile;
import com.uniovi.rag.service.document.KnowledgeChunkMetadataFactory;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pipeline stages for one workspace document; invoked only from {@link KnowledgePipelineOrchestrator}.
 */
@Service
public class KnowledgeIndexingService {

    private static final String JSON_KEY_SCHEMA_VERSION = "schemaVersion";
    private static final int ARTIFACT_SCHEMA_VERSION = 1;
    private static final String UNKNOWN_FILENAME_LABEL = "unknown";

    private final PgVectorStore vectorStore;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final BinaryStoragePort binaryStoragePort;
    private final DocumentArtifactRepository documentArtifactRepository;

    public KnowledgeIndexingService(
            PgVectorStore vectorStore,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            BinaryStoragePort binaryStoragePort,
            DocumentArtifactRepository documentArtifactRepository) {
        this.vectorStore = vectorStore;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.binaryStoragePort = binaryStoragePort;
        this.documentArtifactRepository = documentArtifactRepository;
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
        String legacyHash = KnowledgeChunkMetadataFactory.legacyContentHashId(name, content, doc.getId());
        int totalVectors = computeTotalVectorCount(strategy, chunks);

        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
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
                            legacyHash);
            vectorDocs.add(new Document(chunks.getFirst(), vm));
        } else {
            for (int i = 0; i < chunks.size(); i++) {
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
                                legacyHash);
                vectorDocs.add(new Document(chunks.get(i), vm));
            }
        }

        if (strategy == MaterializationStrategy.HYBRID) {
            String docSlice =
                    content.length() > effectiveChunkMaxChars * 4
                            ? content.substring(0, effectiveChunkMaxChars * 4)
                            : content;
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
                            legacyHash);
            vectorDocs.add(new Document(docSlice, vmDoc));
        }

        if (!vectorDocs.isEmpty()) {
            vectorStore.add(vectorDocs);
        }

        Map<String, Object> indexPayload = new LinkedHashMap<>();
        indexPayload.put(JSON_KEY_SCHEMA_VERSION, ARTIFACT_SCHEMA_VERSION);
        indexPayload.put("vectorChunkCount", vectorDocs.size());
        indexPayload.put("indexSignatureHash", indexSigHex);
        saveArtifact(doc, DocumentArtifactType.INDEX, indexPayload, now);
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
