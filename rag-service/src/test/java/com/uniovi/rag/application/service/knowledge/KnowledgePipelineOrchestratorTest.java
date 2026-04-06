package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgePipelineOrchestratorTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ProjectDocumentIngestionService projectDocumentIngestionService;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private BinaryStoragePort binaryStoragePort;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexingService knowledgeIndexingService;

    @Test
    void previewSnapshotSignatureHex_isDeterministicForEmptyReadyCorpus() {
        UUID projectId = UUID.randomUUID();
        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(eq(projectId), eq(CorpusScope.PROJECT_SHARED)))
                .thenReturn(List.of());

        KnowledgePipelineOrchestrator orchestrator =
                new KnowledgePipelineOrchestrator(
                        jdbcTemplate,
                        projectDocumentIngestionService,
                        knowledgeDocumentRepository,
                        binaryStoragePort,
                        knowledgeSnapshotService,
                        knowledgeIndexingService,
                        400,
                        "mxbai-embed-large",
                        "CHUNK_LEVEL");

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
                        projectDocumentIngestionService,
                        knowledgeDocumentRepository,
                        binaryStoragePort,
                        knowledgeSnapshotService,
                        knowledgeIndexingService,
                        400,
                        "mxbai-embed-large",
                        "CHUNK_LEVEL");

        assertThat(orchestrator.scopeHasRequiresReindex(projectId, CorpusScope.PROJECT_SHARED, null)).isFalse();
    }
}
