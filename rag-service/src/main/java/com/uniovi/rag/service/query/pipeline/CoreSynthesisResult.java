package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.model.DraftAndContext;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.model.QueryType;

/**
 * Output of the single synthesis core ({@link ResponseSynthesisPipeline#synthesizeCore}): always a
 * {@link DraftAndContext} for post/ranker, plus how to map to {@link QueryResponse} when reasoning is off.
 */
public record CoreSynthesisResult(
        DraftAndContext draftAndContext,
        Kind kind,
        String toolSourceOrNull
) {

    public enum Kind {
        /** Date / metadata guard (answered as tool-style response). */
        METADATA_GUARD,
        /** Meeting-minutes tools (adapter, function-calling, or routed tool). */
        TOOL,
        /** Plain LLM + RAG path. */
        LLM
    }

    /**
     * Final HTTP response when reasoning is disabled (no post-step).
     */
    public QueryResponse toDirectQueryResponse(QueryType queryType) {
        String draft = draftAndContext.draft();
        return switch (kind) {
            case METADATA_GUARD, TOOL -> QueryResponse.fromTool(
                    draft,
                    toolSourceOrNull != null ? toolSourceOrNull : "tool",
                    queryType);
            case LLM -> QueryResponse.fromLLM(draft, queryType);
        };
    }
}
