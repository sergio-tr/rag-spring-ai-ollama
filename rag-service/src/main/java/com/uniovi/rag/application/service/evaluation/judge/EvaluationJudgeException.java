package com.uniovi.rag.application.service.evaluation.judge;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Structured failure for evaluation judge LLM calls (no scoring changes). */
public final class EvaluationJudgeException extends RuntimeException {

    public static final String ERROR_CODE_INVOCATION_FAILED = "EVALUATION_JUDGE_INVOCATION_FAILED";
    public static final String ERROR_CODE_EMPTY_RESPONSE = "EVALUATION_JUDGE_EMPTY_RESPONSE";

    private final String errorCode;
    private final LlmProvider judgeProvider;
    private final String judgeModel;

    private EvaluationJudgeException(
            String errorCode, LlmProvider judgeProvider, String judgeModel, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.judgeProvider = judgeProvider;
        this.judgeModel = judgeModel;
    }

    public String errorCode() {
        return errorCode;
    }

    public LlmProvider judgeProvider() {
        return judgeProvider;
    }

    public String judgeModel() {
        return judgeModel;
    }

    public static EvaluationJudgeException invocationFailed(LlmProvider provider, String model, Throwable cause) {
        String msg =
                cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                        ? cause.getMessage()
                        : ERROR_CODE_INVOCATION_FAILED;
        return new EvaluationJudgeException(ERROR_CODE_INVOCATION_FAILED, provider, model, msg, cause);
    }

    public static EvaluationJudgeException emptyResponse(LlmProvider provider, String model) {
        return new EvaluationJudgeException(
                ERROR_CODE_EMPTY_RESPONSE, provider, model, ERROR_CODE_EMPTY_RESPONSE, null);
    }

    public static EvaluationJudgeException fromErrorCode(String errorCode) {
        String code =
                errorCode != null && !errorCode.isBlank()
                        ? errorCode.trim()
                        : ERROR_CODE_INVOCATION_FAILED;
        return new EvaluationJudgeException(code, null, null, code, null);
    }
}
