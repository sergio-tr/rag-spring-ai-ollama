package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.service.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link ContextRetriever}.
 * All operations (retrieve, retrieveWithMetadataFilters, createContext) are wrapped in spans with
 * counters and timers; getters/setters and restoreDefaultSettings delegate without spans.
 *
 * <p>Retrieval <strong>document counts</strong> are recorded as {@code rag_retrieval_documents_total}
 * with low-cardinality labels {@code operation} and {@code bucket} (size bands), not per-query IDs.
 */
public final class TracedContextRetriever implements ContextRetriever {

    private static final int MAX_ATTR = 500;

    private static final String METRIC_RETRIEVER_CALLS = "rag.retriever.calls";

    /** Counters bucketed to avoid high-cardinality labels (no raw document counts as tag values). */
    private static final String METRIC_RETRIEVAL_DOCS = "rag_retrieval_documents_total";

    private final ContextRetriever delegate;
    private final ObservabilitySupport observability;

    public TracedContextRetriever(ContextRetriever delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public List<Document> retrieve(String query) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "retrieve");
        List<Document> result =
                observability.recordTimer(
                        "rag.retriever.retrieve",
                        () ->
                                observability.runWithSpan(
                                        // Domain convention: retrieval is part of document search
                                        "rag.documents.search",
                                        Map.of(
                                                "query", truncate(query != null ? query : ""),
                                                "rag.top_k", String.valueOf(delegate.getTopK())
                                        ),
                                        (String) null,
                                        () -> delegate.retrieve(query)));
        recordRetrievalDocuments("retrieve", result);
        return result;
    }

    @Override
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "retrieveWithMetadataFilters");
        List<Document> result =
                observability.recordTimer(
                        "rag.retriever.retrieveWithMetadataFilters",
                        () ->
                                observability.runWithSpan(
                                        // Domain convention: retrieval is part of document search
                                        "rag.documents.search",
                                        Map.of(
                                                "query", truncate(query != null ? query : ""),
                                                "hasEntities",
                                                String.valueOf(nerEntities != null && !nerEntities.isEmpty()),
                                                "rag.top_k", String.valueOf(delegate.getTopK())
                                        ),
                                        (String) null,
                                        () -> delegate.retrieveWithMetadataFilters(query, nerEntities)));
        recordRetrievalDocuments("retrieveWithMetadataFilters", result);
        return result;
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "createContext");
        String ctx =
                observability.recordTimer(
                        "rag.retriever.createContext",
                        () ->
                                observability.runWithSpan(
                                        // Domain convention: retrieval stage (documents -> context)
                                        "rag.documents.search",
                                        Map.of(
                                                "query", truncate(query != null ? query : ""),
                                                "rag.docs.count",
                                                String.valueOf(documents != null ? documents.size() : 0),
                                                "rag.top_k", String.valueOf(delegate.getTopK())
                                        ),
                                        (String) null,
                                        () -> delegate.createContext(documents, query, entities)));
        recordRetrievalDocuments("createContext", documents);
        return ctx;
    }

    @Override
    public int getTopK() {
        return delegate.getTopK();
    }

    @Override
    public void setTopK(int topK) {
        delegate.setTopK(topK);
    }

    @Override
    public double getSimilarityThreshold() {
        return delegate.getSimilarityThreshold();
    }

    @Override
    public void setSimilarityThreshold(double similarityThreshold) {
        delegate.setSimilarityThreshold(similarityThreshold);
    }

    @Override
    public void restoreDefaultSettings() {
        delegate.restoreDefaultSettings();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }

    /**
     * Records one increment in a fixed size bucket (low cardinality).
     */
    private void recordRetrievalDocuments(String operation, List<Document> documents) {
        int n = documents == null ? 0 : documents.size();
        String bucket = sizeBucket(n);
        observability.recordCounter(METRIC_RETRIEVAL_DOCS, "operation", operation, "bucket", bucket);
    }

    static String sizeBucket(int n) {
        if (n <= 0) {
            return "0";
        }
        if (n <= 4) {
            return "1_4";
        }
        if (n <= 19) {
            return "5_19";
        }
        return "20_plus";
    }
}
