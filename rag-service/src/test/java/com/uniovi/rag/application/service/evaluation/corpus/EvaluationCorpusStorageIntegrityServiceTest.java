package com.uniovi.rag.application.service.evaluation.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationCorpusStorageIntegrityServiceTest {

    @Mock private BinaryStoragePort binaryStoragePort;

    private EvaluationCorpusStorageIntegrityService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationCorpusStorageIntegrityService(binaryStoragePort);
    }

    @Test
    void readyDocumentWithMissingBinaryIsNotStorageReady() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(docId, ProjectDocumentStatus.READY, "project/doc/source.bin");
        when(binaryStoragePort.isReadableNonEmpty("project/doc/source.bin")).thenReturn(false);

        assertThat(service.isStorageReady(doc)).isFalse();
        assertThat(service.hasReadyDocumentWithMissingBinary(List.of(doc))).isTrue();
        assertThat(service.storageReadyDocumentIds(List.of(doc))).isEmpty();
    }

    @Test
    void readyDocumentWithReadableBinaryIsStorageReady() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = document(docId, ProjectDocumentStatus.READY, "project/doc/source.bin");
        when(binaryStoragePort.isReadableNonEmpty("project/doc/source.bin")).thenReturn(true);

        assertThat(service.isStorageReady(doc)).isTrue();
        assertThat(service.hasReadyDocumentWithMissingBinary(List.of(doc))).isFalse();
        assertThat(service.storageReadyDocumentIds(List.of(doc))).containsExactly(docId);
    }

    private static KnowledgeDocumentEntity document(UUID id, ProjectDocumentStatus status, String storageUri) {
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        lenient().when(doc.getId()).thenReturn(id);
        when(doc.getStatus()).thenReturn(status);
        when(doc.getStorageUri()).thenReturn(storageUri);
        return doc;
    }
}
