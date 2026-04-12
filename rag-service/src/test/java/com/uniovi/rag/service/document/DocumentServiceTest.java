package com.uniovi.rag.service.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for {@link DocumentService} contract methods (pure Mockito stubbing/verification).
 */
class DocumentServiceTest {

    @Test
    void interfaceMethods_canBeStubbedAndVerified() {
        DocumentService svc = mock(DocumentService.class);

        MultipartFile file = mock(MultipartFile.class);
        List<Document> docs = List.of(new Document("id", "c", java.util.Map.of()));

        svc.processDocument(file);
        svc.add(docs);
        svc.clearDatabase();
        when(svc.hasDocuments()).thenReturn(true);
        when(svc.deleteDocumentByDocumentId("id")).thenReturn(3);
        when(svc.hasDocumentWithId("id")).thenReturn(true);

        svc.processDocument(file);
        svc.add(docs);
        svc.clearDatabase();
        assertThat(svc.hasDocuments()).isTrue();
        assertThat(svc.deleteDocumentByDocumentId("id")).isEqualTo(3);
        assertThat(svc.hasDocumentWithId("id")).isTrue();

        verify(svc, times(2)).processDocument(file);
        verify(svc, times(2)).add(docs);
        verify(svc, times(2)).clearDatabase();
        verify(svc, times(1)).hasDocuments();
        verify(svc).deleteDocumentByDocumentId("id");
        verify(svc).hasDocumentWithId("id");
    }
}
