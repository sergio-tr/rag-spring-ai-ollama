package com.uniovi.rag.observability;

import com.uniovi.rag.service.query.ResponseValidator;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link ResponseValidator}.
 */
public final class TracedResponseValidator implements ResponseValidator {

    private static final int MAX_ATTR = 500;
    private static final String METRIC_KEY_OPERATION = "operation";

    private final ResponseValidator delegate;
    private final ObservabilitySupport observability;

    public TracedResponseValidator(ResponseValidator delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public boolean isValidResponse(String response, String context) {
        observability.recordCounter("rag.validator.calls", METRIC_KEY_OPERATION, "isValidResponse");
        return observability.runWithSpan(
                "rag.validator.isValidResponse",
                Map.of("context", truncate(context != null ? context : "")),
                "valid",
                () -> delegate.isValidResponse(response, context)
        );
    }

    @Override
    public String cleanResponse(String response) {
        observability.recordCounter("rag.validator.calls", METRIC_KEY_OPERATION, "cleanResponse");
        return observability.runWithSpan(
                "rag.validator.cleanResponse",
                Map.of("responseLength", String.valueOf(response != null ? response.length() : 0)),
                (String) null,
                () -> delegate.cleanResponse(response)
        );
    }

    @Override
    public String validateAndClean(String response, String context) {
        observability.recordCounter("rag.validator.calls", METRIC_KEY_OPERATION, "validateAndClean");
        return observability.runWithSpan(
                "rag.validator.validateAndClean",
                Map.of("context", truncate(context != null ? context : "")),
                (String) null,
                () -> delegate.validateAndClean(response, context)
        );
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
