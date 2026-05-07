package com.uniovi.rag.domain.runtime.policy;

/**
 * How strictly generation must stick to retrieved evidence vs attempt an answer from partial context.
 */
public enum AnswerGroundingPolicy {
    /** No retrieval path: answer like a plain chat model; disclose lack of project documents when relevant. */
    DIRECT_BASELINE,
    /** Prefer answering from retrieved fragments; express uncertainty; never substitute generic abstention when context is non-empty. */
    ATTEMPT_WITH_CONTEXT,
    /** Strong grounding; still allows partial answers when fragments exist (no blind abstention). */
    STRICT_GROUNDED,
    /** Post-retrieval / evidence-preservation framing; emphasize surviving fragments and uncertainty. */
    NEGATIVE_GROUNDED
}
