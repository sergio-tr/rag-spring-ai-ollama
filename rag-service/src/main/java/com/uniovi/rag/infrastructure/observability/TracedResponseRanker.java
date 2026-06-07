package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.query.CandidateResponse;
import com.uniovi.rag.domain.model.RankerResult;
import com.uniovi.rag.application.service.runtime.ranking.ResponseRanker;

import java.util.List;
import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link ResponseRanker}.
 * Wraps the delegate; when observability is present, each {@link #selectBest} is traced.
 */
public final class TracedResponseRanker implements ResponseRanker {

    private static final int MAX_ATTR = 500;

    private final ResponseRanker delegate;
    private final ObservabilitySupport observability;

    public TracedResponseRanker(ResponseRanker delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public RankerResult selectBest(String query, String context, List<CandidateResponse> candidates) {
        if (observability == null) {
            return delegate.selectBest(query, context, candidates);
        }
        observability.recordCounter("rag.ranker.calls", "ranker", delegate.getClass().getSimpleName());
        return observability.recordTimer("rag.ranker.selectBest", () ->
                observability.runWithSpan(
                        "rag.ranker.selectBest",
                        Map.of(
                                "query", truncate(query != null ? query : ""),
                                "candidateCount", String.valueOf(candidates != null ? candidates.size() : 0)
                        ),
                        (String) null,
                        () -> delegate.selectBest(query, context, candidates)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
