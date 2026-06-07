package com.uniovi.rag.application.service.evaluation.async;

/**
 * Keys for {@link com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity#getRequestPayload()} maps.
 */
public final class LabJobPayloadKeys {

    public static final String TRAIN_PATH = "trainPath";
    public static final String LABELS_PATH = "labelsPath";
    public static final String MODEL_NAME = "modelName";
    public static final String LABELS_JSON = "labelsJson";
    public static final String EPOCHS = "epochs";
    public static final String BATCH_SIZE = "batchSize";
    public static final String MODEL_ID = "modelId";
    public static final String INCLUDE_IMAGES = "includeImages";
    public static final String EVAL_PATH = "evalPath";
    public static final String EVAL_FILENAME = "evalFilename";
    public static final String OLLAMA_MODEL = "model";

    /** Canonical UUID string of {@code evaluation_run} when the task writes benchmark results to SQL. */
    public static final String EVALUATION_RUN_ID = "evaluationRunId";

    /** When set, the async task orchestrates all runs under this evaluation campaign. */
    public static final String CAMPAIGN_ID = "campaignId";

    private LabJobPayloadKeys() {
    }
}
