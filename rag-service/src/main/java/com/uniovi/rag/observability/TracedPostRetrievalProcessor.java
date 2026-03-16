package com.uniovi.rag.observability;

import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link PostRetrievalProcessor}.
 */
public final class TracedPostRetrievalProcessor implements PostRetrievalProcessor {

    private static final int MAX_ATTR = 500;

    private final PostRetrievalProcessor delegate;
    private final ObservabilitySupport observability;

    public TracedPostRetrievalProcessor(PostRetrievalProcessor delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public List<Document> process(List<Document> documents, String query) {
        observability.recordCounter("rag.postretrieval.calls", "operation", "process");
        return observability.recordTimer("rag.postretrieval.process", () ->
                observability.runWithSpan(
                        "rag.postretrieval.process",
                        Map.of(
                                "inputCount", String.valueOf(documents != null ? documents.size() : 0),
                                "query", truncate(query != null ? query : "")
                        ),
                        (String) null,
                        () -> delegate.process(documents, query)
                ));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
