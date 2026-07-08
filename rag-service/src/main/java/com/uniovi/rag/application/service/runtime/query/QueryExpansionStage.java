package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryExpansionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies optional LLM query expansion when {@code expansionEnabled} is true.
 * Failures fall back to the original query without failing the chat turn.
 */
@Service
public class QueryExpansionStage {

    private static final Logger log = LoggerFactory.getLogger(QueryExpansionStage.class);
    private static final String STRATEGY_MINUTE_DOCUMENT = "MINUTE_DOCUMENT_STRUCTURE";

    private final QueryExpander queryExpander;

    public QueryExpansionStage(QueryExpander queryExpander) {
        this.queryExpander = queryExpander;
    }

    public QueryExpansionResult expand(ExecutionContext ctx, String normalizedQuery) {
        String original = normalizedQuery != null ? normalizedQuery : "";
        if (!ctx.resolved().toRagConfig().expansionEnabled()) {
            return QueryExpansionResult.skipped(original);
        }
        if (original.isBlank()) {
            return QueryExpansionResult.skipped(original);
        }
        try {
            String expanded = queryExpander.expand(original);
            if (expanded == null || expanded.isBlank()) {
                return QueryExpansionResult.failed(original, "expansion_empty_result");
            }
            if (expanded.equals(original)) {
                return new QueryExpansionResult(
                        original, original, false, STRATEGY_MINUTE_DOCUMENT, List.of(), "expansion_unchanged");
            }
            return QueryExpansionResult.applied(original, expanded, STRATEGY_MINUTE_DOCUMENT, "expansion_applied");
        } catch (Exception e) {
            log.warn("Query expansion failed; using original query: {}", e.getMessage());
            return QueryExpansionResult.failed(original, e.getMessage() != null ? e.getMessage() : "expansion_failed");
        }
    }
}
