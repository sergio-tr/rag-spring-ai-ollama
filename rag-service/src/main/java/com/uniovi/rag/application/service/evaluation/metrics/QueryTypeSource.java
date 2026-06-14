package com.uniovi.rag.application.service.evaluation.metrics;

/** Origin of the predicted query type used in evaluation telemetry. */
public enum QueryTypeSource {
    CLASSIFIER,
    DATASET_EXPECTED,
    ORACLE,
    UNKNOWN
}
