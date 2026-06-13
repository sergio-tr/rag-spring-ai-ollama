package com.uniovi.rag.application.service.evaluation.metrics;

/** Whether the workbook expected query type is eligible for deterministic tools. */
public enum ToolCoverageStatus {
    APPLICABLE,
    NOT_APPLICABLE,
    UNKNOWN
}
