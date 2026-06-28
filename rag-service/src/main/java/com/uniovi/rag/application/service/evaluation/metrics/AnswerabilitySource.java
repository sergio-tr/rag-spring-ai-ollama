package com.uniovi.rag.application.service.evaluation.metrics;

/** Origin of the persisted answerability label on an evaluation item. */
public enum AnswerabilitySource {
    DATASET_COLUMN,
    INFERRED_FROM_EXPECTED_ANSWER,
    REVIEW_REQUIRED,
    GOLD_SUBSET_MANIFEST,
    DEFAULT_UNKNOWN
}
