package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.service.document.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link DocumentService}.
 * Each operation is wrapped in a span and optional counter/timer so all document
 * operations are visible in traces and metrics.
 */
public final class TracedDocumentService implements DocumentService {

    private static final int MAX_ATTR = 500;

    private final DocumentService delegate;
    private final ObservabilitySupport observability;

    public TracedDocumentService(DocumentService delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public void processDocument(MultipartFile file) {
        String filename = file != null && file.getOriginalFilename() != null ? file.getOriginalFilename() : "null";
        observability.recordCounter("rag.document.calls", "operation", "processDocument");
        observability.recordTimer("rag.document.processDocument", () -> {
            observability.runWithSpan(
                    // Domain convention: document ingestion/loading
                    "rag.documents.load",
                    Map.of("filename", truncate(filename)),
                    () -> delegate.processDocument(file)
            );
            return null;
        });
    }

    @Override
    public void add(List<Document> documents) {
        int count = documents != null ? documents.size() : 0;
        observability.recordCounter("rag.document.calls", "operation", "add");
        observability.recordTimer("rag.document.add", () -> {
            observability.runWithSpan(
                    // Domain convention: document ingestion/loading
                    "rag.documents.load",
                    Map.of("documentCount", String.valueOf(count)),
                    () -> delegate.add(documents)
            );
            return null;
        });
    }

    @Override
    public void clearDatabase() {
        observability.recordCounter("rag.document.calls", "operation", "clearDatabase");
        observability.recordTimer("rag.document.clearDatabase", () -> {
            observability.runWithSpan(
                    // Domain convention: ingestion/loading is the closest operation bucket
                    "rag.documents.load",
                    Map.of("operation", "clearDatabase"),
                    () -> delegate.clearDatabase()
            );
            return null;
        });
    }

    @Override
    public boolean hasDocuments() {
        observability.recordCounter("rag.document.calls", "operation", "hasDocuments");
        return observability.runWithSpan(
                // Domain convention: document ingestion/loading
                "rag.documents.load",
                Map.of(),
                "result",
                () -> delegate.hasDocuments()
        );
    }

    @Override
    public int deleteDocumentByDocumentId(String documentId) {
        observability.recordCounter("rag.document.calls", "operation", "deleteDocumentByDocumentId");
        return observability.recordTimer("rag.document.deleteDocumentByDocumentId", () ->
                observability.runWithSpan(
                        "rag.documents.load",
                        Map.of("documentId", truncate(documentId != null ? documentId : "")),
                        "deletedCount",
                        () -> delegate.deleteDocumentByDocumentId(documentId)
                ));
    }

    @Override
    public boolean hasDocumentWithId(String documentId) {
        observability.recordCounter("rag.document.calls", "operation", "hasDocumentWithId");
        return observability.runWithSpan(
                "rag.documents.load",
                Map.of("documentId", truncate(documentId != null ? documentId : "")),
                "result",
                () -> delegate.hasDocumentWithId(documentId)
        );
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
