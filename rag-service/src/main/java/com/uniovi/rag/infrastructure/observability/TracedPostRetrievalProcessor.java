package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.runtime.retrieval.post.PostRetrievalProcessor;
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
                                "queryLength", TelemetryRedaction.queryLength(query)
                        ),
                        (String) null,
                        () -> delegate.process(documents, query)
                ));
    }

}
