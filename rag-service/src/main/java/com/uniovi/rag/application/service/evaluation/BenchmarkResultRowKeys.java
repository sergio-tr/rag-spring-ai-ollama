package com.uniovi.rag.application.service.evaluation;

/** JSON keys for canonical benchmark result rows (maps passed to persistence). */
public final class BenchmarkResultRowKeys {

    public static final String DATASET_QUESTION_ID = "dataset_question_id";
    public static final String ITEM_OUTCOME = "item_outcome";
    /** Protocol label for baseline runners ({@link com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol}). */
    public static final String EVALUATION_PROTOCOL = "evaluation_protocol";

    /** Thesis workbook preset id (e.g. {@code P7}). */
    public static final String PRESET_CODE = "preset_code";

    public static final String DIFFICULTY = "difficulty";

    public static final String LATENCY_MS = "latency_ms";

    /** Stable machine code when {@link com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome#FAILED} / guard rails. */
    public static final String ERROR_CODE = "error_code";

    public static final String LLM_MODEL_ID = "llm_model_id";

    public static final String EMBEDDING_MODEL_ID = "embedding_model_id";

    private BenchmarkResultRowKeys() {}
}
