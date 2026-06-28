package com.uniovi.rag.domain.runtime.policy;

/**
 * How strictly generation must stick to retrieved evidence vs attempt an answer from partial context.
 */
public enum AnswerGroundingPolicy {
    /**
     * No retrieval and no assembled documentary corpus: plain chat-style generation (general knowledge); disclose that
     * project documents are not consulted when relevant.
     */
    DIRECT_UNGROUNDED_BASELINE,
    /**
     * Non-retrieval path where prompt includes assembled project/meeting documentary corpus (full-corpus / snapshot chunks).
     */
    CORPUS_GROUNDED_BASELINE,
    /** Prefer answering from retrieved fragments; express uncertainty; never substitute generic abstention when context is non-empty. */
    ATTEMPT_WITH_CONTEXT,
    /** Default retrieval path with corpus-only factual claims. */
    DEFAULT_RETRIEVAL_GROUNDED,
    /** Strong grounding; every constraint must match supporting text. */
    STRICT_GROUNDED,
    /** Absence and negative-evidence questions; forbid affirmative answers without exact support. */
    NEGATIVE_EVIDENCE,
    /** Count, duration, date, and comparator questions. */
    NUMERIC_OR_DATE,
    /** Entity, topic lookup, boolean presence, and find-paragraph questions. */
    ENTITY_OR_TOPIC,
    /** Post-retrieval legacy framing; superseded by factual policies for document-bound questions. */
    NEGATIVE_GROUNDED
}
