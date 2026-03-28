package com.uniovi.rag.observability;

import com.uniovi.rag.service.document.DocumentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedDocumentService}.
 */
class TracedDocumentServiceTest {

    private DocumentService delegate;
    private ObservabilitySupport observability;
    private TracedDocumentService traced;

    @BeforeEach
    void setUp() {
        delegate = mock(DocumentService.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedDocumentService(delegate, observability);
    }

    @Test
    void processDocument_delegates() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        doNothing().when(delegate).processDocument(any(MultipartFile.class));

        traced.processDocument(file);

        verify(delegate).processDocument(file);
    }

    @Test
    void add_delegates() {
        List<Document> docs = List.of(new Document("id", "content", java.util.Map.of()));
        doNothing().when(delegate).add(anyList());

        traced.add(docs);

        verify(delegate).add(docs);
    }

    @Test
    void clearDatabase_delegates() {
        doNothing().when(delegate).clearDatabase();

        traced.clearDatabase();

        verify(delegate).clearDatabase();
    }

    @Test
    void hasDocuments_delegates() {
        when(delegate.hasDocuments()).thenReturn(true);
        assertTrue(traced.hasDocuments());
        when(delegate.hasDocuments()).thenReturn(false);
        assertFalse(traced.hasDocuments());
        verify(delegate, times(2)).hasDocuments();
    }

    @Test
    void deleteDocumentByDocumentId_delegates() {
        when(delegate.deleteDocumentByDocumentId("id-1")).thenReturn(2);
        assertEquals(2, traced.deleteDocumentByDocumentId("id-1"));
        verify(delegate).deleteDocumentByDocumentId("id-1");
    }

    @Test
    void hasDocumentWithId_delegates() {
        when(delegate.hasDocumentWithId("id")).thenReturn(true);
        assertTrue(traced.hasDocumentWithId("id"));
        verify(delegate).hasDocumentWithId("id");
    }
}
