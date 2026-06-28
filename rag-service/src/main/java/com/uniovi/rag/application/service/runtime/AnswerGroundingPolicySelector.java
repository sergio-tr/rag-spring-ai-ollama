package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;

/**
 * Maps effective {@link RagConfig} to {@link AnswerGroundingPolicy} for chat presets P0–P14 approximations.
 */
public final class AnswerGroundingPolicySelector {

    private AnswerGroundingPolicySelector() {
    }

    /**
     * Ungrounded direct chat (no retrieval, no assembled corpus): {@link AnswerGroundingPolicy#DIRECT_UNGROUNDED_BASELINE}.
     * Corpus-grounded without retrieval (naive full corpus): {@link AnswerGroundingPolicy#CORPUS_GROUNDED_BASELINE}.
     * Dense RAG: {@link AnswerGroundingPolicy#ATTEMPT_WITH_CONTEXT} and stricter variants when retrieval is on.
     */
    public static AnswerGroundingPolicy from(RagConfig rag) {
        if (rag == null) {
            return AnswerGroundingPolicy.DIRECT_UNGROUNDED_BASELINE;
        }
        if (!rag.useRetrieval()) {
            if (rag.naiveFullCorpusInPromptEnabled()) {
                return AnswerGroundingPolicy.CORPUS_GROUNDED_BASELINE;
            }
            return AnswerGroundingPolicy.DIRECT_UNGROUNDED_BASELINE;
        }
        if (rag.judgeEnabled() && rag.metadataEnabled()) {
            return AnswerGroundingPolicy.STRICT_GROUNDED;
        }
        if (rag.postRetrievalEnabled()) {
            return AnswerGroundingPolicy.DEFAULT_RETRIEVAL_GROUNDED;
        }
        return AnswerGroundingPolicy.DEFAULT_RETRIEVAL_GROUNDED;
    }
}
