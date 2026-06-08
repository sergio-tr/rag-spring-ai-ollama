package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorpusAvailabilityGateTest {

    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private NamedParameterJdbcTemplate jdbc;

    private final UUID userId = UUID.randomUUID();
    private final UUID corpusId = UUID.randomUUID();
    private final UUID indexProjectId = UUID.randomUUID();

    private CorpusAvailabilityGate gate;

    @BeforeEach
    void setUp() {
        gate = new CorpusAvailabilityGate(evaluationCorpusApplicationService, jdbc);
    }

    @Test
    void noCorpusSelectedWhenCorpusIdNull() {
        CorpusAvailabilityGate.Result result = gate.evaluate(userId, null, List.of(UUID.randomUUID()));
        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_CORPUS_SELECTED);
        verify(evaluationCorpusApplicationService, never()).requireContext(any(), any());
    }

    @Test
    void noDocumentsWhenCorpusEmpty() {
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(), List.of()));

        CorpusAvailabilityGate.Result result = gate.evaluate(userId, corpusId, List.of(UUID.randomUUID()));
        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_DOCUMENTS);
    }

    @Test
    void noReadyDocumentsWhenNoneReady() {
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.INGESTING, null);
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));

        CorpusAvailabilityGate.Result result = gate.evaluate(userId, corpusId, List.of(UUID.randomUUID()));
        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.NO_READY_DOCUMENTS);
    }

    @Test
    void reindexRequiredWhenSnapshotMissing() {
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));

        CorpusAvailabilityGate.Result result = gate.evaluate(userId, corpusId, List.of());
        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.REINDEX_REQUIRED);
    }

    @Test
    void snapshotIncompatibleWhenVectorRowsMissing() {
        UUID snapshotId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));
        when(jdbc.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        CorpusAvailabilityGate.Result result = gate.evaluate(userId, corpusId, List.of(snapshotId));
        assertThat(result.satisfied()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(CorpusAvailabilityGate.SNAPSHOT_VECTOR_ROWS_MISSING);
    }

    @Test
    void evaluateBindsSnapshotIdsAsUuidsForVectorCountQuery() {
        UUID snapshotId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.queryForObject(any(String.class), params.capture(), eq(Long.class))).thenReturn(2L);

        gate.evaluate(userId, corpusId, List.of(snapshotId));

        @SuppressWarnings("unchecked")
        List<UUID> bound = (List<UUID>) params.getValue().getValue("snapshotIds");
        assertThat(bound).containsExactly(snapshotId);
    }

    @Test
    void satisfiedWhenReadyDocsAndVectorRowsExist() {
        UUID snapshotId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));
        when(jdbc.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class))).thenReturn(3L);

        CorpusAvailabilityGate.Result result = gate.evaluate(userId, corpusId, List.of(snapshotId));
        assertThat(result.satisfied()).isTrue();
        assertThat(result.vectorChunkRowCount()).isEqualTo(3L);
    }

    @Test
    void probeIncludesSkippedReasonWhenUnavailable() {
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(documentId, ProjectDocumentStatus.READY, "s3://doc");
        when(evaluationCorpusApplicationService.requireContext(userId, corpusId))
                .thenReturn(new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                        corpusId, indexProjectId, List.of(documentId), List.of(doc)));

        var metrics = gate.probe(userId, corpusId, List.of());
        assertThat(metrics)
                .containsEntry("corpusRequired", true)
                .containsEntry("corpusAvailable", false)
                .containsEntry("skippedReasonCode", CorpusAvailabilityGate.REINDEX_REQUIRED);
    }

    private static KnowledgeDocumentEntity document(UUID id, ProjectDocumentStatus status, String storageUri) {
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        lenient().when(doc.getId()).thenReturn(id);
        lenient().when(doc.getStatus()).thenReturn(status);
        lenient().when(doc.getStorageUri()).thenReturn(storageUri);
        return doc;
    }
}
