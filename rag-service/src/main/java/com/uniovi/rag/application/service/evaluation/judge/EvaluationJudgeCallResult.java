package com.uniovi.rag.application.service.evaluation.judge;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Outcome of an evaluation judge LLM call (success or structured degradation). */
public record EvaluationJudgeCallResult(
        String content,
        String judgeStatus,
        String judgeFailureReason,
        LlmProvider judgeProvider,
        String judgeModel) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static EvaluationJudgeCallResult success(String content) {
        String text = content != null ? content.trim() : "";
        return new EvaluationJudgeCallResult(text, STATUS_SUCCESS, null, null, null);
    }

    public static EvaluationJudgeCallResult failed(String failureReason, LlmProvider provider, String model) {
        return new EvaluationJudgeCallResult("", STATUS_FAILED, failureReason, provider, model);
    }

    public static EvaluationJudgeCallResult failed(String failureReason) {
        return failed(failureReason, null, null);
    }

    public boolean judgeFailed() {
        return STATUS_FAILED.equals(judgeStatus);
    }
}
