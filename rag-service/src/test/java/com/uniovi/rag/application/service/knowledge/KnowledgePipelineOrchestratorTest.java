package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;

@ExtendWith(MockitoExtension.class)
class KnowledgePipelineOrchestratorTest {

    private static IndexingEmbeddingGuard defaultEmbeddingGuard() {
        return new IndexingEmbeddingGuard(new RagIndexingEmbeddingProperties(2048, 400, true, 0.85));
    }

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private BinaryStoragePort binaryStoragePort;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexingService knowledgeIndexingService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService;
    @Mock private PlatformTransactionManager transactionManager;

    private EmbeddingIndexCompatibilityService compatibilityForTests() {
        lenient()
                .when(embeddingIndexCompatibilityService.enrichIndexProfile(any()))
                .thenAnswer(
                        inv -> {
                            Map<String, Object> base = inv.getArgument(0);
                            Map<String, Object> enriched = new java.util.LinkedHashMap<>(base != null ? base : Map.of());
                            enriched.putIfAbsent("embeddingModelId", "mxbai-embed-large");
                            enriched.putIfAbsent("embeddingProvider", "OLLAMA_NATIVE");
                            return enriched;
                        });
        return embeddingIndexCompatibilityService;
    }

    @Test
    void previewSnapshotSignatureHex_isDeterministicForEmptyReadyCorpus() {
        UUID projectId = UUID.randomUUID();
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(eq(projectId), eq(CorpusScope.PROJECT_SHARED)))
                .thenReturn(List.of());
        when(projectIndexProfileService.ensureDefault(projectId))
                .thenReturn(
                        new ProjectIndexProfile(
                                projectId,
                                MaterializationStrategy.CHUNK_LEVEL,
                                false,
                                null,
                                "mxbai-embed-large",
                                400,
                                null,
                                "hash",
                                Instant.now(),
                                Instant.now()));

        KnowledgePipelineOrchestrator orchestrator =
                new KnowledgePipelineOrchestrator(
                        jdbcTemplate,
                        knowledgeDocumentRepository,
                        binaryStoragePort,
                        knowledgeSnapshotService,
                        knowledgeIndexingService,
                        projectIndexProfileService,
                        mock(LabIndexProfileOverrideFactory.class),
                        embeddingSpaceGuard,
                        defaultEmbeddingGuard(),
                        knowledgeIndexSnapshotRepository,
                        compatibilityForTests(),
                        transactionManager,
                        null);

        String a = orchestrator.previewSnapshotSignatureHex(projectId, CorpusScope.PROJECT_SHARED, null);
        String b = orchestrator.previewSnapshotSignatureHex(projectId, CorpusScope.PROJECT_SHARED, null);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    void scopeHasRequiresReindex_falseWhenNoDocuments() {
        UUID projectId = UUID.randomUUID();
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(eq(projectId), eq(CorpusScope.PROJECT_SHARED)))
                .thenReturn(List.of());
        KnowledgePipelineOrchestrator orchestrator =
                new KnowledgePipelineOrchestrator(
                        jdbcTemplate,
                        knowledgeDocumentRepository,
                        binaryStoragePort,
                        knowledgeSnapshotService,
                        knowledgeIndexingService,
                        projectIndexProfileService,
                        mock(LabIndexProfileOverrideFactory.class),
                        embeddingSpaceGuard,
                        defaultEmbeddingGuard(),
                        knowledgeIndexSnapshotRepository,
                        compatibilityForTests(),
                        transactionManager,
                        null);

        assertThat(orchestrator.scopeHasRequiresReindex(projectId, CorpusScope.PROJECT_SHARED, null)).isFalse();
    }

    @Test
    void ingestFromTempFile_marksDocumentErrorWhenBinaryStoreThrows() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();

        KnowledgeDocumentEntity row = mock(KnowledgeDocumentEntity.class, RETURNS_DEEP_STUBS);
        when(knowledgeDocumentRepository.findById(docId)).thenReturn(Optional.of(row));
        when(row.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);

        when(binaryStoragePort.store(any(), anyLong(), any()))
                .thenThrow(new IOException("store failed"));

        KnowledgePipelineOrchestrator orchestrator =
                new KnowledgePipelineOrchestrator(
                        jdbcTemplate,
                        knowledgeDocumentRepository,
                        binaryStoragePort,
                        knowledgeSnapshotService,
                        knowledgeIndexingService,
                        projectIndexProfileService,
                        mock(LabIndexProfileOverrideFactory.class),
                        embeddingSpaceGuard,
                        defaultEmbeddingGuard(),
                        knowledgeIndexSnapshotRepository,
                        compatibilityForTests(),
                        transactionManager,
                        null);

        Path tmp = Files.createTempFile("rag-test-", ".txt");
        Files.writeString(tmp, "x");
        orchestrator.ingestFromTempFile(projectId, docId, tmp, "f.txt", "text/plain", snapId, "hash");

        verify(row).setStatus(ProjectDocumentStatus.ERROR);
        verify(row).setErrorMessage(contains("store failed"));
        verify(knowledgeDocumentRepository).save(row);
    }
}
