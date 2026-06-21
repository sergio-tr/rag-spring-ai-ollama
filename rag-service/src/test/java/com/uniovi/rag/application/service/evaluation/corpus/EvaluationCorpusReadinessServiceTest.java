package com.uniovi.rag.application.service.evaluation.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;

@ExtendWith(MockitoExtension.class)
class EvaluationCorpusReadinessServiceTest {

    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EvaluationCorpusStorageIntegrityService storageIntegrityService;

    @InjectMocks private EvaluationCorpusReadinessService service;

    @BeforeEach
    void setUpReadinessTests() {
        lenient().when(storageIntegrityService.hasReadyDocumentWithMissingBinary(any())).thenReturn(false);
        lenient()
                .when(storageIntegrityService.storageReadyDocumentIds(any()))
                .thenReturn(List.of());
    }

    @Test
    void getReadiness_emptyCorpus_returnsNoDocuments() {
        UUID corpusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(0);
        when(evaluationCorpusApplicationService.requireContext(eq(userId), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, UUID.randomUUID(), List.of(), List.of()));

        var readiness = service.getReadiness(userId, corpusId);

        assertThat(readiness.runnable()).isFalse();
        assertThat(readiness.primaryBlocker()).isEqualTo(LabCorpusReasonCodes.NO_DOCUMENTS);
    }

    @Test
    void getReadiness_processingOnly_returnsNoReadyDocuments() {
        UUID corpusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(0);
        KnowledgeDocumentEntity ingesting = mock(KnowledgeDocumentEntity.class);
        when(ingesting.getStatus()).thenReturn(ProjectDocumentStatus.INGESTING);
        when(evaluationCorpusApplicationService.requireContext(eq(userId), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(ingesting)));

        var readiness = service.getReadiness(userId, corpusId);

        assertThat(readiness.runnable()).isFalse();
        assertThat(readiness.primaryBlocker()).isEqualTo(LabCorpusReasonCodes.NO_READY_DOCUMENTS);
    }

    @Test
    void getReadiness_readyDocs_exposesStorageReadyCount() {
        UUID corpusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID docIdMissingBinary = UUID.randomUUID();
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(0);
        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        KnowledgeDocumentEntity readyMissingBinary = mock(KnowledgeDocumentEntity.class);
        when(readyMissingBinary.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(evaluationCorpusApplicationService.requireContext(eq(userId), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId,
                                UUID.randomUUID(),
                                List.of(docId, docIdMissingBinary),
                                List.of(ready, readyMissingBinary)));
        when(storageIntegrityService.storageReadyDocumentIds(any())).thenReturn(List.of(docId));
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.empty());

        var readiness = service.getReadiness(userId, corpusId);

        assertThat(readiness.readyCount()).isEqualTo(2);
        assertThat(readiness.storageReadyCount()).isEqualTo(1);
        assertThat(readiness.runnable()).isTrue();
    }

    @Test
    void getReadiness_readyDocsNoSnapshot_returnsNoActiveSnapshotBlocker() {
        UUID corpusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(0);
        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(evaluationCorpusApplicationService.requireContext(eq(userId), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, UUID.randomUUID(), List.of(docId), List.of(ready)));
        when(storageIntegrityService.storageReadyDocumentIds(any())).thenReturn(List.of(docId));
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.empty());

        var readiness = service.getReadiness(userId, corpusId);

        assertThat(readiness.runnable()).isTrue();
        assertThat(readiness.primaryBlocker()).isNull();
        assertThat(readiness.snapshotBlocker()).isEqualTo(LabCorpusReasonCodes.INDEX_PREPARATION_REQUIRED);
        assertThat(readiness.snapshotBlockerDetailCode()).isEqualTo("NO_ACTIVE_INDEX");
        assertThat(readiness.reindexRequired()).isTrue();
    }

    @Test
    void getReadiness_readyDocsMissingBinary_returnsDocumentBinaryMissing() {
        UUID corpusId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(0);
        KnowledgeDocumentEntity ready = mock(KnowledgeDocumentEntity.class);
        when(ready.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(evaluationCorpusApplicationService.requireContext(eq(userId), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(ready)));
        when(storageIntegrityService.hasReadyDocumentWithMissingBinary(any())).thenReturn(true);
        when(storageIntegrityService.storageReadyDocumentIds(any())).thenReturn(List.of());

        var readiness = service.getReadiness(userId, corpusId);

        assertThat(readiness.runnable()).isFalse();
        assertThat(readiness.primaryBlocker()).isEqualTo(LabCorpusReasonCodes.DOCUMENT_BINARY_MISSING);
    }
}
