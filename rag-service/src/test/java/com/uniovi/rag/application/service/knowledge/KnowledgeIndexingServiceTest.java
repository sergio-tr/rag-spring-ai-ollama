package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.persistence.DocumentArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexingServiceTest {

    @TempDir Path tempDir;

    private static IndexingEmbeddingGuard defaultEmbeddingGuard() {
        return new IndexingEmbeddingGuard(new RagIndexingEmbeddingProperties(2048, 400, true, 0.85));
    }

    private static JdbcTemplate jdbcTemplateReturningUpdateRows(int rows) {
        Answer<Object> answer =
                (InvocationOnMock inv) -> "update".equals(inv.getMethod().getName()) ? rows : 0;
        return mock(JdbcTemplate.class, answer);
    }

    private static EmbeddingIndexCompatibilityService noopCompatibilityService() {
        EmbeddingIndexCompatibilityService svc = mock(EmbeddingIndexCompatibilityService.class);
        lenient().doNothing().when(svc).assertIndexingCompatible(any());
        lenient()
                .when(svc.enrichIndexProfile(any()))
                .thenAnswer(
                        inv -> {
                            Map<String, Object> base = inv.getArgument(0);
                            Map<String, Object> enriched = new java.util.LinkedHashMap<>(base != null ? base : Map.of());
                            enriched.putIfAbsent(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, "mxbai-embed-large");
                            enriched.putIfAbsent(
                                    IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, LlmProvider.OLLAMA_NATIVE.name());
                            return enriched;
                        });
        return svc;
    }

    private static KnowledgeIndexingService sutWithRegistry(
            PgVectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            ProjectDocumentIngestionService ingestionService,
            BinaryStoragePort storagePort,
            DocumentArtifactRepository artifactRepo,
            MetadataMinuteDocumentService metadataMinuteDocumentService) {
        PgVectorStoreRegistry reg = mock(PgVectorStoreRegistry.class);
        lenient().when(reg.forEmbeddingModelId(anyString())).thenReturn(vectorStore);
        return new KnowledgeIndexingService(
                reg,
                jdbcTemplate,
                ingestionService,
                storagePort,
                artifactRepo,
                defaultEmbeddingGuard(),
                metadataMinuteDocumentService,
                noopCompatibilityService());
    }

    private static MetadataMinuteDocumentService metadataServiceReturningEmpty() {
        MetadataMinuteDocumentService svc = mock(MetadataMinuteDocumentService.class);
        lenient()
                .when(svc.tryExtractDeterministicMetadataForIndexing(any(), any(), any()))
                .thenReturn(Optional.empty());
        return svc;
    }

    @Test
    void processDocument_structuredSearch_savesParsedMetadataIndex_andSkipsVectorsAndChunks() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");
        when(doc.getMimeType()).thenReturn("text/plain");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("hello world");

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "ignored.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.STRUCTURED_SEARCH,
                        123));

        ArgumentCaptor<DocumentArtifactEntity> captor = ArgumentCaptor.forClass(DocumentArtifactEntity.class);
        verify(artifactRepo, Mockito.times(3)).save(captor.capture());

        List<DocumentArtifactType> types = captor.getAllValues().stream().map(DocumentArtifactEntity::getArtifactType).toList();
        assertThat(types)
                .containsExactly(DocumentArtifactType.PARSED, DocumentArtifactType.METADATA, DocumentArtifactType.INDEX);

        Map<String, Object> indexPayload = captor.getAllValues().getLast().getPayloadJsonb();
        assertThat(indexPayload.get("vectorChunkCount")).isEqualTo(0);
        assertThat(indexPayload.get("vectorRefs")).isEqualTo(List.of());

        verify(vectorStore, never()).add(any());
    }

    @Test
    void processDocument_documentLevel_indexesSingleVector_andDoesNotPersistChunkArtifact() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");
        when(doc.getMimeType()).thenReturn("text/plain");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("hello world");

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "ignored.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.DOCUMENT_LEVEL,
                        100));

        ArgumentCaptor<DocumentArtifactEntity> captor = ArgumentCaptor.forClass(DocumentArtifactEntity.class);
        verify(artifactRepo, Mockito.times(3)).save(captor.capture());
        List<DocumentArtifactType> types = captor.getAllValues().stream().map(DocumentArtifactEntity::getArtifactType).toList();
        assertThat(types)
                .containsExactly(DocumentArtifactType.PARSED, DocumentArtifactType.METADATA, DocumentArtifactType.INDEX);

        Map<String, Object> indexPayload = captor.getAllValues().getLast().getPayloadJsonb();
        assertThat(indexPayload.get("vectorChunkCount")).isEqualTo(1);

        verify(vectorStore).add(any());
    }

    @Test
    void processDocument_hybrid_savesChunkArtifact_andIndexesChunksPlusDocSlice() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");
        when(doc.getMimeType()).thenReturn("text/plain");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("hello world");
        when(ingestionService.splitContentIntoChunks("hello world", 5)).thenReturn(List.of("a", "b", "c"));

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "ignored.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.HYBRID,
                        5));

        ArgumentCaptor<DocumentArtifactEntity> captor = ArgumentCaptor.forClass(DocumentArtifactEntity.class);
        verify(artifactRepo, Mockito.times(4)).save(captor.capture());
        List<DocumentArtifactType> types = captor.getAllValues().stream().map(DocumentArtifactEntity::getArtifactType).toList();
        assertThat(types)
                .containsExactly(DocumentArtifactType.PARSED, DocumentArtifactType.METADATA, DocumentArtifactType.CHUNK, DocumentArtifactType.INDEX);

        Map<String, Object> indexPayload = captor.getAllValues().getLast().getPayloadJsonb();
        assertThat(indexPayload.get("vectorChunkCount")).isEqualTo(4);

        verify(vectorStore).add(any());
    }

    @Test
    void processDocument_throwsWhenProjectIdBackfillUpdatesZeroRows() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(0);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");
        when(doc.getMimeType()).thenReturn("text/plain");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("hello world");

        assertThatThrownBy(
                        () ->
                                sut.processDocument(
                                        new KnowledgeDocumentIndexingRequest(
                                                doc,
                                                tempFile,
                                                "ignored.txt",
                                                "text/plain",
                                                snapshot,
                                                "abc123",
                                                MaterializationStrategy.DOCUMENT_LEVEL,
                                                100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector_store_project_id_backfill_failed");
    }

    @Test
    void processDocument_vectorMetadataIncludesIndexSnapshotId() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        UUID snapshotId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(snapshotId);
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(documentId);
        when(doc.getProject().getId()).thenReturn(projectId);
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");
        when(doc.getMimeType()).thenReturn("text/plain");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("hello world");

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "ignored.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.DOCUMENT_LEVEL,
                        100));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docs.capture());
        assertThat(docs.getValue()).isNotEmpty();
        assertThat(docs.getValue().getFirst().getMetadata())
                .containsEntry("indexSnapshotId", snapshotId.toString())
                .containsEntry("projectId", projectId.toString())
                .containsEntry("projectDocumentId", documentId.toString());
    }

    @Test
    void processDocument_acta_mergesStructuredMetadataIntoEveryVectorChunk() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        MetadataMinuteDocumentService metadataService = new MetadataMinuteDocumentService(
                mock(PgVectorStore.class), mock(ChatClient.class), mock(JdbcTemplate.class), 400);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataService);

        UUID snapshotId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(snapshotId);
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("ACTA 1.pdf");
        when(doc.getMimeType()).thenReturn("application/pdf");

        String actaContent =
                Files.readString(
                        Path.of("src/test/resources/acta-fixtures/acta-1.txt"), StandardCharsets.UTF_8);
        Path tempFile = tempDir.resolve("acta1.txt");
        Files.writeString(tempFile, actaContent, StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn(actaContent);

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "ACTA 1.pdf",
                        "application/pdf",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.CHUNK_LEVEL,
                        400));

        ArgumentCaptor<DocumentArtifactEntity> artifactCaptor = ArgumentCaptor.forClass(DocumentArtifactEntity.class);
        verify(artifactRepo, Mockito.atLeast(3)).save(artifactCaptor.capture());
        Map<String, Object> metadataPayload =
                artifactCaptor.getAllValues().stream()
                        .filter(a -> a.getArtifactType() == DocumentArtifactType.METADATA)
                        .findFirst()
                        .orElseThrow()
                        .getPayloadJsonb();
        assertThat(metadataPayload).containsKey("structuredActa");
        assertThat(metadataPayload).containsKey("fieldPresence");
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) metadataPayload.get("structuredActa");
        assertThat(structured.get("president")).isEqualTo("Juan Pérez Gutiérrez");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docs.capture());
        for (Document vectorDoc : docs.getValue()) {
            assertThat(vectorDoc.getMetadata())
                    .containsEntry("president", "Juan Pérez Gutiérrez")
                    .containsEntry("date_iso", "2025-02-24")
                    .containsEntry("documentTitle", "ACTA 1.pdf")
                    .containsEntry("actaDate", "2025-02-24")
                    .containsKey("fieldPresence")
                    .containsKey("sectionType");
            assertThat(vectorDoc.getText()).startsWith("Acta 2025-02-24");
        }
        assertThat(docs.getValue().stream().map(d -> d.getMetadata().get("sectionType")).distinct().count())
                .isGreaterThan(1);
    }

    @Test
    void processDocument_throwsOnEmptyContent() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "hello world", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("");

        assertThatThrownBy(
                        () ->
                                sut.processDocument(
                                        new KnowledgeDocumentIndexingRequest(
                                                doc,
                                                tempFile,
                                                "ignored.txt",
                                                "text/plain",
                                                snapshot,
                                                "abc123",
                                                MaterializationStrategy.DOCUMENT_LEVEL,
                                                100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void processDocument_readsFromStorage_whenNoTempFileOverride() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getStorageUri()).thenReturn("mem://doc");

        InputStream in = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        when(storagePort.openStream("mem://doc")).thenReturn(in);
        when(ingestionService.extractContent(any())).thenReturn("hello");

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        null,
                        "name.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.DOCUMENT_LEVEL,
                        100));

        verify(storagePort).openStream("mem://doc");
        verify(vectorStore).add(any());
    }

    @Test
    void computeChunkCountForDoc_returnsFirstChunkCountFromArtifacts() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        UUID docId = UUID.randomUUID();
        DocumentArtifactEntity parsed = mock(DocumentArtifactEntity.class);
        when(parsed.getArtifactType()).thenReturn(DocumentArtifactType.PARSED);

        DocumentArtifactEntity chunk = mock(DocumentArtifactEntity.class);
        when(chunk.getArtifactType()).thenReturn(DocumentArtifactType.CHUNK);
        when(chunk.getPayloadJsonb()).thenReturn(Map.of("chunkCount", 7));

        when(artifactRepo.findByDocument_IdOrderByCreatedAtAsc(docId)).thenReturn(List.of(parsed, chunk));

        assertThat(sut.computeChunkCountForDoc(docId)).isEqualTo(7);
    }

    @Test
    void processDocument_throwsWhenStorageUriMissing() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut =
                sutWithRegistry(vectorStore, jdbcTemplate, ingestionService, storagePort, artifactRepo, metadataServiceReturningEmpty());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getStorageUri()).thenReturn("  ");

        assertThatThrownBy(
                        () ->
                                sut.processDocument(
                                        new KnowledgeDocumentIndexingRequest(
                                                doc,
                                                null,
                                                "name.txt",
                                                "text/plain",
                                                mock(KnowledgeIndexSnapshotEntity.class),
                                                "abc123",
                                                MaterializationStrategy.DOCUMENT_LEVEL,
                                                100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing storage URI");
    }
    @Test
    void processDocument_documentLevel_truncatesBeforeEmbed() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        IndexingEmbeddingGuard guard = new IndexingEmbeddingGuard(
                new RagIndexingEmbeddingProperties(200, 400, false, 0.85));
        PgVectorStoreRegistry reg = mock(PgVectorStoreRegistry.class);
        when(reg.forEmbeddingModelId(anyString())).thenReturn(vectorStore);
        KnowledgeIndexingService sut = new KnowledgeIndexingService(
                reg, jdbcTemplate, ingestionService, storagePort, artifactRepo, guard, metadataServiceReturningEmpty(), noopCompatibilityService());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("long.txt");

        String longContent = "x".repeat(5000);
        Path tempFile = tempDir.resolve("long.txt");
        Files.writeString(tempFile, longContent, StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn(longContent);

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "long.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.DOCUMENT_LEVEL,
                        400));

        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docsCaptor.capture());
        List<Document> docs = docsCaptor.getValue();
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getText().length()).isLessThanOrEqualTo(guard.effectiveEmbedMaxChars(400));
        assertThat(docs.getFirst().getMetadata().get("truncated")).isEqualTo(true);
        assertThat(docs.getFirst().getMetadata().get("sourceDocumentId")).isEqualTo(doc.getId().toString());
    }

    @Test
    void processDocument_retriesWithSmallerChunksOnContextLengthError() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningUpdateRows(1);
        var ingestionService = mock(ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        IndexingEmbeddingGuard guard = new IndexingEmbeddingGuard(
                new RagIndexingEmbeddingProperties(2048, 400, true, 0.85));
        PgVectorStoreRegistry reg = mock(PgVectorStoreRegistry.class);
        when(reg.forEmbeddingModelId(anyString())).thenReturn(vectorStore);
        KnowledgeIndexingService sut = new KnowledgeIndexingService(
                reg, jdbcTemplate, ingestionService, storagePort, artifactRepo, guard, metadataServiceReturningEmpty(), noopCompatibilityService());

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
        when(snapshot.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getProject().getId()).thenReturn(UUID.randomUUID());
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(doc.getConversation()).thenReturn(null);
        when(doc.getFileName()).thenReturn("doc.txt");

        Path tempFile = tempDir.resolve("doc.txt");
        Files.writeString(tempFile, "short", StandardCharsets.UTF_8);
        when(ingestionService.extractContent(any())).thenReturn("short");
        when(ingestionService.splitContentIntoChunks("short", 400)).thenReturn(List.of("short"));

        RuntimeException contextErr =
                new RuntimeException("[400] input length exceeds the context length");
        Mockito.doThrow(contextErr).doNothing().when(vectorStore).add(any());

        sut.processDocument(
                new KnowledgeDocumentIndexingRequest(
                        doc,
                        tempFile,
                        "doc.txt",
                        "text/plain",
                        snapshot,
                        "abc123",
                        MaterializationStrategy.CHUNK_LEVEL,
                        400));

        verify(vectorStore, Mockito.times(2)).add(any());
    }

}

