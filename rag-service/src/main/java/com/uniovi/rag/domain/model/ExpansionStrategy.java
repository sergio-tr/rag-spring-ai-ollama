package com.uniovi.rag.domain.model;

public enum ExpansionStrategy {
    /** Current behaviour: rephrase using document structure; result = original + rephrased. */
    REPHRASE,
    /** Chain-of-Thought: rationale before answering → more keywords. */
    COT,
    /** Query-to-Expansion: list of keywords only. */
    Q2E
}
