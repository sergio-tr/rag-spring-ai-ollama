package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CorpusAvailabilityGateTest {

    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CorpusAvailabilityGate gate = new CorpusAvailabilityGate(documents, jdbc);

    @Test
    void noProjectReturnsNoCorpusSelected() {
        CorpusAvailabilityGate.Result result = gate.evaluate(null, List.of(UUID.randomUUID()));

        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_CORPUS_SELECTED);
        verify(documents, never()).findByProject_IdAndCorpusScopeOrderByIdAsc(any(), any());
    }

    @Test
    void noDocumentsReturnsNoDocuments() {
        UUID projectId = UUID.randomUUID();
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of());

        CorpusAvailabilityGate.Result result = gate.evaluate(projectId, List.of(UUID.randomUUID()));

        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_DOCUMENTS);
        assertThat(result.projectDocumentCount()).isZero();
    }

    @Test
    void documentsButNoneReadyReturnsNoReadyDocuments() {
        UUID projectId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(UUID.randomUUID(), ProjectDocumentStatus.INGESTING, null);
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));

        CorpusAvailabilityGate.Result result = gate.evaluate(projectId, List.of(UUID.randomUUID()));

        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_READY_DOCUMENTS);
        assertThat(result.projectDocumentCount()).isEqualTo(1);
        assertThat(result.readySharedDocsWithUriCount()).isZero();
    }

    @Test
    void readyDocumentsWithoutSnapshotReturnsReindexRequiredNotNoDocuments() {
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));

        CorpusAvailabilityGate.Result result = gate.evaluate(projectId, List.of());

        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.REINDEX_REQUIRED);
        assertThat(result.readyDocumentIds()).containsExactly(documentId);
    }

    @Test
    void readyDocumentsWithSnapshotButNoRowsReturnsSnapshotIncompatible() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(UUID.randomUUID(), ProjectDocumentStatus.READY, "s3://doc");
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));
        when(jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        CorpusAvailabilityGate.Result result = gate.evaluate(projectId, List.of(snapshotId));

        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("SNAPSHOT_INCOMPATIBLE");
    }

    @Test
    void probeWithoutSnapshotExplainsEmptySelectedSnapshotIds() {
        UUID projectId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(UUID.randomUUID(), ProjectDocumentStatus.READY, "s3://doc");
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));

        assertThat(gate.probe(projectId, List.of()))
                .containsEntry("selectedSnapshotIds", List.of())
                .containsKey("snapshotSelectionReason")
                .containsEntry("skippedReasonCode", CorpusAvailabilityGate.REINDEX_REQUIRED);
    }

    @Test
    void readyDocumentsWithSnapshotRowsSatisfyGateAndProbeExportsCorpusIds() {
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(documents.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));
        when(jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);

        CorpusAvailabilityGate.Result result = gate.evaluate(projectId, List.of(snapshotId));

        assertThat(result.satisfied()).isTrue();
        assertThat(result.reasonCode()).isNull();
        assertThat(result.vectorChunkRowCount()).isEqualTo(7L);
        assertThat(gate.probe(projectId, List.of(snapshotId)))
                .containsEntry("corpusAvailable", true)
                .containsEntry("projectDocumentCount", 1);
    }

    private static KnowledgeDocumentEntity document(UUID id, ProjectDocumentStatus status, String storageUri) {
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(id);
        when(doc.getStatus()).thenReturn(status);
        when(doc.getStorageUri()).thenReturn(storageUri);
        return doc;
    }
}
