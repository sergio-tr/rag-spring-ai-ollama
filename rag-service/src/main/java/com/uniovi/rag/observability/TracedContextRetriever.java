package com.uniovi.rag.observability;

import com.uniovi.rag.service.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link ContextRetriever}.
 * All operations (retrieve, retrieveWithMetadataFilters, createContext) are wrapped in spans with
 * counters and timers; getters/setters and restoreDefaultSettings delegate without spans.
 */
public final class TracedContextRetriever implements ContextRetriever {

    private static final int MAX_ATTR = 500;

    private static final String METRIC_RETRIEVER_CALLS = "rag.retriever.calls";

    private final ContextRetriever delegate;
    private final ObservabilitySupport observability;

    public TracedContextRetriever(ContextRetriever delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public List<Document> retrieve(String query) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "retrieve");
        return observability.recordTimer("rag.retriever.retrieve", () ->
                observability.runWithSpan(
                        // Domain convention: retrieval is part of document search
                        "rag.documents.search",
                        Map.of(
                                "query", truncate(query != null ? query : ""),
                                "rag.top_k", String.valueOf(delegate.getTopK())
                        ),
                        (String) null,
                        () -> delegate.retrieve(query)
                ));
    }

    @Override
    public List<Document> retrieveWithMetadataFilters(String query, JSONObject nerEntities) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "retrieveWithMetadataFilters");
        return observability.recordTimer("rag.retriever.retrieveWithMetadataFilters", () ->
                observability.runWithSpan(
                        // Domain convention: retrieval is part of document search
                        "rag.documents.search",
                        Map.of(
                                "query", truncate(query != null ? query : ""),
                                "hasEntities", String.valueOf(nerEntities != null && !nerEntities.isEmpty()),
                                "rag.top_k", String.valueOf(delegate.getTopK())
                        ),
                        (String) null,
                        () -> delegate.retrieveWithMetadataFilters(query, nerEntities)
                ));
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        observability.recordCounter(METRIC_RETRIEVER_CALLS, "operation", "createContext");
        return observability.recordTimer("rag.retriever.createContext", () ->
                observability.runWithSpan(
                        // Domain convention: retrieval stage (documents -> context)
                        "rag.documents.search",
                        Map.of(
                                "query", truncate(query != null ? query : ""),
                                "rag.docs.count", String.valueOf(documents != null ? documents.size() : 0),
                                "rag.top_k", String.valueOf(delegate.getTopK())
                        ),
                        (String) null,
                        () -> delegate.createContext(documents, query, entities)
                ));
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
}
