package com.uniovi.rag.infrastructure.persistence.impl;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Default implementation of MinuteDocumentRepository.
 * Enforces no-duplicate rule: never inserts if a document with the same id already exists.
 */
public class MinuteDocumentRepositoryImpl implements MinuteDocumentRepository {

    private final DocumentService documentService;
    private final MetadataMinuteDocumentService minuteDocumentService;

    public MinuteDocumentRepositoryImpl(DocumentService documentService,
                                       MetadataMinuteDocumentService minuteDocumentService) {
        this.documentService = documentService;
        this.minuteDocumentService = minuteDocumentService;
    }

    @Override
    public AddResult addMinute(Minute minute) {
        if (minute == null || minute.id() == null || minute.id().isBlank()) {
            throw new IllegalArgumentException("Minute and minute.id() must be non-null and non-blank");
        }
        if (documentService.hasDocumentWithId(minute.id())) {
            return AddResult.ALREADY_EXISTS;
        }
        List<Document> documents = minuteDocumentService.createDocumentsFromMinute(minute);
        documentService.add(documents);
        return AddResult.ADDED;
    }

    @Override
    public int deleteById(String id) {
        return documentService.deleteDocumentByDocumentId(id);
    }

    @Override
    public boolean hasDocumentWithId(String id) {
        return documentService.hasDocumentWithId(id);
    }
}
