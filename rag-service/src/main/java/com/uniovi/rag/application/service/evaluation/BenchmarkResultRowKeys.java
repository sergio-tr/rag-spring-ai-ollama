package com.uniovi.rag.application.service.evaluation;

/** JSON keys for canonical benchmark result rows (maps passed to persistence). */
public final class BenchmarkResultRowKeys {

    public static final String DATASET_QUESTION_ID = "dataset_question_id";
    public static final String ITEM_OUTCOME = "item_outcome";
    /** Protocol label for baseline runners ({@link com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol}). */
    public static final String EVALUATION_PROTOCOL = "evaluation_protocol";

    /** Workbook preset id (e.g. {@code P7}). */
    public static final String PRESET_CODE = "preset_code";
    public static final String PRESET_LABEL = "preset_label";

    public static final String DIFFICULTY = "difficulty";

    public static final String LATENCY_MS = "latency_ms";

    /** Stable machine code when {@link com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome#FAILED} / guard rails. */
    public static final String ERROR_CODE = "error_code";
    public static final String REASON = "reason";

    public static final String LLM_MODEL_ID = "llm_model_id";

    public static final String EMBEDDING_MODEL_ID = "embedding_model_id";

    public static final String JUDGE_STATUS = "judge_status";
    public static final String JUDGE_FAILURE_REASON = "judge_failure_reason";

    public static final String ROLE_EVAL_SUBSET = "role_eval_subset";
    public static final String ROLE_EVAL_ROLE_FAMILY = "role_eval_role_family";
    public static final String ROLE_EVAL_ROLE_PROFILE = "role_eval_role_profile";
    public static final String ROLE_EVAL_SCORING_TYPE = "role_eval_scoring_type";
    public static final String ROLE_EVAL_PASSED = "role_eval_passed";

    private BenchmarkResultRowKeys() {}
}
