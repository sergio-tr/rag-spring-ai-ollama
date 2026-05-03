package com.uniovi.rag.domain.runtime.judge;

/**
 * Where the candidate answer text originated from, for judge policy and traceability.
 */
public enum JudgeCandidateSource {
    WORKFLOW,
    DETERMINISTIC_TOOL,
    FUNCTION_CALLING
}

