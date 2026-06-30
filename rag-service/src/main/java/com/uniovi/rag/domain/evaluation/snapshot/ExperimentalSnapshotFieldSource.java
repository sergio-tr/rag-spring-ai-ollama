package com.uniovi.rag.domain.evaluation.snapshot;

/** Provenance marker for a field captured in an experimental snapshot. */
public enum ExperimentalSnapshotFieldSource {
    RESOLVED_CONFIG,
    APPLICATION_DEFAULT,
    RUN_OVERRIDE,
    RUN_ENTITY,
    CATALOG_HEURISTIC,
    INDEX_COMPATIBILITY,
    UNKNOWN,
    NOT_APPLIED,
    UNSUPPORTED
}
