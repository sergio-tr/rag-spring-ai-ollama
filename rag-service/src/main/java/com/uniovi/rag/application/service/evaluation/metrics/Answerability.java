package com.uniovi.rag.application.service.evaluation.metrics;

/** Dataset answerability group for evaluation analysis rollups. */
public enum Answerability {
    ANSWERABLE,
    UNANSWERABLE,
    AMBIGUOUS,
    UNKNOWN,
    NEEDS_REVIEW;

    public static Answerability fromDataset(
            Boolean unanswerable,
            boolean unanswerableDeclared,
            Boolean ambiguous,
            boolean ambiguousDeclared) {
        if (ambiguousDeclared && Boolean.TRUE.equals(ambiguous)) {
            return AMBIGUOUS;
        }
        if (!unanswerableDeclared && !ambiguousDeclared) {
            return UNKNOWN;
        }
        if (unanswerableDeclared) {
            return Boolean.TRUE.equals(unanswerable) ? UNANSWERABLE : ANSWERABLE;
        }
        return UNKNOWN;
    }
}
