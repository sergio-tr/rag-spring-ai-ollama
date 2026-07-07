/**
 * Observability: tracing and metrics for the RAG backend.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>ObservabilitySupport</b>: Low-level helper to create spans ({@code runWithSpan}),
 *       record timers ({@code recordTimer}) and counters ({@code recordCounter}).
 *       Used by abstract traced bases and by decorators.</li>
 *   <li><b>ContextPropagatingFutures</b>: Use instead of raw {@code CompletableFuture.supplyAsync} /
 *       {@code runAsync} on the common pool; for {@code parallelStream()} use
 *       {@code captureContext()} + {@code withSnapshot(snapshot, ...)} in each worker
 *       (restores Observation / trace ThreadLocals - avoids orphan spans in Jaeger).</li>
 *   <li><b>Abstract base per hierarchy</b>: Each major operation hierarchy has an abstract
 *       class that wraps the public API in spans and delegates to a protected abstract
 *       method for the real implementation. This avoids repeating trace/error handling
 *       and keeps parameters and responses in spans.
 *   <li><b>AbstractTracedQueryClassifier</b>: Implements {@link com.uniovi.rag.infrastructure.classifier.QueryClassifier};
 *       {@code classify}/{@code classifyWithText} run in span {@code rag.query.classify}
 *       and expose the predicted {@code rag.query.type}. Subclasses implement
 *       {@code doClassifyWithText}.</li>
 *   <li><b>TracedQueryService</b>: Implements {@link com.uniovi.rag.application.service.runtime.execution.QueryExecutionService};
 *       {@code generateResponse} runs in a timer and span {@code rag.query.generate}.
 *       Subclasses implement {@code doGenerateResponse}. Internal steps (expand, analyse)
 *       can be wrapped in additional spans by the subclass.</li>
 *   <li><b>TracedContextRetriever</b>: Decorator around any {@link com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever};
 *       {@code retrieve} and {@code createContext} run in spans
 *       {@code rag.documents.search}.</li>
 * </ul>
 * <p>When {@link io.micrometer.tracing.Tracer} is not available (no OTEL dependency),
 * {@link ObservabilitySupport} is not created and all traced types no-op (run the delegate logic without spans).
 */
package com.uniovi.rag.infrastructure.observability;
