package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.infrastructure.persistence.DocumentArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class KnowledgeIndexingServiceTest {

    @TempDir Path tempDir;

    @Test
    void processDocument_structuredSearch_savesParsedMetadataIndex_andSkipsVectorsAndChunks() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
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
        verify(artifactRepo, org.mockito.Mockito.times(3)).save(captor.capture());

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
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
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
        verify(artifactRepo, org.mockito.Mockito.times(3)).save(captor.capture());
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
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
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
        verify(artifactRepo, org.mockito.Mockito.times(4)).save(captor.capture());
        List<DocumentArtifactType> types = captor.getAllValues().stream().map(DocumentArtifactEntity::getArtifactType).toList();
        assertThat(types)
                .containsExactly(DocumentArtifactType.PARSED, DocumentArtifactType.METADATA, DocumentArtifactType.CHUNK, DocumentArtifactType.INDEX);

        Map<String, Object> indexPayload = captor.getAllValues().getLast().getPayloadJsonb();
        assertThat(indexPayload.get("vectorChunkCount")).isEqualTo(4);

        verify(vectorStore).add(any());
    }

    @Test
    void processDocument_throwsOnEmptyContent() throws Exception {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

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
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        KnowledgeIndexSnapshotEntity snapshot = mock(KnowledgeIndexSnapshotEntity.class);
        when(snapshot.getId()).thenReturn(UUID.randomUUID());
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
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

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
        var ingestionService = mock(com.uniovi.rag.service.document.ProjectDocumentIngestionService.class);
        BinaryStoragePort storagePort = mock(BinaryStoragePort.class);
        DocumentArtifactRepository artifactRepo = mock(DocumentArtifactRepository.class);

        KnowledgeIndexingService sut = new KnowledgeIndexingService(vectorStore, ingestionService, storagePort, artifactRepo);

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
}

