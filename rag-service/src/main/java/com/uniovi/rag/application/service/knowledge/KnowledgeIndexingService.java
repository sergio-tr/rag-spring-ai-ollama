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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline stages for one workspace document; invoked only from {@link KnowledgePipelineOrchestrator}.
 */
@Service
public class KnowledgeIndexingService {

    private final PgVectorStore vectorStore;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final BinaryStoragePort binaryStoragePort;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final int chunkMaxChars;

    public KnowledgeIndexingService(
            PgVectorStore vectorStore,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            BinaryStoragePort binaryStoragePort,
            DocumentArtifactRepository documentArtifactRepository,
            @Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
        this.vectorStore = vectorStore;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.binaryStoragePort = binaryStoragePort;
        this.documentArtifactRepository = documentArtifactRepository;
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
    }

    /**
     * Runs parse → metadata → optional chunk → embed → index artifact + vectors per §8 matrix.
     */
    public void processDocument(
            KnowledgeDocumentEntity doc,
            Path tempFileOverride,
            String originalFilename,
            String contentType,
            KnowledgeIndexSnapshotEntity snapshot,
            String indexSigHex,
            MaterializationStrategy strategy,
            int effectiveChunkMaxChars)
            throws IOException {
        byte[] bytes =
                tempFileOverride != null
                        ? Files.readAllBytes(tempFileOverride)
                        : readAllBytesFromStorage(doc);
        String name = doc.getFileName() != null ? doc.getFileName() : originalFilename;
        String ct = doc.getMimeType() != null ? doc.getMimeType() : contentType;
        MultipartFile mf = new ByteArrayMultipartFile("file", name, ct, bytes);
        String content = projectDocumentIngestionService.extractContent(mf);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("empty content");
        }

        Instant now = Instant.now();
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("schemaVersion", 1);
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
            chunkPayload.put("schemaVersion", 1);
            chunkPayload.put("chunkCount", chunks.size());
            chunkPayload.put("chunks", chunks);
            saveArtifact(doc, DocumentArtifactType.CHUNK, chunkPayload, now);
        }

        List<Document> vectorDocs = new ArrayList<>();
        String legacyHash = KnowledgeChunkMetadataFactory.legacyContentHashId(name, content, doc.getId());
        int totalVectors =
                strategy == MaterializationStrategy.HYBRID
                        ? chunks.size() + 1
                        : strategy == MaterializationStrategy.DOCUMENT_LEVEL
                                ? 1
                                : chunks.size();

        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            Map<String, Object> vm =
                    KnowledgeChunkMetadataFactory.buildV2(
                            doc.getCorpusScope(),
                            doc.getId(),
                            doc.getProject().getId(),
                            doc.getConversation() != null ? doc.getConversation().getId() : null,
                            snapshot.getId(),
                            indexSigHex,
                            name != null ? name : "unknown",
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
                                name != null ? name : "unknown",
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
                            name != null ? name : "unknown",
                            chunks.size(),
                            totalVectors,
                            legacyHash);
            vectorDocs.add(new Document(docSlice, vmDoc));
        }

        if (!vectorDocs.isEmpty()) {
            vectorStore.add(vectorDocs);
        }

        Map<String, Object> indexPayload = new LinkedHashMap<>();
        indexPayload.put("schemaVersion", 1);
        indexPayload.put("vectorChunkCount", vectorDocs.size());
        indexPayload.put("indexSignatureHash", indexSigHex);
        saveArtifact(doc, DocumentArtifactType.INDEX, indexPayload, now);
    }

    public int computeChunkCountForDoc(java.util.UUID documentId) {
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
        indexPayload.put("schemaVersion", 1);
        indexPayload.put("vectorChunkCount", 0);
        indexPayload.put("vectorRefs", List.of());
        return indexPayload;
    }

    private Map<String, Object> buildMetadataPayload(MaterializationStrategy strategy, String content, String filename) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", 1);
        meta.put("fileName", filename != null ? filename : "");
        meta.put("textLength", content.length());
        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            Map<String, Object> proj = new LinkedHashMap<>();
            proj.put("schemaVersion", 1);
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
