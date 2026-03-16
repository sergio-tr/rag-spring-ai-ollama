package com.uniovi.rag.repository.impl;

import com.uniovi.rag.model.AddResult;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MinuteDocumentRepositoryImplTest {

    private DocumentService documentService;
    private MetadataMinuteDocumentService metadataMinuteDocumentService;
    private MinuteDocumentRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        metadataMinuteDocumentService = mock(MetadataMinuteDocumentService.class);
        repository = new MinuteDocumentRepositoryImpl(documentService, metadataMinuteDocumentService);
    }

    @Test
    void addMinute_nullOrBlankId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                repository.addMinute(new Minute(null, null, null, null, null, null, null, null, null, 0, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () ->
                repository.addMinute(new Minute("", "f", null, null, null, null, null, null, null, 0, null, null, null, null, null)));
    }

    @Test
    void addMinute_alreadyExists_returnsAlreadyExists() {
        Minute minute = new Minute("id-1", "f", null, null, null, null, null, null, null, 0, null, null, null, null, null);
        when(documentService.hasDocumentWithId("id-1")).thenReturn(true);

        assertEquals(AddResult.ALREADY_EXISTS, repository.addMinute(minute));
        verify(documentService, never()).add(anyList());
    }

    @Test
    void addMinute_newMinute_returnsAdded() {
        Minute minute = new Minute("id-1", "f", null, null, null, null, null, null, null, 0, null, null, null, null, null);
        when(documentService.hasDocumentWithId("id-1")).thenReturn(false);
        when(metadataMinuteDocumentService.createDocumentsFromMinute(minute)).thenReturn(List.of(new Document("c", java.util.Map.of())));

        assertEquals(AddResult.ADDED, repository.addMinute(minute));
        verify(documentService).add(anyList());
    }

    @Test
    void deleteById_delegatesToDocumentService() {
        when(documentService.deleteDocumentByDocumentId("doc-1")).thenReturn(3);
        assertEquals(3, repository.deleteById("doc-1"));
        verify(documentService).deleteDocumentByDocumentId("doc-1");
    }

    @Test
    void hasDocumentWithId_delegatesToDocumentService() {
        when(documentService.hasDocumentWithId("id")).thenReturn(true);
        assertTrue(repository.hasDocumentWithId("id"));
        when(documentService.hasDocumentWithId("id")).thenReturn(false);
        assertFalse(repository.hasDocumentWithId("id"));
    }
}
