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
     * P0-like (no retrieval): {@link AnswerGroundingPolicy#DIRECT_BASELINE}.
     * P1–P3 / low RAG: {@link AnswerGroundingPolicy#ATTEMPT_WITH_CONTEXT} when retrieval is on.
     * P4+ stricter stacks: {@link AnswerGroundingPolicy#STRICT_GROUNDED} when judge + metadata are on.
     * Post-retrieval stacks: {@link AnswerGroundingPolicy#NEGATIVE_GROUNDED} when post-processing is on (unless strict wins).
     */
    public static AnswerGroundingPolicy from(RagConfig rag) {
        if (rag == null || !rag.useRetrieval()) {
            return AnswerGroundingPolicy.DIRECT_BASELINE;
        }
        if (rag.judgeEnabled() && rag.metadataEnabled()) {
            return AnswerGroundingPolicy.STRICT_GROUNDED;
        }
        if (rag.postRetrievalEnabled()) {
            return AnswerGroundingPolicy.NEGATIVE_GROUNDED;
        }
        return AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT;
    }
}
