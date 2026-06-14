package com.uniovi.rag.application.service.evaluation.metrics;

/** Outcome of controlled answerability labeling for one dataset row. */
public record AnswerabilityLabelResult(
        Answerability label,
        AnswerabilitySource source,
        String ruleId,
        AnswerabilityLabelConfidence confidence,
        String reason) {

    public AnswerabilityLabelResult {
        ruleId = ruleId != null ? ruleId : "";
        reason = reason != null ? reason : "";
    }

    public boolean reviewRequired() {
        return label == Answerability.NEEDS_REVIEW;
    }
}
