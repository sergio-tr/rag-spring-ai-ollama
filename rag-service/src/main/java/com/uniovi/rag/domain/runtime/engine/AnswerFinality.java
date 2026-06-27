package com.uniovi.rag.domain.runtime.engine;

/** Terminal contract for answer-path preservation (R1 acceptance guard). */
public enum AnswerFinality {
    STANDARD,
    DETERMINISTIC_TOOL_FINAL;

    public boolean allowPostSynthesisRewrite() {
        return this == STANDARD;
    }

    public AnswerSource answerSource() {
        return this == DETERMINISTIC_TOOL_FINAL ? AnswerSource.DETERMINISTIC_TOOL : AnswerSource.STANDARD;
    }
}
