package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.knowledge.document.DocumentService;
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

    private static final String METRIC_DOCUMENT_CALLS = "rag.document.calls";

    private static final String TAG_OPERATION = "operation";

    /** Span name for document ingestion / loading operations. */
    private static final String SPAN_DOCUMENTS_LOAD = "rag.documents.load";

    private final DocumentService delegate;
    private final ObservabilitySupport observability;

    public TracedDocumentService(DocumentService delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public void processDocument(MultipartFile file) {
        String filename = file != null && file.getOriginalFilename() != null ? file.getOriginalFilename() : "null";
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "processDocument");
        observability.recordTimer("rag.document.processDocument", () -> {
            observability.runWithSpan(
                    // Domain convention: document ingestion/loading
                    SPAN_DOCUMENTS_LOAD,
                    Map.of("filename", truncate(filename)),
                    () -> delegate.processDocument(file)
            );
            return null;
        });
    }

    @Override
    public void add(List<Document> documents) {
        int count = documents != null ? documents.size() : 0;
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "add");
        observability.recordTimer("rag.document.add", () -> {
            observability.runWithSpan(
                    // Domain convention: document ingestion/loading
                    SPAN_DOCUMENTS_LOAD,
                    Map.of("documentCount", String.valueOf(count)),
                    () -> delegate.add(documents)
            );
            return null;
        });
    }

    @Override
    public void clearDatabase() {
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "clearDatabase");
        observability.recordTimer("rag.document.clearDatabase", () -> {
            observability.runWithSpan(
                    // Domain convention: ingestion/loading is the closest operation bucket
                    SPAN_DOCUMENTS_LOAD,
                    Map.of(TAG_OPERATION, "clearDatabase"),
                    delegate::clearDatabase
            );
            return null;
        });
    }

    @Override
    public boolean hasDocuments() {
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "hasDocuments");
        return observability.runWithSpan(
                // Domain convention: document ingestion/loading
                SPAN_DOCUMENTS_LOAD,
                Map.of(),
                "result",
                delegate::hasDocuments);
    }

    @Override
    public int deleteDocumentByDocumentId(String documentId) {
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "deleteDocumentByDocumentId");
        return observability.recordTimer("rag.document.deleteDocumentByDocumentId", () ->
                observability.runWithSpan(
                        SPAN_DOCUMENTS_LOAD,
                        Map.of("documentId", truncate(documentId != null ? documentId : "")),
                        "deletedCount",
                        () -> delegate.deleteDocumentByDocumentId(documentId)
                ));
    }

    @Override
    public boolean hasDocumentWithId(String documentId) {
        observability.recordCounter(METRIC_DOCUMENT_CALLS, TAG_OPERATION, "hasDocumentWithId");
        return observability.runWithSpan(
                SPAN_DOCUMENTS_LOAD,
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
