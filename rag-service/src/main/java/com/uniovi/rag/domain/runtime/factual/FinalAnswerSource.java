package com.uniovi.rag.domain.runtime.factual;

/** Origin of the user-visible answer for one turn. */
public enum FinalAnswerSource {
    GENERATED,
    TOOL_FINAL,
    FUNCTION_FINAL,
    FORCED_ABSTENTION,
    DATE_GUARD_ABSTENTION
}
